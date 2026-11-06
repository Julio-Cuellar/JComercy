package com.jcode.jcomercy.companymanagement.infra.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
public class RabbitMqOutboxConfig {
    private static final Logger log = LoggerFactory.getLogger(RabbitMqOutboxConfig.class);

    /** El enlace con que esta cola se declaraba antes: recibe todo lo que pase por el exchange, también los eventos de inventario. */
    private static final String LEGACY_BINDING = "#";

    @Bean
    TopicExchange jcomercyEventsExchange(@Value("${outbox.rabbit.exchange:jcomercy.events}") String name) {
        return ExchangeBuilder.topicExchange(name).durable(true).build();
    }

    @Bean
    Queue jcomercyEventsStagingQueue(@Value("${outbox.rabbit.staging-queue:jcomercy.events.pending}") String name) {
        return QueueBuilder.durable(name).build();
    }

    /**
     * Un enlace por familia de eventos de company-management (los prefijos de los canales del AsyncAPI). El exchange es
     * compartido con inventario: con {@code #} esta cola recibía también sus eventos. Un evento nuevo en el contrato
     * necesita su prefijo aquí; {@code EventBindingsContractIntegrationTest} lo comprueba.
     */
    @Bean
    Declarables jcomercyEventsStagingBindings(
        Queue jcomercyEventsStagingQueue,
        TopicExchange jcomercyEventsExchange,
        @Value("${outbox.rabbit.staging-bindings:account.#,billing.#,branch.#,branch-addon.#,branch-assignment.#,company.#,"
            + "effective-permissions.#,membership.#,operation.#,operational-context.#,platform-user-limit.#,role.#,"
            + "subscription.#}") String[] patterns
    ) {
        List<Binding> bindings = Arrays.stream(patterns)
            .map(String::trim)
            .filter(pattern -> !pattern.isEmpty())
            .map(pattern -> BindingBuilder.bind(jcomercyEventsStagingQueue).to(jcomercyEventsExchange).with(pattern))
            .toList();
        return new Declarables(bindings);
    }

    /**
     * Un broker que ya tenía la cola conserva su enlace {@code #} aunque el código deje de declararlo, así que se quita
     * en cada conexión. Quitar un enlace que no existe no es un error para RabbitMQ; si la cola aún no existe, el fallo
     * se ignora: no hay nada que quitar.
     */
    @Bean
    ConnectionListener legacyStagingBindingCleaner(
        ConnectionFactory connectionFactory,
        Queue jcomercyEventsStagingQueue,
        TopicExchange jcomercyEventsExchange,
        @Value("${outbox.rabbit.staging-bindings:}") String[] configured
    ) {
        boolean stillWanted = Arrays.stream(configured).map(String::trim).anyMatch(LEGACY_BINDING::equals);
        ConnectionListener listener = new ConnectionListener() {
            @Override
            public void onCreate(org.springframework.amqp.rabbit.connection.Connection connection) {
                if (stillWanted) {
                    return;
                }
                try (com.rabbitmq.client.Channel channel = connection.createChannel(false)) {
                    channel.queueUnbind(jcomercyEventsStagingQueue.getName(), jcomercyEventsExchange.getName(), LEGACY_BINDING);
                } catch (Exception ex) {
                    log.debug("No se pudo quitar el enlace heredado de {}: {}", jcomercyEventsStagingQueue.getName(), ex.toString());
                }
            }
        };
        connectionFactory.addConnectionListener(listener);
        return listener;
    }
}
