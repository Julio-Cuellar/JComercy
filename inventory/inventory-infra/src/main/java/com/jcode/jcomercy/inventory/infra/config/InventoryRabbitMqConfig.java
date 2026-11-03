package com.jcode.jcomercy.inventory.infra.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InventoryRabbitMqConfig {

    @Bean
    TopicExchange jcomercyEventsExchange(@Value("${outbox.rabbit.exchange:jcomercy.events}") String name) {
        return ExchangeBuilder.topicExchange(name).durable(true).build();
    }

    @Bean
    Queue inventoryEventsStagingQueue(@Value("${outbox.rabbit.inventory-staging-queue:jcomercy.events.inventory.pending}") String name) {
        return QueueBuilder.durable(name).build();
    }

    @Bean
    Binding inventoryEventsStagingBinding(Queue inventoryEventsStagingQueue, TopicExchange jcomercyEventsExchange) {
        return BindingBuilder.bind(inventoryEventsStagingQueue).to(jcomercyEventsExchange).with("inventory.#");
    }
}
