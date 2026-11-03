package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ERR-011, fase B4: cada persona puede ponerle, si quiere, una contraseña propia a su acceso a una empresa. Con ella
 * hay que escribirla para entrar; cinco intentos fallidos bloquean ese acceso quince minutos, y el contador vive en la
 * base de datos. La contraseña de empresa no sustituye los permisos: solo protege la puerta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CompanyAccessPasswordIntegrationTest {

    private static final String ACCOUNT_PASSWORD = RestWorld.PASSWORD;
    private static final String COMPANY_PASSWORD = "LlaveDeLaEmpresa42";
    private static final String COMPANY_DENIED = "No tienes acceso a esta empresa. Pide acceso a quien administra la cuenta.";
    private static final String ACCOUNT_DENIED = "No tienes acceso a esta cuenta. Inicia sesión con la cuenta que corresponde.";
    private static final String ACCOUNT_ONLY =
        "Esto se hace desde tu cuenta, no desde una empresa. Vuelve a tu cuenta e intenta de nuevo.";
    private static final String PASSWORD_REQUIRED = "Esta empresa pide su contraseña. Escríbela para entrar.";
    private static final String CURRENT_REQUIRED = "Para cambiar la contraseña de esta empresa, escribe la actual.";
    private static final String TOO_SHORT = "La contraseña debe tener al menos 8 caracteres.";
    private static final String SAME_AS_ACCOUNT = "La contraseña de la empresa debe ser distinta a la de tu cuenta.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private RestWorld world;
    private RestWorld.Person alice;
    private RestWorld.Person bob;
    private RestWorld.Person carlos;
    private String aliceLogin;
    private String bobLogin;
    private String companyA;
    private String companyA2;
    private String companyB;
    private String companyC;

    /**
     * Alice es dueña de las empresas A y A2 y personal de la empresa B, que es de Bob. Carlos es dueño de la empresa C,
     * a la que Alice no pertenece.
     */
    @BeforeEach
    void people() throws Exception {
        world = new RestWorld(mockMvc, objectMapper, jdbc);
        alice = world.register();
        bob = world.register();
        carlos = world.register();
        aliceLogin = world.login(alice.email());
        bobLogin = world.login(bob.email());
        String carlosLogin = world.login(carlos.email());

        companyA = world.createCompany(alice.accountId(), aliceLogin);
        companyA2 = world.createCompany(alice.accountId(), aliceLogin);
        companyB = world.createCompany(bob.accountId(), bobLogin);
        companyC = world.createCompany(carlos.accountId(), carlosLogin);
        world.addMember(companyB, alice.accountId(), "ACTIVE", "[\"STAFF\"]");
    }

    // ---- Ponerla y verla ----

    @Test
    @DisplayName("La lista de empresas marca cuáles tienen contraseña")
    void theListMarksTheCompaniesThatHaveAPassword() throws Exception {
        assertThat(protectedFlags()).containsEntry(companyA, false).containsEntry(companyA2, false);

        setPassword(aliceLogin, alice.accountId(), companyA, null, COMPANY_PASSWORD).andExpect(status().isNoContent());

        assertThat(protectedFlags()).containsEntry(companyA, true).containsEntry(companyA2, false).containsEntry(companyB, false);
    }

    @Test
    @DisplayName("Solo se guarda la huella cifrada de la contraseña, nunca la contraseña")
    void onlyTheHashIsStored() throws Exception {
        setPassword(aliceLogin, alice.accountId(), companyA, null, COMPANY_PASSWORD).andExpect(status().isNoContent());

        Map<String, Object> row = row(alice.accountId(), companyA);
        assertThat((String) row.get("password_hash")).startsWith("$2").isNotEqualTo(COMPANY_PASSWORD).doesNotContain(COMPANY_PASSWORD);
        assertThat(((Number) row.get("failed_attempts")).intValue()).isZero();
        assertThat(row.get("locked_until")).isNull();
    }

    @Test
    @DisplayName("La contraseña nueva debe tener al menos 8 caracteres y ser distinta a la de la cuenta")
    void theNewPasswordMustBeLongEnoughAndDifferentFromTheAccountOne() throws Exception {
        problem(setPassword(aliceLogin, alice.accountId(), companyA, null, "corta"), 422, "DOMAIN_RULE_VIOLATION", TOO_SHORT);
        problem(setPassword(aliceLogin, alice.accountId(), companyA, null, ACCOUNT_PASSWORD), 422, "DOMAIN_RULE_VIOLATION", SAME_AS_ACCOUNT);

        assertThat(rows(alice.accountId(), companyA)).isZero();
        enterWith(aliceLogin, alice.accountId(), companyA, null).andExpect(status().isOk());
    }

    // ---- Entrar ----

    @Test
    @DisplayName("Entrar a una empresa con contraseña la pide, rechaza la equivocada y acepta la correcta")
    void enteringAProtectedCompanyNeedsThePassword() throws Exception {
        protect(companyA);

        problem(enterWith(aliceLogin, alice.accountId(), companyA, null), 403, "COMPANY_PASSWORD_REQUIRED", PASSWORD_REQUIRED);
        problem(enterWith(aliceLogin, alice.accountId(), companyA, "otra-cosa-123"), 403, "COMPANY_PASSWORD_INVALID",
            wrongPassword(4));

        ResultActions entered = enterWith(aliceLogin, alice.accountId(), companyA, COMPANY_PASSWORD).andExpect(status().isOk());
        JsonNode claims = world.claimsOf("Bearer " + body(entered).get("accessToken").asText());
        assertThat(claims.get("ctx").asText()).isEqualTo(companyA);
        assertThat(claims.get("permissions").asText()).isEqualTo("*");
    }

    @Test
    @DisplayName("El contador de intentos fallidos se guarda en la base de datos y se borra al entrar bien")
    void theCounterLivesInTheDatabase() throws Exception {
        protect(companyA);

        enterWith(aliceLogin, alice.accountId(), companyA, "mal-uno-123").andExpect(status().isForbidden());
        enterWith(aliceLogin, alice.accountId(), companyA, "mal-dos-123").andExpect(status().isForbidden());
        assertThat(failedAttempts(alice.accountId(), companyA)).isEqualTo(2);

        enterWith(aliceLogin, alice.accountId(), companyA, COMPANY_PASSWORD).andExpect(status().isOk());
        assertThat(failedAttempts(alice.accountId(), companyA)).isZero();

        // Tras entrar bien se parte de cero: cuatro fallos más no bloquean.
        for (int i = 0; i < 4; i++) {
            enterWith(aliceLogin, alice.accountId(), companyA, "mal-otra-vez-" + i).andExpect(status().isForbidden());
        }
        enterWith(aliceLogin, alice.accountId(), companyA, COMPANY_PASSWORD).andExpect(status().isOk());
    }

    // ---- El bloqueo ----

    @Test
    @DisplayName("Cinco contraseñas equivocadas bloquean el acceso quince minutos, aun si después se escribe la correcta")
    void fiveWrongPasswordsLockTheAccessForFifteenMinutes() throws Exception {
        protect(companyA);

        for (int left = 4; left >= 1; left--) {
            problem(enterWith(aliceLogin, alice.accountId(), companyA, "mal-" + left + "-intento"), 403,
                "COMPANY_PASSWORD_INVALID", wrongPassword(left));
        }
        enterWith(aliceLogin, alice.accountId(), companyA, "mal-quinto-intento")
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("COMPANY_ACCESS_LOCKED"))
            .andExpect(jsonPath("$.detail").value(locked("15 minutos")))
            .andExpect(header().string("Retry-After", "900"));

        OffsetDateTime lockedUntil = lockedUntil(alice.accountId(), companyA);
        assertThat(lockedUntil).isBetween(OffsetDateTime.now().plusMinutes(14), OffsetDateTime.now().plusMinutes(16));
        // Al bloquearse el contador vuelve a cero: pasado el bloqueo hacen falta otros cinco fallos, no uno.
        assertThat(failedAttempts(alice.accountId(), companyA)).isZero();

        // Con el acceso bloqueado, ni la contraseña correcta entra.
        enterWith(aliceLogin, alice.accountId(), companyA, COMPANY_PASSWORD)
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("COMPANY_ACCESS_LOCKED"))
            .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    @DisplayName("Pasado el bloqueo se puede entrar, y los intentos vuelven a contarse desde cero")
    void theLockEndsAndTheCountStartsOver() throws Exception {
        protect(companyA);
        lockNow(alice.accountId(), companyA);

        // Falta medio minuto: el aviso lo dice en singular.
        jdbc.update("UPDATE company_management.company_access_password SET locked_until=? WHERE user_id=? AND public_company_id=?",
            OffsetDateTime.now().plusSeconds(30), alice.accountId(), companyA);
        enterWith(aliceLogin, alice.accountId(), companyA, COMPANY_PASSWORD)
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.detail").value(locked("1 minuto")));

        // El bloqueo ya pasó.
        jdbc.update("UPDATE company_management.company_access_password SET locked_until=? WHERE user_id=? AND public_company_id=?",
            OffsetDateTime.now().minusSeconds(1), alice.accountId(), companyA);
        problem(enterWith(aliceLogin, alice.accountId(), companyA, "mal-despues-123"), 403, "COMPANY_PASSWORD_INVALID", wrongPassword(4));
        enterWith(aliceLogin, alice.accountId(), companyA, COMPANY_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Bloquear una empresa no afecta a las otras empresas de la misma persona")
    void aLockOnOneCompanyDoesNotAffectAnother() throws Exception {
        protect(companyA);
        protect(companyA2);
        lockNow(alice.accountId(), companyA);

        enterWith(aliceLogin, alice.accountId(), companyA, COMPANY_PASSWORD).andExpect(status().isTooManyRequests());
        enterWith(aliceLogin, alice.accountId(), companyA2, COMPANY_PASSWORD).andExpect(status().isOk());
    }

    // ---- Cambiarla ----

    @Test
    @DisplayName("Cambiar la contraseña exige la actual; la anterior deja de valer")
    void changingThePasswordNeedsTheCurrentOne() throws Exception {
        protect(companyA);

        problem(setPassword(aliceLogin, alice.accountId(), companyA, null, "NuevaLlave2026"), 403,
            "COMPANY_PASSWORD_REQUIRED", CURRENT_REQUIRED);
        problem(setPassword(aliceLogin, alice.accountId(), companyA, "no-es-la-actual", "NuevaLlave2026"), 403,
            "COMPANY_PASSWORD_INVALID", wrongPassword(4));
        enterWith(aliceLogin, alice.accountId(), companyA, COMPANY_PASSWORD).andExpect(status().isOk());

        setPassword(aliceLogin, alice.accountId(), companyA, COMPANY_PASSWORD, "NuevaLlave2026").andExpect(status().isNoContent());

        enterWith(aliceLogin, alice.accountId(), companyA, COMPANY_PASSWORD).andExpect(status().isForbidden());
        enterWith(aliceLogin, alice.accountId(), companyA, "NuevaLlave2026").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Equivocarse al cambiar la contraseña cuenta para el mismo bloqueo que equivocarse al entrar")
    void wrongCurrentPasswordsShareTheLockWithEntering() throws Exception {
        protect(companyA);

        for (int i = 0; i < 4; i++) {
            setPassword(aliceLogin, alice.accountId(), companyA, "mal-actual-" + i + "-x", "NuevaLlave2026")
                .andExpect(status().isForbidden());
        }
        setPassword(aliceLogin, alice.accountId(), companyA, "mal-actual-final", "NuevaLlave2026")
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("COMPANY_ACCESS_LOCKED"));

        enterWith(aliceLogin, alice.accountId(), companyA, COMPANY_PASSWORD).andExpect(status().isTooManyRequests());
    }

    // ---- Quitarla ----

    @Test
    @DisplayName("Quitar la contraseña exige la contraseña de la cuenta, no la de la empresa: es la salida si se olvidó")
    void removingThePasswordNeedsTheAccountPassword() throws Exception {
        protect(companyA);

        // Sin escribirla, se pide; y no gasta un intento.
        problem(removePassword(aliceLogin, alice.accountId(), companyA, null), 403, "ACCOUNT_PASSWORD_REQUIRED",
            "Para quitar la contraseña de esta empresa, escribe la contraseña de tu cuenta.");
        problem(removePassword(aliceLogin, alice.accountId(), companyA, "no-es-la-de-cuenta"), 403,
            "ACCOUNT_PASSWORD_INVALID", wrongAccountPassword(4));
        problem(removePassword(aliceLogin, alice.accountId(), companyA, COMPANY_PASSWORD), 403,
            "ACCOUNT_PASSWORD_INVALID", wrongAccountPassword(3));
        assertThat(protectedFlags()).containsEntry(companyA, true);

        // La olvidó: con la contraseña de la cuenta basta.
        removePassword(aliceLogin, alice.accountId(), companyA, ACCOUNT_PASSWORD).andExpect(status().isNoContent());

        assertThat(protectedFlags()).containsEntry(companyA, false);
        assertThat(rows(alice.accountId(), companyA)).isZero();
        enterWith(aliceLogin, alice.accountId(), companyA, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Quitar la contraseña de un acceso que no la tiene no cambia nada")
    void removingWhenThereIsNoPasswordChangesNothing() throws Exception {
        removePassword(aliceLogin, alice.accountId(), companyA, ACCOUNT_PASSWORD).andExpect(status().isNoContent());

        assertThat(rows(alice.accountId(), companyA)).isZero();
        enterWith(aliceLogin, alice.accountId(), companyA, null).andExpect(status().isOk());
    }

    // ---- A quién le toca ----

    @Test
    @DisplayName("Quien no pertenece a la empresa recibe la misma respuesta que ante una que no existe, y no se guarda nada")
    void aPersonWhoDoesNotBelongGetsTheSameAnswerAsForAMissingCompany() throws Exception {
        for (String company : List.of(companyC, "com_no_existe_1234")) {
            problem(enterWith(aliceLogin, alice.accountId(), company, COMPANY_PASSWORD), 403, "INSUFFICIENT_PERMISSIONS", COMPANY_DENIED);
            problem(setPassword(aliceLogin, alice.accountId(), company, null, COMPANY_PASSWORD), 403, "INSUFFICIENT_PERMISSIONS", COMPANY_DENIED);
            problem(removePassword(aliceLogin, alice.accountId(), company, ACCOUNT_PASSWORD), 403, "INSUFFICIENT_PERMISSIONS", COMPANY_DENIED);
            assertThat(rows(alice.accountId(), company)).isZero();
        }
    }

    @Test
    @DisplayName("Otra cuenta no puede tocar mis contraseñas, y el token de una empresa tampoco")
    void anotherAccountOrACompanyTokenCannotTouchMyPasswords() throws Exception {
        problem(setPassword(bobLogin, alice.accountId(), companyA, null, COMPANY_PASSWORD), 403, "INSUFFICIENT_PERMISSIONS", ACCOUNT_DENIED);
        problem(removePassword(bobLogin, alice.accountId(), companyA, ACCOUNT_PASSWORD), 403, "INSUFFICIENT_PERMISSIONS", ACCOUNT_DENIED);

        String aliceInA = world.enter(alice.accountId(), companyA, aliceLogin);
        problem(setPassword(aliceInA, alice.accountId(), companyA, null, COMPANY_PASSWORD), 403, "INSUFFICIENT_PERMISSIONS", ACCOUNT_ONLY);
        problem(removePassword(aliceInA, alice.accountId(), companyA, ACCOUNT_PASSWORD), 403, "INSUFFICIENT_PERMISSIONS", ACCOUNT_ONLY);

        assertThat(rows(alice.accountId(), companyA)).isZero();
    }

    @Test
    @DisplayName("Cada persona tiene su propia contraseña para la misma empresa, y no cambia lo que puede hacer")
    void eachPersonHasTheirOwnPasswordForTheSameCompany() throws Exception {
        // Alice es personal de la empresa B, que es de Bob.
        setPassword(aliceLogin, alice.accountId(), companyB, null, COMPANY_PASSWORD).andExpect(status().isNoContent());

        // Bob no tiene contraseña en su empresa y entra como siempre.
        enterWith(bobLogin, bob.accountId(), companyB, null).andExpect(status().isOk());
        problem(enterWith(aliceLogin, alice.accountId(), companyB, null), 403, "COMPANY_PASSWORD_REQUIRED", PASSWORD_REQUIRED);

        ResultActions entered = enterWith(aliceLogin, alice.accountId(), companyB, COMPANY_PASSWORD).andExpect(status().isOk());
        JsonNode claims = world.claimsOf("Bearer " + body(entered).get("accessToken").asText());
        assertThat(claims.get("permissions").asText()).doesNotContain("*");
        assertThat(rows(bob.accountId(), companyB)).isZero();
    }

    // ---- Lo que queda en el registro ----

    @Test
    @DisplayName("El registro avisa de los intentos fallidos y del bloqueo, y nunca contiene una contraseña")
    void logsWarnAboutFailuresAndNeverContainPasswords(CapturedOutput output) throws Exception {
        String wrongGuess = "adivinanza-secreta-7";
        String newPassword = "NuevaLlaveSecreta9";
        setPassword(aliceLogin, alice.accountId(), companyA, null, COMPANY_PASSWORD).andExpect(status().isNoContent());
        for (int i = 0; i < 5; i++) {
            enterWith(aliceLogin, alice.accountId(), companyA, wrongGuess).andExpect(status().is4xxClientError());
        }
        jdbc.update("UPDATE company_management.company_access_password SET locked_until=NULL WHERE user_id=?", alice.accountId());
        setPassword(aliceLogin, alice.accountId(), companyA, COMPANY_PASSWORD, newPassword).andExpect(status().isNoContent());
        removePassword(aliceLogin, alice.accountId(), companyA, ACCOUNT_PASSWORD).andExpect(status().isNoContent());

        assertThat(output.getAll())
            .contains("Contraseña de empresa incorrecta accountId=" + alice.accountId())
            .contains("Acceso a empresa bloqueado accountId=" + alice.accountId())
            .doesNotContain(COMPANY_PASSWORD, wrongGuess, newPassword, ACCOUNT_PASSWORD);
    }

    // ---- Ayudas ----

    private static String wrongPassword(int left) {
        return "La contraseña de esta empresa no es correcta. " + attemptsLeft(left);
    }

    private static String wrongAccountPassword(int left) {
        return "Tu contraseña de cuenta no es correcta. " + attemptsLeft(left);
    }

    private static String attemptsLeft(int left) {
        return left == 1 ? "Te queda 1 intento antes de que se bloquee."
            : "Te quedan " + left + " intentos antes de que se bloquee.";
    }

    private static String locked(String time) {
        return "Esta empresa se bloqueó por demasiados intentos fallidos. Vuelve a intentarlo en " + time + ".";
    }

    /** Pone la contraseña de empresa de Alice, que es dueña, sin pasar por las pruebas de cada regla. */
    private void protect(String companyId) throws Exception {
        setPassword(aliceLogin, alice.accountId(), companyId, null, COMPANY_PASSWORD).andExpect(status().isNoContent());
    }

    private ResultActions enterWith(String bearer, String accountId, String companyId, String password) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/accounts/{id}/company-accesses/{target}/session", accountId, companyId)
            .header("Authorization", bearer);
        if (password != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json("password", password));
        }
        return mockMvc.perform(request);
    }

    private ResultActions setPassword(String bearer, String accountId, String companyId, String current, String next) throws Exception {
        return mockMvc.perform(put("/api/v1/accounts/{id}/company-accesses/{target}/password", accountId, companyId)
            .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
            .content(json("currentPassword", current, "newPassword", next)));
    }

    private ResultActions removePassword(String bearer, String accountId, String companyId, String accountPassword) throws Exception {
        return mockMvc.perform(delete("/api/v1/accounts/{id}/company-accesses/{target}/password", accountId, companyId)
            .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
            .content(json("accountPassword", accountPassword)));
    }

    private void problem(ResultActions result, int status, String code, String detail) throws Exception {
        result.andExpect(status().is(status))
            .andExpect(jsonPath("$.status").value(status))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.detail").value(detail));
    }

    private String json(String... pairs) throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                fields.put(pairs[i], pairs[i + 1]);
            }
        }
        return objectMapper.writeValueAsString(fields);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Qué empresas de Alice dicen estar protegidas con contraseña, según la lista «Tus empresas». */
    private Map<String, Boolean> protectedFlags() throws Exception {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (JsonNode access : body(mockMvc.perform(get("/api/v1/accounts/{id}/company-accesses", alice.accountId())
                .header("Authorization", aliceLogin)).andExpect(status().isOk()))) {
            flags.put(access.get("publicCompanyId").asText(), access.get("passwordProtected").asBoolean());
        }
        return flags;
    }

    private Map<String, Object> row(String accountId, String companyId) {
        return jdbc.queryForMap("""
            SELECT password_hash, failed_attempts, locked_until FROM company_management.company_access_password
            WHERE user_id=? AND public_company_id=?
            """, accountId, companyId);
    }

    private int rows(String accountId, String companyId) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM company_management.company_access_password WHERE user_id=? AND public_company_id=?
            """, Integer.class, accountId, companyId);
        return count == null ? 0 : count;
    }

    private int failedAttempts(String accountId, String companyId) {
        return ((Number) row(accountId, companyId).get("failed_attempts")).intValue();
    }

    private OffsetDateTime lockedUntil(String accountId, String companyId) {
        return jdbc.queryForObject("""
            SELECT locked_until FROM company_management.company_access_password WHERE user_id=? AND public_company_id=?
            """, OffsetDateTime.class, accountId, companyId);
    }

    /** Deja el acceso bloqueado por quince minutos, como lo dejarían cinco intentos fallidos. */
    private void lockNow(String accountId, String companyId) {
        jdbc.update("""
            UPDATE company_management.company_access_password SET locked_until=?, failed_attempts=0
            WHERE user_id=? AND public_company_id=?
            """, OffsetDateTime.now().plus(15, ChronoUnit.MINUTES), accountId, companyId);
    }
}
