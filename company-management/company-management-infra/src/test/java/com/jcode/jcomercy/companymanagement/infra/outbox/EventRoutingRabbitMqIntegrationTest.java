package com.jcode.jcomercy.companymanagement.infra.outbox;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Qué eventos llegan a la cola de company-management (hipótesis de BIT-038 y ERR-026). La cola se unía al exchange
 * compartido con {@code #}, que recibe todo, incluidos los eventos de inventario. Solo corre con {@code -Prabbitmq}.
 *
 * <p>Antes de arrancar la aplicación se deja en el broker la topología de una versión anterior (la cola con su enlace
 * {@code #}): un broker ya desplegado conserva ese enlace aunque el código cambie, y la aplicación debe quitarlo.
 */
@SpringBootTest(properties = "outbox.publisher.fixed-delay-ms=3600000")
@EnabledIfSystemProperty(named = "jcomercy.test.broker", matches = "rabbitmq")
class EventRoutingRabbitMqIntegrationTest {

    private static final String EXCHANGE = "jcomercy.events";
    private static final String QUEUE = "jcomercy.events.pending";

    private static RabbitMQContainer broker;

    @BeforeAll
    static void startBrokerWithTheOldTopology() throws Exception {
        broker = new RabbitMQContainer("rabbitmq:3.13-alpine");
        broker.start();
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(broker.getHost());
        factory.setPort(broker.getAmqpPort());
        factory.setUsername(broker.getAdminUsername());
        factory.setPassword(broker.getAdminPassword());
        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(EXCHANGE, "topic", true);
            channel.queueDeclare(QUEUE, true, false, false, null);
            channel.queueBind(QUEUE, EXCHANGE, "#");
        }
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
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @BeforeEach
    void connectAndEmptyTheQueue() {
        // Abre la primera conexión: es cuando la aplicación declara su topología y limpia la anterior.
        amqpAdmin.initialize();
        while (rabbitTemplate.receive(QUEUE, 500) != null) {
            // se vacía la cola de lo que dejó otra prueba
        }
    }

    @Test
    @DisplayName("Los eventos de inventario no caen en la cola de company-management")
    void inventoryEventsDoNotReachTheCompanyManagementQueue() {
        rabbitTemplate.convertAndSend(EXCHANGE, "inventory.stock.issued", "{}");
        rabbitTemplate.convertAndSend(EXCHANGE, "inventory.warehouse.created", "{}");

        assertThat(drainRoutingKeys()).as("eventos de inventario en la cola de company-management").isEmpty();
    }

    @Test
    @DisplayName("Los eventos propios del módulo sí llegan a su cola")
    void ownEventsReachTheQueue() {
        List<String> own = List.of("account.created", "company.activated", "branch.created", "membership.invited",
            "branch-addon.created", "effective-permissions.changed");
        own.forEach(key -> rabbitTemplate.convertAndSend(EXCHANGE, key, "{}"));

        assertThat(drainRoutingKeys()).as("eventos propios en la cola").containsExactlyInAnyOrderElementsOf(own);
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
