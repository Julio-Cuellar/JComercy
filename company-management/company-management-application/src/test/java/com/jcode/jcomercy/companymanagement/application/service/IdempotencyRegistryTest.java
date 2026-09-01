package com.jcode.jcomercy.companymanagement.application.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class IdempotencyRegistryTest {

    @Test
    void executesAnOperationOnlyOnceForTheSameKey() {
        IdempotencyRegistry registry = new IdempotencyRegistry();
        AtomicInteger executions = new AtomicInteger();

        String first = registry.execute("company.create", "idem-1", () -> {
            executions.incrementAndGet();
            return "created";
        });
        String second = registry.execute("company.create", "idem-1", () -> {
            executions.incrementAndGet();
            return "should-not-run";
        });

        assertThat(first).isEqualTo("created");
        assertThat(second).isEqualTo("created");
        assertThat(executions).hasValue(1);
    }

    @Test
    void separatesKeysByOperation() {
        IdempotencyRegistry registry = new IdempotencyRegistry();

        assertThat(registry.execute("company.create", "idem-1", () -> "create"))
            .isEqualTo("create");
        assertThat(registry.execute("branch.create", "idem-1", () -> "branch"))
            .isEqualTo("branch");
    }

    @Test
    void rejectsBlankKeys() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new IdempotencyRegistry().execute("company.create", " ", () -> "x"));
    }
}
