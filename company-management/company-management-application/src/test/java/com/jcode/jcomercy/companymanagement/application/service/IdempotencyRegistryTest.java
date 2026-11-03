package com.jcode.jcomercy.companymanagement.application.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
    void rejectsMissingEmptyAndBlankKeysWithTheirOwnException() {
        for (String key : new String[] {null, "", " "}) {
            assertThatExceptionOfType(IdempotencyKeyRequiredException.class)
                .as("clave: [%s]", key)
                .isThrownBy(() -> new IdempotencyRegistry().execute("company.create", key, () -> "x"));
        }
    }

    /**
     * ERR-007: con el almacén durable ya reclamado y la memoria del registro vacía (proceso reiniciado), repetir
     * la clave lanza su propia excepción, que la capa web responde como 409, y la operación no se vuelve a ejecutar.
     */
    @Test
    void err007RepeatedKeyAlreadyClaimedByTheStoreFailsWithItsOwnExceptionAfterARestart() {
        FakeIdempotencyStore store = new FakeIdempotencyStore();
        AtomicInteger executions = new AtomicInteger();

        IdempotencyRegistry beforeRestart = new IdempotencyRegistry(store);
        String first = beforeRestart.execute("account.register", "idem-1", () -> {
            executions.incrementAndGet();
            return "created";
        });
        String sameProcess = beforeRestart.execute("account.register", "idem-1", () -> {
            executions.incrementAndGet();
            return "again";
        });
        IdempotencyRegistry afterRestart = new IdempotencyRegistry(store);

        assertThat(first).isEqualTo("created");
        assertThat(sameProcess).as("en el mismo proceso la memoria devuelve el resultado anterior").isEqualTo("created");
        assertThatExceptionOfType(IdempotencyKeyAlreadyUsedException.class)
            .isThrownBy(() -> afterRestart.execute("account.register", "idem-1", () -> {
                executions.incrementAndGet();
                return "again";
            }))
            .withMessage("La solicitud con Idempotency-Key ya fue procesada");
        assertThat(executions).as("la operación solo se ejecutó la primera vez").hasValue(1);
    }

    /** Imita el contrato de JpaIdempotencyStore: reclamar una pareja operación-clave solo funciona una vez. */
    private static class FakeIdempotencyStore implements IdempotencyStore {
        private final Set<String> claimed = new HashSet<>();

        @Override
        public boolean claim(String operation, String key) {
            return claimed.add(operation + ":" + key);
        }

        @Override
        public void release(String operation, String key) {
            claimed.remove(operation + ":" + key);
        }
    }
}
