package com.jcode.jcomercy.sales.infra.persistence;

import com.jcode.jcomercy.sales.domain.exception.DuplicateSaleException;
import com.jcode.jcomercy.sales.domain.model.sale.Payment;
import com.jcode.jcomercy.sales.domain.model.sale.PaymentMethod;
import com.jcode.jcomercy.sales.domain.model.sale.Sale;
import com.jcode.jcomercy.sales.domain.model.sale.SaleChannel;
import com.jcode.jcomercy.sales.domain.model.sale.SaleLine;
import com.jcode.jcomercy.sales.domain.model.sale.SaleStatus;
import com.jcode.jcomercy.sales.domain.port.SaleRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las ventas con sus líneas y pagos en el esquema sales, y la unicidad que protege contra ventas repetidas. */
@SpringBootTest
class JdbcSaleRepositoryAdapterIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-07T18:00:00Z");

    @Autowired private SaleRepositoryPort sales;

    private String company;

    @BeforeEach
    void aCompanyOfItsOwn() {
        company = "cmp_repo_" + UUID.randomUUID();
    }

    @Test
    @DisplayName("Una venta nueva se guarda con sus líneas y se lee igual")
    void aNewSaleIsStoredWithItsLinesAndReadBackTheSame() {
        Sale sale = sale("br_1", NOW, null);

        sales.save(sale);

        Sale found = sales.findById(company, "br_1", sale.id()).orElseThrow();
        assertThat(found).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class).isEqualTo(sale);
        assertThat(found.lines()).extracting(SaleLine::lineNumber).containsExactly(1, 2);
        assertThat(found.total()).isEqualByComparingTo("101.00");
    }

    @Test
    @DisplayName("Los pagos, el folio y la fecha de cierre se guardan al avanzar la venta")
    void paymentsFolioAndCompletionAreStoredAsTheSaleAdvances() {
        Sale pending = sales.save(sale("br_1", NOW, null));
        Sale paid = pending.registerPayments(List.of(
            new Payment(PaymentMethod.CARD, new BigDecimal("50.00"), "AUT-1"), new Payment(PaymentMethod.CASH, new BigDecimal("60.00"), null)));
        sales.save(paid);

        Sale confirming = sales.findById(company, "br_1", pending.id()).orElseThrow();
        assertThat(confirming.status()).isEqualTo(SaleStatus.CONFIRMING);
        assertThat(confirming.payments()).extracting(Payment::method).containsExactly(PaymentMethod.CARD, PaymentMethod.CASH);
        assertThat(confirming.payments().get(0).reference()).isEqualTo("AUT-1");
        assertThat(confirming.received()).isEqualByComparingTo("110.00");
        assertThat(confirming.change()).isEqualByComparingTo("9.00");

        sales.save(confirming.complete("TCK-000001", NOW.plusSeconds(30)));

        Sale done = sales.findById(company, "br_1", pending.id()).orElseThrow();
        assertThat(done.status()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(done.folio()).isEqualTo("TCK-000001");
        assertThat(done.completedAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(done.payments()).hasSize(2);
    }

    @Test
    @DisplayName("La cancelación guarda su motivo y su fecha, y la venta conserva su folio")
    void aCancellationStoresItsReasonAndDate() {
        Sale pending = sales.save(sale("br_1", NOW, null));
        Sale done = pending.registerPayments(List.of(new Payment(PaymentMethod.CASH, new BigDecimal("101.00"), null)))
            .complete("TCK-000002", NOW.plusSeconds(30));
        sales.save(done);

        sales.save(done.cancel("El cliente se arrepintió", NOW.plusSeconds(90)));

        Sale found = sales.findById(company, "br_1", pending.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(found.cancelReason()).isEqualTo("El cliente se arrepintió");
        assertThat(found.cancelledAt()).isEqualTo(NOW.plusSeconds(90));
        assertThat(found.folio()).isEqualTo("TCK-000002");
    }

    @Test
    @DisplayName("Las ventas de una sucursal salen de la más reciente a la más antigua y no se mezclan con otras")
    void theBranchListIsNewestFirstAndIsolated() {
        Sale older = sales.save(sale("br_1", NOW, null));
        Sale newer = sales.save(sale("br_1", NOW.plusSeconds(60), null));
        sales.save(sale("br_2", NOW, null));

        assertThat(sales.findByBranch(company, "br_1")).extracting(Sale::id).containsExactly(newer.id(), older.id());
        assertThat(sales.findByBranch(company, "br_9")).isEmpty();
        assertThat(sales.findByBranch("cmp_otra_" + UUID.randomUUID(), "br_1")).isEmpty();
    }

    @Test
    @DisplayName("Una venta no se encuentra con la sucursal o la empresa de otra")
    void aSaleIsNotFoundThroughAnotherBranchOrCompany() {
        Sale sale = sales.save(sale("br_1", NOW, null));

        assertThat(sales.findById(company, "br_2", sale.id())).isEmpty();
        assertThat(sales.findById("cmp_otra", "br_1", sale.id())).isEmpty();
        assertThat(sales.findById(company, "br_1", UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("La misma referencia externa en el mismo canal no se registra dos veces")
    void theSameExternalReferenceCannotBeRegisteredTwice() {
        sales.save(sale("br_1", NOW, "ML-1001"));

        assertThatThrownBy(() -> sales.save(sale("br_1", NOW.plusSeconds(5), "ML-1001")))
            .isInstanceOf(DuplicateSaleException.class)
            .hasMessage("Esa venta ya está registrada.");
        // En otra empresa la misma referencia es otra venta.
        sales.save(Sale.create(UUID.randomUUID(), "cmp_otra_" + UUID.randomUUID(), "br_1", UUID.randomUUID(), SaleChannel.MOSTRADOR,
            "ML-1001", "MXN", List.of(line("CAFE", "1", "45.50")), "usr_1", NOW));
    }

    @Test
    @DisplayName("Las ventas de mostrador, sin referencia externa, no chocan entre sí")
    void salesWithoutAnExternalReferenceNeverClash() {
        sales.save(sale("br_1", NOW, null));
        sales.save(sale("br_1", NOW.plusSeconds(1), null));

        assertThat(sales.findByBranch(company, "br_1")).hasSize(2);
    }

    // ---- Ayudas ----

    private Sale sale(String branch, Instant at, String externalReference) {
        return Sale.create(UUID.randomUUID(), company, branch, UUID.randomUUID(), SaleChannel.MOSTRADOR, externalReference, "MXN",
            List.of(line("CAFE", "2", "45.50"), line("GALLETA", "1", "10.00")), "usr_cajera", at);
    }

    private static SaleLine line(String sku, String quantity, String price) {
        return SaleLine.of(UUID.randomUUID(), sku, "Producto " + sku, new BigDecimal(quantity), new BigDecimal(price));
    }
}
