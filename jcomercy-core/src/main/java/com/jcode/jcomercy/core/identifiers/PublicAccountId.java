package com.jcode.jcomercy.core.identifiers;

import java.util.Objects;
import java.util.UUID;

public record PublicAccountId(UUID value) {

    public PublicAccountId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PublicAccountId newId() {
        return new PublicAccountId(UUID.randomUUID());
    }
}
