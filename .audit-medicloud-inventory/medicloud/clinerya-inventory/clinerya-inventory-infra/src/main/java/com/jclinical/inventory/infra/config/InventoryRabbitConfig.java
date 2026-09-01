package com.jclinical.inventory.infra.config;

import com.jclinical.core.events.DomainEventRoutingKeys;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Colas propias de Inventario para reservar/liberar material por evento de dominio.
 * Cada cola declara su propia dead-letter routing hacia el exchange compartido
 * jclinical.dlx (ver clinerya-messaging), que republica en inventory.dlq tras 3 reintentos.
 */
@Configuration
public class InventoryRabbitConfig {

    public static final String RESERVATION_REQUESTED_QUEUE = "inventory.material.reservation.requested";
    public static final String RESERVATION_RELEASED_QUEUE = "inventory.material.reservation.released";
    public static final String DEAD_LETTER_QUEUE = "inventory.dlq";

    @Bean
    public Queue materialReservationRequestedQueue() {
        return QueueBuilder.durable(RESERVATION_REQUESTED_QUEUE)
                .withArgument("x-dead-letter-exchange", DomainEventRoutingKeys.DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DomainEventRoutingKeys.MATERIAL_RESERVATION_REQUESTED)
                .build();
    }

    @Bean
    public Queue materialReservationReleasedQueue() {
        return QueueBuilder.durable(RESERVATION_RELEASED_QUEUE)
                .withArgument("x-dead-letter-exchange", DomainEventRoutingKeys.DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DomainEventRoutingKeys.MATERIAL_RESERVATION_RELEASED)
                .build();
    }

    @Bean
    public Queue inventoryDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding materialReservationRequestedBinding(Queue materialReservationRequestedQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(materialReservationRequestedQueue)
                .to(domainEventsExchange)
                .with(DomainEventRoutingKeys.MATERIAL_RESERVATION_REQUESTED);
    }

    @Bean
    public Binding materialReservationReleasedBinding(Queue materialReservationReleasedQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(materialReservationReleasedQueue)
                .to(domainEventsExchange)
                .with(DomainEventRoutingKeys.MATERIAL_RESERVATION_RELEASED);
    }

    @Bean
    public Binding inventoryDeadLetterRequestedBinding(Queue inventoryDeadLetterQueue, DirectExchange domainEventsDeadLetterExchange) {
        return BindingBuilder.bind(inventoryDeadLetterQueue)
                .to(domainEventsDeadLetterExchange)
                .with(DomainEventRoutingKeys.MATERIAL_RESERVATION_REQUESTED);
    }

    @Bean
    public Binding inventoryDeadLetterReleasedBinding(Queue inventoryDeadLetterQueue, DirectExchange domainEventsDeadLetterExchange) {
        return BindingBuilder.bind(inventoryDeadLetterQueue)
                .to(domainEventsDeadLetterExchange)
                .with(DomainEventRoutingKeys.MATERIAL_RESERVATION_RELEASED);
    }
}
