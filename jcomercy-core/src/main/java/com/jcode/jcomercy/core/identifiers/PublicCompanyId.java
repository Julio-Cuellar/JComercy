package com.jcode.jcomercy.core.identifiers;

import java.util.Objects;
import java.util.UUID;

public record PublicCompanyId(UUID value) {

    public PublicCompanyId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PublicCompanyId newId() {
        return new PublicCompanyId(UUID.randomUUID());
    }
}
