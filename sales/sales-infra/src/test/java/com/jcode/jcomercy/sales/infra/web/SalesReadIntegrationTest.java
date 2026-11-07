package com.jcode.jcomercy.sales.infra.web;

import com.jcode.jcomercy.sales.domain.model.sale.Payment;
import com.jcode.jcomercy.sales.domain.model.sale.PaymentMethod;
import com.jcode.jcomercy.sales.domain.model.sale.Sale;
import com.jcode.jcomercy.sales.domain.model.sale.SaleChannel;
import com.jcode.jcomercy.sales.domain.model.sale.SaleLine;
import com.jcode.jcomercy.sales.domain.port.SaleRepositoryPort;
import com.jcode.jcomercy.sales.infra.SalesTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Consultar las ventas de una sucursal: lo que se ve, a quién se le niega y qué responde cuando no existe. */
@SpringBootTest
@AutoConfigureMockMvc
class SalesReadIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-07T18:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private SaleRepositoryPort sales;

    private String company;
    private String reader;

    @BeforeEach
    void aCompanyOfItsOwn() {
        company = "cmp_read_" + UUID.randomUUID();
        reader = SalesTokens.bearer(company, List.of("br_1", "br_2"), "sales.read");
    }

    @Test
    @DisplayName("La lista de una sucursal trae sus ventas de la más reciente a la más antigua, sin las de otra sucursal")
    void theListHoldsTheBranchSalesNewestFirst() throws Exception {
        Sale older = sales.save(sale("br_1", NOW));
        Sale newer = sales.save(sale("br_1", NOW.plusSeconds(60)));
        sales.save(sale("br_2", NOW));

        mockMvc.perform(get("/api/v1/companies/{c}/branches/{b}/sales", company, "br_1").header("Authorization", reader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].saleId").value(newer.id().toString()))
            .andExpect(jsonPath("$[1].saleId").value(older.id().toString()));
    }

    @Test
    @DisplayName("Una venta completada se ve con su folio, sus líneas, sus pagos y su cambio")
    void aCompletedSaleShowsItsFolioLinesPaymentsAndChange() throws Exception {
        Sale pending = sale("br_1", NOW);
        Sale done = pending.registerPayments(List.of(
                new Payment(PaymentMethod.CARD, new BigDecimal("50.00"), "AUT-77"), new Payment(PaymentMethod.CASH, new BigDecimal("60.00"), null)))
            .complete("TCK-000007", NOW.plusSeconds(30));
        sales.save(done);

        mockMvc.perform(get("/api/v1/companies/{c}/branches/{b}/sales/{s}", company, "br_1", pending.id()).header("Authorization", reader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saleId").value(pending.id().toString()))
            .andExpect(jsonPath("$.folio").value("TCK-000007"))
            .andExpect(jsonPath("$.channel").value("MOSTRADOR"))
            .andExpect(jsonPath("$.externalReference").doesNotExist())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.branchId").value("br_1"))
            .andExpect(jsonPath("$.currency").value("MXN"))
            .andExpect(jsonPath("$.total").value(101.0))
            .andExpect(jsonPath("$.received").value(110.0))
            .andExpect(jsonPath("$.change").value(9.0))
            .andExpect(jsonPath("$.actorUserId").value("usr_cajera"))
            .andExpect(jsonPath("$.completedAt").exists())
            .andExpect(jsonPath("$.lines", hasSize(2)))
            .andExpect(jsonPath("$.lines[0].lineNumber").value(1))
            .andExpect(jsonPath("$.lines[0].sku").value("CAFE"))
            .andExpect(jsonPath("$.lines[0].quantity").value(2.0))
            .andExpect(jsonPath("$.lines[0].unitPrice").value(45.5))
            .andExpect(jsonPath("$.lines[0].lineTotal").value(91.0))
            .andExpect(jsonPath("$.payments", hasSize(2)))
            .andExpect(jsonPath("$.payments[0].method").value("CARD"))
            .andExpect(jsonPath("$.payments[0].reference").value("AUT-77"));
    }

    @Test
    @DisplayName("Una venta cancelada muestra su motivo y su fecha")
    void aCancelledSaleShowsItsReason() throws Exception {
        Sale pending = sale("br_1", NOW);
        sales.save(pending.cancel("Se acabó el stock", NOW.plusSeconds(10)));

        mockMvc.perform(get("/api/v1/companies/{c}/branches/{b}/sales/{s}", company, "br_1", pending.id()).header("Authorization", reader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelReason").value("Se acabó el stock"))
            .andExpect(jsonPath("$.cancelledAt").exists());
    }

    @Test
    @DisplayName("Una venta que no existe, o que es de otra sucursal, responde 404 con un mensaje claro")
    void anUnknownOrForeignSaleIsNotFound() throws Exception {
        Sale other = sales.save(sale("br_2", NOW));

        mockMvc.perform(get("/api/v1/companies/{c}/branches/{b}/sales/{s}", company, "br_1", UUID.randomUUID()).header("Authorization", reader))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("No encontramos esa venta en esta sucursal."));
        mockMvc.perform(get("/api/v1/companies/{c}/branches/{b}/sales/{s}", company, "br_1", other.id()).header("Authorization", reader))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Sin sales.read, o sin acceso a la sucursal o a la empresa, se responde 403")
    void withoutPermissionOrScopeTheAnswerIsForbidden() throws Exception {
        Sale sale = sales.save(sale("br_1", NOW));
        String noPermission = SalesTokens.bearer(company, List.of("br_1"), "sales.create");
        String otherBranch = SalesTokens.bearer(company, List.of("br_2"), "sales.read");
        String otherCompany = SalesTokens.bearer("cmp_otra_" + UUID.randomUUID(), List.of("br_1"), "sales.read");

        for (String token : List.of(noPermission, otherBranch, otherCompany)) {
            mockMvc.perform(get("/api/v1/companies/{c}/branches/{b}/sales", company, "br_1").header("Authorization", token))
                .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/companies/{c}/branches/{b}/sales/{s}", company, "br_1", sale.id()).header("Authorization", token))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Sin token no se consulta nada")
    void withoutATokenNothingIsRead() throws Exception {
        mockMvc.perform(get("/api/v1/companies/{c}/branches/{b}/sales", company, "br_1")).andExpect(status().isUnauthorized());
    }

    private Sale sale(String branch, Instant at) {
        return Sale.create(UUID.randomUUID(), company, branch, UUID.randomUUID(), SaleChannel.MOSTRADOR, null, "MXN",
            List.of(SaleLine.of(UUID.randomUUID(), "CAFE", "Café", new BigDecimal("2"), new BigDecimal("45.50")),
                SaleLine.of(UUID.randomUUID(), "GALLETA", "Galleta", BigDecimal.ONE, new BigDecimal("10.00"))), "usr_cajera", at);
    }
}
