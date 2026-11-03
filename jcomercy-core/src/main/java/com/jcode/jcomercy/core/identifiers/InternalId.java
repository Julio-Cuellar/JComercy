package com.jcode.jcomercy.core.identifiers;

import java.util.Objects;
import java.util.UUID;

public record InternalId(UUID value) {

    public InternalId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static InternalId newId() {
        return new InternalId(UUID.randomUUID());
    }
}
