package com.jcode.jcomercy.sales.application.service;

import com.jcode.jcomercy.sales.application.exception.SalesApplicationException;
import com.jcode.jcomercy.sales.domain.model.sale.Sale;
import com.jcode.jcomercy.sales.domain.model.sale.SaleChannel;
import com.jcode.jcomercy.sales.domain.model.sale.SaleLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Consultar las ventas de una sucursal: solo se ven las de esa empresa y esa sucursal. */
class SaleQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T18:00:00Z");

    private InMemorySalesPorts.Sales sales;
    private SaleQueryService service;

    @BeforeEach
    void service() {
        sales = new InMemorySalesPorts.Sales();
        service = new SaleQueryService(sales);
    }

    @Test
    @DisplayName("Una venta se consulta por su empresa, su sucursal y su identificador")
    void aSaleIsFoundByCompanyBranchAndId() {
        Sale sale = sales.save(sale("cmp_1", "br_1", NOW));

        assertThat(service.get("cmp_1", "br_1", sale.id())).isEqualTo(sale);
    }

    @Test
    @DisplayName("Una venta de otra sucursal o de otra empresa no se encuentra, igual que una que no existe")
    void aSaleOfAnotherBranchOrCompanyIsNotFound() {
        Sale sale = sales.save(sale("cmp_1", "br_1", NOW));

        for (var lookup : List.of(List.of("cmp_1", "br_2"), List.of("cmp_2", "br_1"))) {
            assertThatThrownBy(() -> service.get(lookup.get(0), lookup.get(1), sale.id()))
                .isInstanceOf(SalesApplicationException.class)
                .hasMessage("No encontramos esa venta en esta sucursal.")
                .satisfies(e -> assertThat(((SalesApplicationException) e).kind()).isEqualTo(SalesApplicationException.Kind.NOT_FOUND));
        }
        assertThatThrownBy(() -> service.get("cmp_1", "br_1", UUID.randomUUID())).isInstanceOf(SalesApplicationException.class);
    }

    @Test
    @DisplayName("La lista trae solo las ventas de la sucursal, de la más reciente a la más antigua")
    void theListHoldsTheBranchSalesNewestFirst() {
        Sale older = sales.save(sale("cmp_1", "br_1", NOW));
        Sale newer = sales.save(sale("cmp_1", "br_1", NOW.plusSeconds(60)));
        sales.save(sale("cmp_1", "br_2", NOW));
        sales.save(sale("cmp_2", "br_1", NOW));

        assertThat(service.list("cmp_1", "br_1")).containsExactly(newer, older);
    }

    private static Sale sale(String company, String branch, Instant at) {
        return Sale.create(UUID.randomUUID(), company, branch, UUID.randomUUID(), SaleChannel.MOSTRADOR, null, "MXN",
            List.of(SaleLine.of(UUID.randomUUID(), "CAFE", "Café", BigDecimal.ONE, new BigDecimal("45.50"))), "usr_1", at);
    }
}
