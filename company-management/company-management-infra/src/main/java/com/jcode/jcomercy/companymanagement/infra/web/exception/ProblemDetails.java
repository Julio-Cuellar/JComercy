package com.jcode.jcomercy.companymanagement.infra.web.exception;

import java.time.Instant;

public record ProblemDetails(
    String type,
    String title,
    int status,
    String detail,
    String instance,
    String code,
    Instant timestamp
) {
    public static ProblemDetails of(String type, String title, int status, String detail, String instance, String code) {
        return new ProblemDetails(type, title, status, detail, instance, code, Instant.now());
    }
}
