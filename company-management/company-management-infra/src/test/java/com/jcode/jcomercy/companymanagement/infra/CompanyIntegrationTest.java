package com.jcode.jcomercy.companymanagement.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.companymanagement.application.dto.AccountDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CompanyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Debe autenticar correo y contraseña registrados y emitir un JWT")
    void shouldLoginAndIssueJwt() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/registration")
                .header("Idempotency-Key", "idem_login_account")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Cuenta Login","owner":{"firstName":"Login","lastName":"Test","birthDate":"1985-04-12","rfc":"LOGI850412ABC","phoneNumber":"5512345678","email":"login@example.com","password":"LoginPassword123!","state":"Ciudad de México","address":"Reforma 100"}}
                    """))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"login@example.com\",\"password\":\"LoginPassword123!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @DisplayName("Debe rechazar credenciales inválidas")
    void shouldRejectInvalidLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"login@example.com\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Debe crear y activar una empresa correctamente en el flujo completo REST")
    void shouldCreateAndActivateCompany() throws Exception {
        // 1. Create Account
        String accJson = """
            {
              "name": "Holding Test",
              "owner": {
                "firstName": "Julio",
                "lastName": "Pérez López",
                "birthDate": "1985-04-12",
                "rfc": "HOLD850412ABC",
                "phoneNumber": "5512345678",
                "email": "holding-owner@example.com",
                "password": "HoldingPassword123!",
                "state": "Ciudad de México",
                "address": "Av. Reforma 100"
              }
            }
            """;

        String accRes = mockMvc.perform(post("/api/v1/accounts/registration")
                .header("Idempotency-Key", "idem_acc_company_test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(accJson))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        AccountDto account = objectMapper.readValue(accRes, AccountDto.class);

        // 2. Create Company
        String compJson = """
            {
              "legalName": "Subempresa Uno S.A.",
              "tradeName": "SubEmpresa1",
              "rfc": "RFC111111111",
              "taxRegime": "601",
              "incorporationDate": "2020-01-15",
              "phone": "5512345678",
              "email": "empresa@example.com",
              "website": "https://example.com",
              "logoUrl": "https://example.com/logo.png",
              "fiscalAddress": {
                "calle": "Reforma",
                "numeroExterior": "100",
                "numeroInterior": "10",
                "colonia": "Juárez",
                "codigoPostal": "06600",
                "municipioAlcaldia": "Cuauhtémoc",
                "estado": "Ciudad de México",
                "pais": "México"
              }
            }
            """;

        String compRes = mockMvc.perform(post("/api/v1/accounts/" + account.publicAccountId() + "/companies")
                .header("Idempotency-Key", "idem_comp_test_001")
                .header("Authorization", "Bearer " + token(account.publicAccountId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(compJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.publicCompanyId").value(org.hamcrest.Matchers.startsWith("com_")))
            .andExpect(jsonPath("$.legalName").value("Subempresa Uno S.A."))
            .andExpect(jsonPath("$.rfc").value("RFC111111111"))
            .andExpect(jsonPath("$.fiscalAddress.calle").value("Reforma"))
            .andExpect(jsonPath("$.status").value("CONFIGURING"))
            .andReturn().getResponse().getContentAsString();

        String publicCompanyId = objectMapper.readTree(compRes).get("publicCompanyId").asText();

        // 3. Activate Company
        mockMvc.perform(post("/api/v1/companies/" + publicCompanyId + "/activation")
                .header("Idempotency-Key", "idem_activate_company_001")
                .header("Authorization", "Bearer " + token(account.publicAccountId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 4. Create Branch with mandatory contact and location data
        mockMvc.perform(post("/api/v1/companies/" + publicCompanyId + "/branches")
                .header("Idempotency-Key", "idem_branch_company_test_001")
                .header("Authorization", "Bearer " + token(account.publicAccountId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "SUC-001",
                      "name": "Sucursal Centro",
                      "type": "MATRIZ",
                      "phoneNumber": "5555555555",
                      "email": "centro@example.com",
                      "rfc": "SUC850412ABC",
                      "fiscalName": "Sucursal Centro Fiscal",
                      "fiscalPostalCode": "06000",
                      "centroCostoId": "cc_centro",
                      "almacenId": "alm_centro",
                      "listaPreciosId": "lp_retail",
                      "serieFacturacion": "CENTRO",
                      "folioActual": 12,
                      "moneda": "MXN",
                      "responsableId": "usr_hold_1",
                      "fechaApertura": "2026-01-01",
                      "domicilio": {
                        "calle": "Juárez",
                        "numeroExterior": "50",
                        "numeroInterior": "",
                        "colonia": "Centro",
                        "codigoPostal": "06000",
                        "municipioAlcaldia": "Cuauhtémoc",
                        "estado": "Ciudad de México",
                        "pais": "México"
                      }
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.domicilio.calle").value("Juárez"))
            .andExpect(jsonPath("$.phoneNumber").value("5555555555"))
            .andExpect(jsonPath("$.type").value("MATRIZ"))
            .andExpect(jsonPath("$.email").value("centro@example.com"))
            .andExpect(jsonPath("$.rfc").value("SUC850412ABC"))
            .andExpect(jsonPath("$.folioActual").value(12))
            .andExpect(jsonPath("$.moneda").value("MXN"))
            .andExpect(jsonPath("$.domicilio.codigoPostal").value("06000"))
            .andExpect(jsonPath("$.domicilio.estado").value("Ciudad de México"))
            .andExpect(jsonPath("$.domicilio.municipioAlcaldia").value("Cuauhtémoc"));
    }

    @Test
    @DisplayName("Debe rechazar mutaciones sin identidad autenticada")
    void shouldRejectMutationWithoutPrincipal() throws Exception {
        mockMvc.perform(post("/api/v1/companies/com_missing/activation")
                .header("Idempotency-Key", "idem_missing_principal"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @DisplayName("Debe rechazar un JWT con firma inválida")
    void shouldRejectInvalidJwt() throws Exception {
        mockMvc.perform(post("/api/v1/companies/com_missing/activation")
                .header("Idempotency-Key", "idem_invalid_jwt")
                .header("Authorization", "Bearer not-a-valid-jwt"))
            .andExpect(status().isUnauthorized());
    }

    private static String token(String subject) {
        SecretKey key = Keys.hmacShaKeyFor("local-only-change-me-012345678901234567890123456789".getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
            .subject(subject)
            .issuer("jcomercy")
            .issuedAt(now)
            .expiration(new Date(now.getTime() + 300_000))
            .signWith(key)
            .compact();
    }
}
