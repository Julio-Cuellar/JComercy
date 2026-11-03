package com.jcode.jcomercy.core.messaging;

import com.jcode.jcomercy.core.events.DomainEvent;

public interface EventPublisher {

    void publish(DomainEvent<?> event);
}
