package com.jcode.jcomercy.sales.infra.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El exchange compartido de eventos de JComercy y la cola de Ventas, enlazada con {@code sales.#}: así los eventos de
 * Ventas llegan a su cola y no a la de otro módulo (ERR-027).
 */
@Configuration
public class SalesRabbitMqConfig {

    @Bean
    TopicExchange jcomercyEventsExchange(@Value("${outbox.rabbit.exchange:jcomercy.events}") String name) {
        return ExchangeBuilder.topicExchange(name).durable(true).build();
    }

    @Bean
    Queue salesEventsStagingQueue(@Value("${outbox.rabbit.sales-staging-queue:jcomercy.events.sales.pending}") String name) {
        return QueueBuilder.durable(name).build();
    }

    @Bean
    Binding salesEventsStagingBinding(Queue salesEventsStagingQueue, TopicExchange jcomercyEventsExchange) {
        return BindingBuilder.bind(salesEventsStagingQueue).to(jcomercyEventsExchange).with("sales.#");
    }
}
