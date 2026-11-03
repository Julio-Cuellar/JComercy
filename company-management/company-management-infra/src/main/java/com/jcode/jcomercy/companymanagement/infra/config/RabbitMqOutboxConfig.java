package com.jcode.jcomercy.companymanagement.infra.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqOutboxConfig {
    @Bean
    TopicExchange jcomercyEventsExchange(@Value("${outbox.rabbit.exchange:jcomercy.events}") String name) {
        return ExchangeBuilder.topicExchange(name).durable(true).build();
    }

    @Bean
    Queue jcomercyEventsStagingQueue(@Value("${outbox.rabbit.staging-queue:jcomercy.events.pending}") String name) {
        return QueueBuilder.durable(name).build();
    }

    @Bean
    Binding jcomercyEventsStagingBinding(Queue jcomercyEventsStagingQueue, TopicExchange jcomercyEventsExchange) {
        return BindingBuilder.bind(jcomercyEventsStagingQueue).to(jcomercyEventsExchange).with("#");
    }
}
