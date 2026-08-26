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
    @DisplayName("Debe crear y activar una empresa correctamente en el flujo completo REST")
    void shouldCreateAndActivateCompany() throws Exception {
        // 1. Create Account
        String accJson = """
            { "name": "Holding Test", "ownerUserId": "usr_hold_1" }
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
            { "legalName": "Subempresa Uno S.A.", "tradeName": "SubEmpresa1", "taxId": "RFC111111111" }
            """;

        String compRes = mockMvc.perform(post("/api/v1/accounts/" + account.publicAccountId() + "/companies")
                .header("Idempotency-Key", "idem_comp_test_001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(compJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.publicCompanyId").value(org.hamcrest.Matchers.startsWith("com_")))
            .andExpect(jsonPath("$.legalName").value("Subempresa Uno S.A."))
            .andExpect(jsonPath("$.status").value("CONFIGURING"))
            .andReturn().getResponse().getContentAsString();

        String publicCompanyId = objectMapper.readTree(compRes).get("publicCompanyId").asText();

        // 3. Activate Company
        mockMvc.perform(post("/api/v1/companies/" + publicCompanyId + "/activation"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
