package com.jcode.jcomercy.companymanagement.infra;

import com.jcode.jcomercy.companymanagement.infra.persistence.entity.OutboxEventJpaEntity;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.OutboxSpringDataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxSpringDataRepository outboxRepository;

    @Test
    @DisplayName("Debe registrar una cuenta por REST, persistirla y publicar evento outbox transaccional")
    void shouldRegisterAccountAndPublishOutboxEvent() throws Exception {
        String jsonRequest = """
            {
                "name": "Empresa Corporativa JComercy",
                "ownerUserId": "usr_owner_999"
            }
            """;

        String responseJson = mockMvc.perform(post("/api/v1/accounts/registration")
                .header("Idempotency-Key", "idem_test_account_001")
                .header("X-Correlation-Id", "cor_test_001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
            .andExpect(status().isCreated())
            .andExpect(header().exists("ETag"))
            .andExpect(jsonPath("$.publicAccountId").value(org.hamcrest.Matchers.startsWith("acc_")))
            .andExpect(jsonPath("$.name").value("Empresa Corporativa JComercy"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.ownerUserId").value("usr_owner_999"))
            .andReturn().getResponse().getContentAsString();

        // Check Outbox Event table in Database
        List<OutboxEventJpaEntity> events = outboxRepository.findAll();
        assertThat(events).isNotEmpty();
        OutboxEventJpaEntity createdEvent = events.stream()
            .filter(e -> "account.created".equals(e.getEventType()))
            .findFirst()
            .orElseThrow();

        assertThat(createdEvent.getProducerModule()).isEqualTo("company_management");
        assertThat(createdEvent.getSchemaVersion()).isEqualTo("1.0");
        assertThat(createdEvent.getStatus()).isEqualTo("PENDING");
        assertThat(createdEvent.getPayloadJson()).contains("Empresa Corporativa JComercy");
    }

    @Test
    @DisplayName("Debe fallar con 404 al consultar una cuenta inexistente")
    void shouldReturn404ForNonExistentAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/acc_non_existent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
