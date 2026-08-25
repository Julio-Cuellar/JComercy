package com.jcode.jcomercy.core.errors;

import java.util.Objects;

public class CoreException extends RuntimeException {

    private final CoreErrorCode code;

    public CoreException(CoreErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public CoreErrorCode code() {
        return code;
    }
}
