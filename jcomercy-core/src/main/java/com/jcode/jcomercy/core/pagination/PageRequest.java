package com.jcode.jcomercy.core.pagination;

import java.util.Objects;

public record PageRequest(int page, int size, String sort) {

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive");
        }
        sort = Objects.requireNonNullElse(sort, "");
    }

    public static PageRequest firstPage(int size) {
        return new PageRequest(0, size, "");
    }
}
