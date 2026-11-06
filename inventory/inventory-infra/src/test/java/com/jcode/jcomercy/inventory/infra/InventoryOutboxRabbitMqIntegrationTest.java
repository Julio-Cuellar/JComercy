package com.jcode.jcomercy.inventory.infra;

import com.jcode.jcomercy.inventory.domain.port.InventoryOutboxPort;
import com.jcode.jcomercy.inventory.infra.outbox.InventoryOutboxDispatcher;
import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryOutboxEventJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventoryOutboxEventSpringDataRepository;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase R1: el Outbox de inventario contra un RabbitMQ real. {@link InventoryOutboxIntegrationTest} usa un
 * {@code RabbitTemplate} simulado y nunca comprobó que el evento llega de verdad al broker ni con qué forma. Solo
 * corre con {@code -Prabbitmq} (necesita Docker); sin el perfil se omite.
 *
 * <p>El despachador programado está apagado en las pruebas ({@code outbox.publisher.enabled=false}), así que se crea
 * uno a mano con el {@code RabbitTemplate} real del contexto y se invoca desde la prueba.
 */
@SpringBootTest(properties = "server.port=0")
@EnabledIfSystemProperty(named = "jcomercy.test.broker", matches = "rabbitmq")
class InventoryOutboxRabbitMqIntegrationTest {

    private static final String EXCHANGE = "jcomercy.events";
    private static final String QUEUE = "jcomercy.events.inventory.pending";

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
    private InventoryOutboxPort outboxPort;

    @Autowired
    private InventoryOutboxEventSpringDataRepository outbox;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void cleanOutbox() {
        // La base de datos de pruebas se comparte entre clases. El despachador de inventario reintenta siempre los 50
        // pendientes más antiguos: con eventos viejos de otras pruebas delante, los de esta no se intentarían (visto en
        // una corrida completa, ERR-022). Igual que InventoryOutboxIntegrationTest, se parte de una tabla vacía.
        outbox.deleteAll();
    }

