package com.jcode.jcomercy.companymanagement.infra.outbox;

import com.jayway.jsonpath.JsonPath;
import com.jcode.jcomercy.companymanagement.infra.persistence.entity.OutboxEventJpaEntity;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.OutboxSpringDataRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
    private OutboxSpringDataRepository outbox;

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

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
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(event.getPayloadJson());
    }

    // ---- Ayudas ----

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
