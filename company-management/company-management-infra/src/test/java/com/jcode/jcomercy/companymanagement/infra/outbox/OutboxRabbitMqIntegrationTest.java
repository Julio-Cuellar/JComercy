package com.jcode.jcomercy.companymanagement.infra.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jcode.jcomercy.companymanagement.infra.persistence.entity.OutboxEventJpaEntity;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.OutboxSpringDataRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fase R1: el Outbox de company-management contra un RabbitMQ real. Las demás pruebas del Outbox usan un
 * {@code RabbitTemplate} simulado, así que nunca habían comprobado que el evento llega de verdad al broker ni con qué
 * forma. Solo corre con {@code -Prabbitmq} (necesita Docker); sin el perfil se omite.
 *
 * <p>El despachador programado se deja para dentro de una hora, y la prueba lo invoca a mano: así no compite con ella.
 */
@SpringBootTest(properties = "outbox.publisher.fixed-delay-ms=3600000")
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "jcomercy.test.broker", matches = "rabbitmq")
class OutboxRabbitMqIntegrationTest {

    private static final String EXCHANGE = "jcomercy.events";
    private static final String QUEUE = "jcomercy.events.pending";

    private static RabbitMQContainer broker;

    @BeforeAll
    static void startBroker() {
        broker = new RabbitMQContainer("rabbitmq:3.13-alpine");
        broker.start();
    }

    @AfterAll
    static void stopBroker() {
        if (broker != null) {
            broker.stop();
        }
    }

    @DynamicPropertySource
    static void brokerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", () -> broker.getHost());
        registry.add("spring.rabbitmq.port", () -> broker.getAmqpPort());
        registry.add("spring.rabbitmq.username", () -> broker.getAdminUsername());
        registry.add("spring.rabbitmq.password", () -> broker.getAdminPassword());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxSpringDataRepository outbox;

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void emptyTheOutbox() {
        // La base de datos de pruebas se comparte entre clases. Con el ciclo que se corta ante un broker caído, un evento
        // pendiente de otra clase puesto delante se llevaría el único intento del ciclo.
        outbox.deleteAll();
    }

