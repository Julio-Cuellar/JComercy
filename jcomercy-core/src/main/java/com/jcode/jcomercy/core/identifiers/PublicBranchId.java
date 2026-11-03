package com.jcode.jcomercy.core.identifiers;

import java.util.Objects;
import java.util.UUID;

public record PublicBranchId(UUID value) {

    public PublicBranchId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PublicBranchId newId() {
        return new PublicBranchId(UUID.randomUUID());
    }
}
