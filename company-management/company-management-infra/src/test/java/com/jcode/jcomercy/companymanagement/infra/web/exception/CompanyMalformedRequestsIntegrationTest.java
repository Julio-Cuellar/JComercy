package com.jcode.jcomercy.companymanagement.infra.web.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Una solicitud mal hecha por quien llama es un error suyo (4xx), no un fallo nuestro (500). */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyMalformedRequestsIntegrationTest {

    private static final String JSON_ILEGIBLE =
        "No pudimos leer lo que enviaste. Revisa que sea un JSON válido y que cada dato tenga el formato correcto.";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Un JSON mal formado responde 400 y no 500")
    void malformedJsonRespondsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{no-es-json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
            .andExpect(jsonPath("$.detail").value(JSON_ILEGIBLE));
    }

    @Test
    @DisplayName("Una solicitud sin cuerpo responde 400 y no 500")
    void missingBodyRespondsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("Una fecha con formato inválido responde 400 sin mostrar el detalle interno del lector de JSON")
    void invalidDateFormatRespondsBadRequest() throws Exception {
        String body = """
            {"name":"Abarrotes Lupita","owner":{"firstName":"Lupita","lastName":"Pérez","birthDate":"ayer",
            "email":"lupita@example.com","password":"clave-segura-1"}}""";

        mockMvc.perform(post("/api/v1/accounts/registration").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "malformed-date-001").content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
            .andExpect(jsonPath("$.detail").value(JSON_ILEGIBLE));
    }

    @Test
    @DisplayName("Si falta la Idempotency-Key responde 400 y dice cuál falta")
    void missingIdempotencyKeyRespondsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/registration").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Abarrotes Lupita\",\"owner\":{\"firstName\":\"Lupita\"}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("MISSING_HEADER"))
            .andExpect(jsonPath("$.detail").value(
                "Falta el encabezado Idempotency-Key. Agrégalo a la solicitud e intenta de nuevo."));
    }

    @Test
    @DisplayName("Un registro sin los datos del dueño responde 400 e indica qué falta")
    void registrationWithoutOwnerRespondsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/registration").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "missing-owner-001").content("{\"name\":\"Abarrotes Lupita\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.detail").value("Revisa estos datos de la solicitud: owner (es obligatorio)."));
    }

    @Test
    @DisplayName("Si la Idempotency-Key viene vacía responde 400 y no 500 (ERR-006)")
    void blankIdempotencyKeyRespondsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/registration").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "").content("{\"name\":\"Abarrotes Lupita\",\"owner\":{\"firstName\":\"Lupita\"}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("MISSING_HEADER"))
            .andExpect(jsonPath("$.detail").value(
                "El encabezado Idempotency-Key está vacío. Envía un valor único para cada operación e intenta de nuevo."));
    }

    @Test
    @DisplayName("Una contraseña de menos de 8 caracteres responde 422 con la regla en palabras del negocio")
    void shortPasswordRespondsUnprocessableEntity() throws Exception {
        String body = """
            {"name":"Abarrotes Lupita","owner":{"firstName":"Lupita","lastName":"Pérez","birthDate":"1990-01-01",
            "rfc":"PEPL900101AB1","phoneNumber":"5512345678","email":"lupita.corta@example.com","state":"CDMX",
            "address":"Calle 1","password":"corta"}}""";

        mockMvc.perform(post("/api/v1/accounts/registration").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "short-password-001").content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.code").value("DOMAIN_RULE_VIOLATION"))
            .andExpect(jsonPath("$.detail").value("La contraseña debe tener al menos 8 caracteres."));
    }

    @Test
    @DisplayName("Un cuerpo que no es JSON responde 415 y no 500")
    void unsupportedContentTypeRespondsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("hola"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.status").value(415))
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
            .andExpect(jsonPath("$.detail").value(
                "Esa dirección no acepta este tipo de contenido. Envía los datos como JSON con el encabezado Content-Type: application/json."));
    }
}
