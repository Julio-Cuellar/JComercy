package com.jclinical.core.events;

public interface DomainEventPublisherPort {

    void publish(String routingKey, Object payload);
}
