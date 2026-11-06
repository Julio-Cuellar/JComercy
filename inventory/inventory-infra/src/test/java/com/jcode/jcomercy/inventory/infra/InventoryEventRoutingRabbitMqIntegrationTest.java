package com.jcode.jcomercy.inventory.infra;

import com.jcode.jcomercy.inventory.infra.outbox.InventoryOutboxDispatcher;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventoryOutboxEventSpringDataRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.RabbitMQContainer;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Qué eventos llegan a la cola de inventario (ERR-026). La cola se une al exchange compartido con {@code inventory.#}:
 * los eventos de almacén se publicaban como {@code warehouse.created} y {@code warehouse.activated}, que ese patrón no
 * cubre, así que nunca llegaban. Y un evento de otro módulo no debe caer en ella. Solo corre con {@code -Prabbitmq}.
 */
@SpringBootTest(properties = "server.port=0")
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "jcomercy.test.broker", matches = "rabbitmq")
class InventoryEventRoutingRabbitMqIntegrationTest {

    private static final String EXCHANGE = "jcomercy.events";
    private static final String QUEUE = "jcomercy.events.inventory.pending";
    private static final String ACTOR = "user-routing-1";
    private static final String COMPANY_ID = "cmp_routing_1";
    private static final String BRANCH_ID = "br_routing_1";

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

    @Autowired private MockMvc mockMvc;
    @Autowired private InventoryOutboxEventSpringDataRepository outbox;
    @Autowired private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void cleanOutboxAndQueue() {
        outbox.deleteAll();
        while (rabbitTemplate.receive(QUEUE, 500) != null) {
            // se vacía la cola de lo que dejó otra prueba
        }
    }

    @Test
    @DisplayName("Los eventos de almacén llegan a la cola de inventario con la clave inventory.warehouse.*")
    void warehouseEventsReachTheInventoryQueue() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor("local-only-change-me-012345678901234567890123456789".getBytes(StandardCharsets.UTF_8));
        String token = "Bearer " + Jwts.builder()
            .subject(ACTOR).issuer("jcomercy")
            .claim("companyIds", List.of(COMPANY_ID))
            .claim("branchIds", List.of(BRANCH_ID))
            .claim("permissions", "INVENTORY_WRITE INVENTORY_READ")
            .expiration(new Date(System.currentTimeMillis() + 3600_000))
            .signWith(key).compact();

        String response = mockMvc.perform(post("/api/v1/companies/{companyId}/branches/{branchId}/warehouses", COMPANY_ID, BRANCH_ID)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_routing_create_1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"ALM-ROUTING-01","name":"Almacén de ruteo","type":"GENERAL",
                     "defaultWarehouse":true,"timezone":"America/Mexico_City","currency":"MXN"}
                    """))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String warehouseId = com.jayway.jsonpath.JsonPath.read(response, "$.id");
        mockMvc.perform(post("/api/v1/warehouses/{warehouseId}/activate", warehouseId)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_routing_activate_1"))
            .andExpect(status().isOk());

        new InventoryOutboxDispatcher(outbox, rabbitTemplate, EXCHANGE).publishPending();

        assertThat(drainRoutingKeys())
            .as("claves de los eventos que llegaron a %s", QUEUE)
            .containsExactly("inventory.warehouse.created", "inventory.warehouse.activated");
    }

    @Test
    @DisplayName("Un evento de otro módulo no cae en la cola de inventario")
    void aForeignEventDoesNotReachTheInventoryQueue() {
        rabbitTemplate.convertAndSend(EXCHANGE, "company.created", "{}");
        rabbitTemplate.convertAndSend(EXCHANGE, "account.created", "{}");

        assertThat(drainRoutingKeys()).as("eventos ajenos en la cola de inventario").isEmpty();
    }

    private List<String> drainRoutingKeys() {
        List<String> keys = new ArrayList<>();
        Message message;
        while ((message = rabbitTemplate.receive(QUEUE, 3_000)) != null) {
            keys.add(message.getMessageProperties().getReceivedRoutingKey());
        }
        return keys;
    }
}
