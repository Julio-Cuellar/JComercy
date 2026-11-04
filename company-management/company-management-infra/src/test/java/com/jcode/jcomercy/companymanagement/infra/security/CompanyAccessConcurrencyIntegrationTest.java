package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.companymanagement.domain.port.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parte de la fase P3: la contraseña de empresa con muchas peticiones a la vez. Las pruebas de
 * {@link CompanyAccessPasswordIntegrationTest} son secuenciales y no ven lo que pasa cuando los intentos llegan juntos.
 *
 * <p>El límite de cinco intentos protege contra adivinar la contraseña. Se mide cuántas contraseñas se llegan a
 * verificar de verdad, espiando el cifrador: es lo único que cuenta como un intento de adivinar, sea cual sea la
 * respuesta que reciba cada petición.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyAccessConcurrencyIntegrationTest {

    private static final String COMPANY_PASSWORD = "LlaveDeLaEmpresa42";
    private static final String GUESS = "intento-";
    private static final int BURST = 20;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @SpyBean
    private PasswordHasher hasher;

    private RestWorld world;
    private RestWorld.Person alice;
    private String aliceLogin;
    private String company;

    @BeforeEach
    void aCompanyOfAlice() throws Exception {
        world = new RestWorld(mockMvc, objectMapper, jdbc);
        alice = world.register();
        aliceLogin = world.login(alice.email());
        company = world.createCompany(alice.accountId(), aliceLogin);
    }

    @Test
    @DisplayName("Una ráfaga de contraseñas equivocadas no llega a probar más de cinco")
    void aBurstOfWrongPasswordsNeverTriesMoreThanTheAllowedGuesses() throws Exception {
        setPassword(null, COMPANY_PASSWORD).andExpect(status().isNoContent());
        Mockito.clearInvocations(hasher);

        List<Integer> statuses = inParallel(BURST, i -> enter(GUESS + i).andReturn().getResponse().getStatus());

        assertThat(guessesTried()).isLessThanOrEqualTo(CompanyAccessPasswords.MAX_FAILED_ATTEMPTS);
        assertThat(statuses).isSubsetOf(403, 429);
        // El acceso quedó bloqueado: ni la contraseña correcta entra.
        enter(COMPANY_PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Poner la contraseña por primera vez desde varias peticiones a la vez deja una sola fila y ningún error")
    void settingTheFirstPasswordInParallelLeavesASingleRow() throws Exception {
        List<Integer> statuses = inParallel(6, i -> setPassword(null, COMPANY_PASSWORD + i).andReturn().getResponse().getStatus());

        // Quien llega cuando la fila ya existe debe escribir la actual para cambiarla: eso es un 403, no un fallo.
        assertThat(statuses).isSubsetOf(204, 403).contains(204);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM company_management.company_access_password WHERE user_id=? AND public_company_id=?",
            Integer.class, alice.accountId(), company)).isEqualTo(1);
    }

    // ---- Ayudas ----

    private ResultActions enter(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/accounts/{id}/company-accesses/{target}/session", alice.accountId(), company)
            .header("Authorization", aliceLogin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"password\":\"%s\"}".formatted(password)));
    }

    private ResultActions setPassword(String current, String next) throws Exception {
        String currentJson = current == null ? "null" : "\"" + current + "\"";
        return mockMvc.perform(put("/api/v1/accounts/{id}/company-accesses/{target}/password", alice.accountId(), company)
            .header("Authorization", aliceLogin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":%s,\"newPassword\":\"%s\"}".formatted(currentJson, next)));
    }

    /** Cuántas contraseñas de las que se escribieron como intento se verificaron contra la huella guardada. */
    private long guessesTried() {
        return Mockito.mockingDetails(hasher).getInvocations().stream()
            .filter(call -> call.getMethod().getName().equals("matches"))
            .filter(call -> call.getArguments()[0] instanceof String raw && raw.startsWith(GUESS))
            .count();
    }

    /** Lanza {@code count} tareas a la vez, todas esperando la misma señal de salida, y devuelve lo que cada una respondió. */
    private <T> List<T> inParallel(int count, Attempt<T> attempt) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Future<T>> pending = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int number = i;
                Callable<T> task = () -> {
                    start.await();
                    return attempt.run(number);
                };
                pending.add(pool.submit(task));
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

    @FunctionalInterface
    private interface Attempt<T> {
        T run(int number) throws Exception;
    }
}
