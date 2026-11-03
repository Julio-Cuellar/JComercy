package com.jcode.jcomercy.core.identifiers;

import java.util.Objects;
import java.util.UUID;

public record CorrelationId(UUID value) {

    public CorrelationId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static CorrelationId newId() {
        return new CorrelationId(UUID.randomUUID());
    }
}