    @Test
    @DisplayName("Un evento del Outbox llega a la cola del broker real con su identificador, su clave, sus cabeceras y modo persistente")
    void anOutboxEventReachesTheRealBrokerWithItsEnvelope() throws Exception {
        String companyId = "cmp_rmq_" + UUID.randomUUID().toString().substring(0, 8);
        outboxPort.publish("inventory.stock.received", companyId, "br_rmq_1", "user_rmq", "cor_rmq_1",
            Map.of("productId", "prod-1", "quantity", 50));
        InventoryOutboxEventJpaEntity event = find(companyId);
        assertThat(event.getStatus()).isEqualTo("PENDING");

        InventoryOutboxDispatcher dispatcher = new InventoryOutboxDispatcher(outbox, rabbitTemplate, EXCHANGE);
        // Otras pruebas del mismo proceso pueden haber dejado eventos pendientes delante: se despacha hasta que salga el nuestro.
        for (int round = 0; round < 5 && !"PUBLISHED".equals(find(companyId).getStatus()); round++) {
            dispatcher.publishPending();
        }
        assertThat(find(companyId).getStatus()).isEqualTo("PUBLISHED");
        assertThat(find(companyId).getPublishedAt()).isNotNull();

        Message message = receive(event.getEventId().toString());
        assertThat(message).as("el mensaje del evento %s en la cola %s", event.getEventId(), QUEUE).isNotNull();
        MessageProperties properties = message.getMessageProperties();
        assertThat(properties.getMessageId()).isEqualTo(event.getEventId().toString());
        // En un mensaje recibido el modo está en otra propiedad: getDeliveryMode() es solo para los que se envían.
        assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getReceivedExchange()).isEqualTo(EXCHANGE);
        assertThat(properties.getReceivedRoutingKey()).isEqualTo("inventory.stock.received");
        assertThat(properties.getHeaders())
            .containsEntry("eventType", event.getEventType())
            .containsEntry("schemaVersion", event.getSchemaVersion())
            .containsEntry("companyId", companyId)
            .containsEntry("branchId", "br_rmq_1")
            .containsEntry("actorUserId", "user_rmq")
            .containsEntry("correlationId", "cor_rmq_1");
        // El cuerpo es el sobre del contrato (ERR-026): el payload guardado va como «data», con el identificador y la secuencia.
        assertThat(properties.getContentType()).isEqualTo("application/json");
        com.fasterxml.jackson.databind.JsonNode body = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        assertThat(body.path("eventId").asText()).isEqualTo(event.getEventId().toString());
        assertThat(body.path("producerModule").asText()).isEqualTo("inventory");
        assertThat(body.path("sequence").asLong()).isEqualTo(event.getSequence());
        assertThat(body.path("data")).isEqualTo(new com.fasterxml.jackson.databind.ObjectMapper().readTree(event.getPayload()));
    }

    /**
     * Fase R2: la caída y el regreso del broker. El Outbox existe para que registrar un evento no dependa de que el
     * broker esté arriba, y para que ningún evento se pierda. La caída se simula parando la aplicación de RabbitMQ dentro
     * del contenedor ({@code rabbitmqctl stop_app}): el puerto sigue asignado, rechaza conexiones y sus datos sobreviven.
     */
    @Test
    @DisplayName("Con el broker caído los eventos no se pierden, y al volver salen todos, una sola vez y en orden")
    void eventsSurviveABrokerOutageAndGoOutOnceItIsBack() throws Exception {
        InventoryOutboxDispatcher dispatcher = new InventoryOutboxDispatcher(outbox, rabbitTemplate, EXCHANGE);
        List<String> companies = new ArrayList<>();
        brokerDown();
        try {
            for (int i = 0; i < 3; i++) {
                String companyId = "cmp_rmq_" + UUID.randomUUID().toString().substring(0, 8);
                // Registrar el evento es solo una fila en la base de datos: no depende del broker.
                outboxPort.publish("inventory.stock.received", companyId, "br_rmq_1", "user_rmq", "cor_rmq_" + i,
                    Map.of("productId", "prod-1", "quantity", i + 1));
                companies.add(companyId);
            }
            dispatcher.publishPending();

            for (String companyId : companies) {
                InventoryOutboxEventJpaEntity stored = find(companyId);
                assertThat(stored.getStatus()).as("estado del evento de %s con el broker caído", companyId).isEqualTo("PENDING");
                assertThat(stored.getPublishedAt()).isNull();
            }
            // Con el broker caído el ciclo se corta en el primer evento (ERR-021): ese cuenta el intento y guarda el error;
            // los demás ni se intentan, así que al volver el broker siguen en orden.
            InventoryOutboxEventJpaEntity head = find(companies.get(0));
            assertThat(head.getRetryCount()).isGreaterThanOrEqualTo(1);
            assertThat(head.getLastError()).isNotBlank();
            assertThat(find(companies.get(1)).getRetryCount()).isZero();
        } finally {
            brokerUp();
        }

        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline
            && companies.stream().anyMatch(companyId -> !"PUBLISHED".equals(find(companyId).getStatus()))) {
            dispatcher.publishPending();
            Thread.sleep(1_000);
        }
        for (String companyId : companies) {
            InventoryOutboxEventJpaEntity stored = find(companyId);
            assertThat(stored.getStatus()).as("estado del evento de %s tras volver el broker", companyId).isEqualTo("PUBLISHED");
            assertThat(stored.getLastError()).isNull();
        }

        List<String> expected = companies.stream().map(companyId -> find(companyId).getEventId().toString()).toList();
        List<String> received = receiveAll(expected);
        assertThat(received).as("mensajes de los tres eventos, sin duplicados").doesNotHaveDuplicates().hasSize(3);
        assertThat(received).as("orden de creación").containsExactlyElementsOf(expected);
    }

    /**
     * Fase R3: un broker que no responde. Se congela el contenedor ({@code docker pause}) con la conexión ya abierta: el
     * sistema operativo sigue aceptando lo que se le manda, pero nadie confirma. Cada evento espera entonces los 5 s de
     * la confirmación y sigue pendiente; al reanudarse el broker, ninguno debe haberse perdido.
     */
    @Test
    @DisplayName("Con el broker colgado, cada evento espera su confirmación y sigue pendiente; al reanudarse, ninguno se pierde")
    void eventsAreNotLostWhenTheBrokerStopsAnswering() throws Exception {
        InventoryOutboxDispatcher dispatcher = new InventoryOutboxDispatcher(outbox, rabbitTemplate, EXCHANGE);
        // La conexión se abre con el broker vivo: si se congelara antes, el saludo inicial se quedaría esperando y no se
        // probaría la espera de la confirmación, que es lo que interesa.
        String warmUp = "cmp_rmq_" + UUID.randomUUID().toString().substring(0, 8);
        outboxPort.publish("inventory.stock.received", warmUp, "br_rmq_1", "user_rmq", "cor_rmq_w", Map.of("quantity", 1));
        for (int round = 0; round < 5 && !"PUBLISHED".equals(find(warmUp).getStatus()); round++) {
            dispatcher.publishPending();
        }
        assertThat(find(warmUp).getStatus()).isEqualTo("PUBLISHED");

        // Dos eventos: antes de ERR-021 la segunda publicación se quedaba bloqueada hasta que el cliente daba la conexión
        // por muerta (unos 130 s, medido en BIT-039). Ahora el ciclo se corta en el primer fallo y termina en segundos.
        String pending = "cmp_rmq_" + UUID.randomUUID().toString().substring(0, 8);
        String second = "cmp_rmq_" + UUID.randomUUID().toString().substring(0, 8);
        outboxPort.publish("inventory.stock.received", pending, "br_rmq_1", "user_rmq", "cor_rmq_p", Map.of("quantity", 1));
        outboxPort.publish("inventory.stock.received", second, "br_rmq_1", "user_rmq", "cor_rmq_q", Map.of("quantity", 2));
        List<String> companies = List.of(pending, second);

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
        for (String companyId : companies) {
            InventoryOutboxEventJpaEntity stored = find(companyId);
            assertThat(stored.getStatus()).as("estado del evento de %s con el broker colgado", companyId).isEqualTo("PENDING");
            assertThat(stored.getPublishedAt()).isNull();
        }
        assertThat(find(pending).getRetryCount()).isGreaterThanOrEqualTo(1);
        assertThat(find(pending).getLastError()).as("error guardado con el broker colgado").isNotBlank();
        assertThat(find(second).getRetryCount()).as("el segundo evento ni se intentó").isZero();

        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline
            && companies.stream().anyMatch(companyId -> !"PUBLISHED".equals(find(companyId).getStatus()))) {
            dispatcher.publishPending();
            Thread.sleep(1_000);
        }
        List<String> expected = companies.stream().map(companyId -> find(companyId).getEventId().toString()).toList();
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

    private InventoryOutboxEventJpaEntity find(String companyId) {
        return outbox.findAll().stream()
            .filter(candidate -> companyId.equals(candidate.getCompanyId()))
            .findFirst().orElseThrow();
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
}
