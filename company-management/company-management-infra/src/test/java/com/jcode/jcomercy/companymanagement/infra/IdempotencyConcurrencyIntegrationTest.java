package com.jcode.jcomercy.companymanagement.infra;

import com.jayway.jsonpath.JsonPath;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.AccountSpringDataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Fase P3: la misma {@code Idempotency-Key} con el mismo cuerpo, enviada varias veces a la vez. Las pruebas de claves
 * repetidas ({@code CompanyRepeatedKeyIntegrationTest}) mandan la repetición después de que la primera terminó; aquí
 * llegan todas juntas, y la clave se reclama con un choque de clave única que en PostgreSQL aborta la transacción.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountSpringDataRepository accounts;

    @Test
    @DisplayName("La misma Idempotency-Key repetida a la vez registra la cuenta una sola vez y ninguna petición falla")
    void theSameKeyInParallelRegistersTheAccountOnce() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "paralelo-" + suffix + "@example.com";
        String rfc = "PAR" + suffix.toUpperCase().replace("-", "").substring(0, 7);
        String body = """
            {"name":"Cuenta paralela %s","owner":{"firstName":"Persona","lastName":"Prueba","birthDate":"1985-04-12",
            "rfc":"%s","phoneNumber":"5512345678","email":"%s","password":"ClaveSegura123!",
            "state":"Ciudad de México","address":"Av. Reforma 100"}}
            """.formatted(suffix, rfc, email);
        String key = "idem-par-" + UUID.randomUUID();

        List<MockHttpServletResponse> responses = inParallel(6, () -> mockMvc.perform(post("/api/v1/accounts/registration")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn().getResponse());

        // Una crea la cuenta; las demás reciben la misma respuesta o el conflicto de la clave ya usada. Ninguna debe
        // fallar con un 500, y todas las que dicen "creada" deben hablar de la misma cuenta.
        List<Integer> statuses = responses.stream().map(MockHttpServletResponse::getStatus).toList();
        assertThat(statuses).allMatch(status -> status == 201 || status == 409);
        assertThat(statuses).contains(201);
        Set<String> accountIds = new HashSet<>();
        for (MockHttpServletResponse response : responses) {
            if (response.getStatus() == 201) {
                accountIds.add(JsonPath.read(response.getContentAsString(), "$.publicAccountId"));
            }
        }
        assertThat(accountIds).as("cuentas distintas creadas con la misma clave").hasSize(1);
        // Si hubiera dos cuentas con ese correo, esta consulta fallaría por resultado no único.
        assertThat(accounts.findByOwner_Email(email)).isPresent();
    }

    /** Lanza {@code count} tareas a la vez, todas esperando la misma señal de salida, y devuelve lo que cada una respondió. */
    private <T> List<T> inParallel(int count, Callable<T> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Future<T>> pending = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                pending.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> one : pending) {
                results.add(one.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
