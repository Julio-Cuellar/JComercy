package com.jcode.jcomercy.core.identifiers;

import java.util.Objects;
import java.util.UUID;

public record EventId(UUID value) {

    public EventId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static EventId newId() {
        return new EventId(UUID.randomUUID());
    }
}