    @Test
    @DisplayName("Un evento del Outbox llega a la cola del broker real con su identificador, su clave, sus cabeceras y modo persistente")
    void anOutboxEventReachesTheRealBrokerWithItsEnvelope() throws Exception {
        String accountId = registerAccount();
        OutboxEventJpaEntity event = outbox.findAll().stream()
            .filter(candidate -> accountId.equals(candidate.getAccountId()))
            .findFirst().orElseThrow();
        assertThat(event.getStatus()).isEqualTo("PENDING");

        // Otras pruebas del mismo proceso pueden haber dejado eventos pendientes delante: se despacha hasta que salga el nuestro.
        for (int round = 0; round < 5 && !"PUBLISHED".equals(statusOf(event)); round++) {
            dispatcher.publishPending();
        }
        assertThat(statusOf(event)).isEqualTo("PUBLISHED");
        assertThat(outbox.findById(event.getId()).orElseThrow().getPublishedAt()).isNotNull();

        Message message = receive(event.getEventId());
        assertThat(message).as("el mensaje del evento %s en la cola %s", event.getEventId(), QUEUE).isNotNull();
        MessageProperties properties = message.getMessageProperties();
        assertThat(properties.getMessageId()).isEqualTo(event.getEventId());
        // En un mensaje recibido el modo está en otra propiedad: getDeliveryMode() es solo para los que se envían.
        assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getReceivedExchange()).isEqualTo(EXCHANGE);
        assertThat(properties.getReceivedRoutingKey()).isEqualTo(event.getEventType());
        assertThat(properties.getHeaders())
            .containsEntry("eventType", event.getEventType())
            .containsEntry("schemaVersion", event.getSchemaVersion())
            .containsEntry("accountId", event.getAccountId())
            .containsEntry("companyId", event.getCompanyId());
        assertThat(properties.getHeaders().get("branchId")).isEqualTo(event.getBranchId());
        // El cuerpo es el sobre que describe el AsyncAPI (R4): el payload guardado va como «data», con el identificador.
        assertThat(properties.getContentType()).isEqualTo("application/json");
        JsonNode body = objectMapper.readTree(message.getBody());
        assertThat(body.path("eventId").asText()).isEqualTo(event.getEventId());
        assertThat(body.path("data")).isEqualTo(objectMapper.readTree(event.getPayloadJson()));
    }

    /**
     * Fase R2: la caída y el regreso del broker. El Outbox existe para que la operación de negocio no dependa de que el
     * broker esté arriba, y para que ningún evento se pierda. La caída se simula parando la aplicación de RabbitMQ dentro
     * del contenedor ({@code rabbitmqctl stop_app}): el puerto sigue asignado, rechaza conexiones y sus datos sobreviven.
     */
    @Test
    @DisplayName("Con el broker caído los eventos no se pierden ni frenan la operación, y al volver salen todos, una sola vez y en orden")
    void eventsSurviveABrokerOutageAndGoOutOnceItIsBack() throws Exception {
        List<OutboxEventJpaEntity> events = new ArrayList<>();
        brokerDown();
        try {
            for (int i = 0; i < 3; i++) {
                // registerAccount exige un 201: la operación de negocio no depende de que el broker esté arriba.
                String accountId = registerAccount();
                events.add(outbox.findAll().stream()
                    .filter(candidate -> accountId.equals(candidate.getAccountId()))
                    .findFirst().orElseThrow());
            }
            dispatcher.publishPending();

            for (OutboxEventJpaEntity event : events) {
                OutboxEventJpaEntity stored = outbox.findById(event.getId()).orElseThrow();
                assertThat(stored.getStatus()).as("estado de %s con el broker caído", event.getEventId()).isEqualTo("PENDING");
                assertThat(stored.getPublishedAt()).isNull();
            }
            // Con el broker caído el ciclo se corta en el primer evento (ERR-021): ese cuenta el intento y guarda el error,
            // sin espera propia; los demás ni se intentan, así que al volver el broker siguen en orden.
            OutboxEventJpaEntity head = outbox.findById(events.get(0).getId()).orElseThrow();
            assertThat(head.getRetryCount()).isGreaterThanOrEqualTo(1);
            assertThat(head.getErrorMessage()).isNotBlank();
            assertThat(head.getNextRetryAt()).isNull();
            assertThat(outbox.findById(events.get(1).getId()).orElseThrow().getRetryCount()).isZero();
        } finally {
            brokerUp();
        }

        // El reintento espera (5 s por intento): se despacha hasta que salgan los tres, con un límite.
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline && events.stream().anyMatch(event -> !"PUBLISHED".equals(statusOf(event)))) {
            dispatcher.publishPending();
            Thread.sleep(1_000);
        }
        for (OutboxEventJpaEntity event : events) {
            OutboxEventJpaEntity stored = outbox.findById(event.getId()).orElseThrow();
            assertThat(stored.getStatus()).as("estado de %s tras volver el broker", event.getEventId()).isEqualTo("PUBLISHED");
            assertThat(stored.getErrorMessage()).isNull();
        }

        List<String> expected = events.stream().map(OutboxEventJpaEntity::getEventId).toList();
        List<String> received = receiveAll(expected);
        assertThat(received).as("mensajes de los tres eventos, sin duplicados").doesNotHaveDuplicates().hasSize(3);
        assertThat(received).as("orden de creación").containsExactlyElementsOf(expected);
    }

    /**
     * Fase R3: un broker que no responde. Se congela el contenedor ({@code docker pause}) con la conexión ya abierta: el
     * sistema operativo sigue aceptando lo que se le manda, pero nadie confirma. Cada evento espera entonces los 5 s de
     * la confirmación y vuelve a pendiente; al reanudarse el broker, ninguno debe haberse perdido.
     */
    @Test
    @DisplayName("Con el broker colgado, cada evento espera su confirmación y vuelve a pendiente; al reanudarse, ninguno se pierde")
    void eventsAreNotLostWhenTheBrokerStopsAnswering() throws Exception {
        // La conexión se abre con el broker vivo: si se congelara antes, el saludo inicial se quedaría esperando y no se
        // probaría la espera de la confirmación, que es lo que interesa.
        OutboxEventJpaEntity warmUp = eventOf(registerAccount());
        for (int round = 0; round < 5 && !"PUBLISHED".equals(statusOf(warmUp)); round++) {
            dispatcher.publishPending();
        }
        assertThat(statusOf(warmUp)).isEqualTo("PUBLISHED");

        // Dos eventos: antes de ERR-021 la segunda publicación se quedaba bloqueada hasta que el cliente daba la conexión
        // por muerta (unos 130 s, medido en BIT-039). Ahora el ciclo se corta en el primer fallo y termina en segundos.
        List<OutboxEventJpaEntity> events = List.of(eventOf(registerAccount()), eventOf(registerAccount()));

        brokerFrozen();
        long started = System.nanoTime();
        try {
            dispatcher.publishPending();
        } finally {
            brokerResumed();
        }
        long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started);

        // El primer evento espera 5 s una confirmación que no llega: si tardó menos, el broker no estaba colgado. Y el
        // ciclo termina ahí: el segundo no espera otra vez ni se queda bloqueado.
        assertThat(seconds).as("segundos que tardó el ciclo con el broker colgado").isBetween(4L, 30L);
        for (OutboxEventJpaEntity event : events) {
            OutboxEventJpaEntity stored = outbox.findById(event.getId()).orElseThrow();
            assertThat(stored.getStatus()).as("estado de %s con el broker colgado", event.getEventId()).isEqualTo("PENDING");
            assertThat(stored.getPublishedAt()).isNull();
        }
        OutboxEventJpaEntity head = outbox.findById(events.get(0).getId()).orElseThrow();
        assertThat(head.getRetryCount()).isGreaterThanOrEqualTo(1);
        assertThat(head.getErrorMessage()).as("error guardado con el broker colgado").isNotBlank();
        assertThat(outbox.findById(events.get(1).getId()).orElseThrow().getRetryCount()).as("el segundo evento ni se intentó").isZero();

        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline && events.stream().anyMatch(event -> !"PUBLISHED".equals(statusOf(event)))) {
            dispatcher.publishPending();
            Thread.sleep(1_000);
        }
        List<String> expected = events.stream().map(OutboxEventJpaEntity::getEventId).toList();
        List<String> received = receiveAll(expected);
        // Al menos una vez, no exactamente una: el primer envío llegó al broker cuando se reanudó, y luego se reintentó.
        assertThat(received).as("cada evento llega al menos una vez").containsAll(expected);
    }

    // ---- Ayudas ----

    private static void brokerFrozen() {
        broker.getDockerClient().pauseContainerCmd(broker.getContainerId()).exec();
    }

    private static void brokerResumed() {
        broker.getDockerClient().unpauseContainerCmd(broker.getContainerId()).exec();
    }

    private OutboxEventJpaEntity eventOf(String accountId) {
        return outbox.findAll().stream()
            .filter(candidate -> accountId.equals(candidate.getAccountId()))
            .findFirst().orElseThrow();
    }

    private static void brokerDown() throws Exception {
        assertThat(broker.execInContainer("rabbitmqctl", "stop_app").getExitCode()).isZero();
    }

    private static void brokerUp() throws Exception {
        assertThat(broker.execInContainer("rabbitmqctl", "start_app").getExitCode()).isZero();
    }

    /** Vacía la cola y devuelve, en el orden en que salieron, los identificadores de los mensajes pedidos. */
    private List<String> receiveAll(List<String> wanted) {
        List<String> received = new ArrayList<>();
        Message message;
        while ((message = rabbitTemplate.receive(QUEUE, 3_000)) != null) {
            String id = message.getMessageProperties().getMessageId();
            if (wanted.contains(id)) {
                received.add(id);
            }
        }
        return received;
    }

    private String statusOf(OutboxEventJpaEntity event) {
        return outbox.findById(event.getId()).orElseThrow().getStatus();
    }

    /** Saca mensajes de la cola hasta encontrar el del evento; los de otras pruebas se descartan. */
    private Message receive(String messageId) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(QUEUE, 1_000);
            if (message != null && messageId.equals(message.getMessageProperties().getMessageId())) {
                return message;
            }
        }
        return null;
    }

    private String registerAccount() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long number = Math.abs(UUID.randomUUID().getMostSignificantBits() % 10_000_000_000L);
        String body = """
            {"name":"Cuenta del broker %s","owner":{"firstName":"Persona","lastName":"Prueba","birthDate":"1985-04-12",
            "rfc":"RMQ%010d","phoneNumber":"5512345678","email":"broker-%s@example.com","password":"ClaveSegura123!",
            "state":"Ciudad de México","address":"Av. Reforma 100"}}
            """.formatted(suffix, number, suffix);
        String response = mockMvc.perform(post("/api/v1/accounts/registration")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.publicAccountId");
    }
}
