package com.jcode.jcomercy.core.identifiers;

import java.util.Objects;
import java.util.UUID;

public record PublicUserId(UUID value) {

    public PublicUserId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PublicUserId newId() {
        return new PublicUserId(UUID.randomUUID());
    }
}
