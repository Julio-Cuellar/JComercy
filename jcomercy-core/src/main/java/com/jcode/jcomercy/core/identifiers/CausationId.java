package com.jcode.jcomercy.core.identifiers;

import java.util.Objects;
import java.util.UUID;

public record CausationId(UUID value) {

    public CausationId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static CausationId newId() {
        return new CausationId(UUID.randomUUID());
    }
}
