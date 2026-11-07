package com.jcode.jcomercy.sales.infra.persistence.adapter;

import com.jcode.jcomercy.sales.domain.exception.DuplicateSaleException;
import com.jcode.jcomercy.sales.domain.model.sale.Payment;
import com.jcode.jcomercy.sales.domain.model.sale.PaymentMethod;
import com.jcode.jcomercy.sales.domain.model.sale.Sale;
import com.jcode.jcomercy.sales.domain.model.sale.SaleChannel;
import com.jcode.jcomercy.sales.domain.model.sale.SaleLine;
import com.jcode.jcomercy.sales.domain.model.sale.SaleStatus;
import com.jcode.jcomercy.sales.domain.port.SaleRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Las ventas en {@code sales.sale}, con sus líneas y sus pagos. Las líneas se guardan una sola vez, al crear la venta;
 * los pagos, el folio y la cancelación se actualizan al avanzar. Los índices únicos de la tabla son la última defensa
 * contra registrar dos veces la misma venta de un canal externo.
 */
@Component
public class JdbcSaleRepositoryAdapter implements SaleRepositoryPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcSaleRepositoryAdapter.class);
    /** Las ventas más recientes que devuelve la lista de una sucursal; la paginación llega cuando haga falta. */
    private static final int LIST_LIMIT = 200;
    private static final String COLUMNS = """
        id, company_id, branch_id, warehouse_id, channel, external_reference, status, currency, folio, total, received,
        change_amount, actor_user_id, created_at, completed_at, cancelled_at, cancel_reason
        """;

    private final JdbcTemplate jdbc;
    private final RowMapper<Header> headerMapper = this::toHeader;

    public JdbcSaleRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Sale save(Sale sale) {
        try {
            int updated = jdbc.update("""
                UPDATE sales.sale
                SET status = ?, folio = ?, received = ?, change_amount = ?, completed_at = ?, cancelled_at = ?,
                    cancel_reason = ?, version = version + 1
                WHERE id = ?
                """, sale.status().name(), sale.folio(), sale.received(), sale.change(), at(sale.completedAt()),
                at(sale.cancelledAt()), sale.cancelReason(), sale.id());
            if (updated == 0) {
                insert(sale);
            }
            jdbc.update("DELETE FROM sales.sale_payment WHERE sale_id = ?", sale.id());
            int number = 0;
            for (Payment payment : sale.payments()) {
                jdbc.update("INSERT INTO sales.sale_payment (sale_id, payment_number, method, amount, reference) VALUES (?, ?, ?, ?, ?)",
                    sale.id(), ++number, payment.method().name(), payment.amount(), payment.reference());
            }
        } catch (DuplicateKeyException e) {
            // No se registra el mensaje de la base de datos: cita el valor repetido, que puede ser un dato del cliente.
            log.warn("Venta rechazada por estar ya registrada companyId={} branchId={} channel={}", sale.companyId(),
                sale.branchId(), sale.channel());
            throw new DuplicateSaleException();
        }
        return sale;
    }

    @Override
    public Optional<Sale> findById(String companyId, String branchId, UUID id) {
        List<Header> headers = jdbc.query("SELECT " + COLUMNS + " FROM sales.sale WHERE company_id = ? AND branch_id = ? AND id = ?",
            headerMapper, companyId, branchId, id);
        return assemble(headers).stream().findFirst();
    }

    @Override
    public List<Sale> findByBranch(String companyId, String branchId) {
        List<Header> headers = jdbc.query("SELECT " + COLUMNS + " FROM sales.sale WHERE company_id = ? AND branch_id = ? "
            + "ORDER BY created_at DESC, id FETCH FIRST " + LIST_LIMIT + " ROWS ONLY", headerMapper, companyId, branchId);
        return assemble(headers);
    }

    private void insert(Sale sale) {
        jdbc.update("""
            INSERT INTO sales.sale
              (id, company_id, branch_id, warehouse_id, channel, external_reference, status, currency, folio, total, received,
               change_amount, actor_user_id, created_at, completed_at, cancelled_at, cancel_reason, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """, sale.id(), sale.companyId(), sale.branchId(), sale.warehouseId(), sale.channel().name(), sale.externalReference(),
            sale.status().name(), sale.currency(), sale.folio(), sale.total(), sale.received(), sale.change(), sale.actorUserId(),
            at(sale.createdAt()), at(sale.completedAt()), at(sale.cancelledAt()), sale.cancelReason());
        for (SaleLine line : sale.lines()) {
            jdbc.update("""
                INSERT INTO sales.sale_line (sale_id, line_number, product_id, sku, name, quantity, unit_price, line_total)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, sale.id(), line.lineNumber(), line.productId(), line.sku(), line.name(), line.quantity(), line.unitPrice(),
                line.lineTotal());
        }
    }

    /** Une cada venta con sus líneas y sus pagos, que se leen de una vez para todas. */
    private List<Sale> assemble(List<Header> headers) {
        if (headers.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = headers.stream().map(Header::id).toList();
        Map<UUID, List<SaleLine>> lines = linesOf(ids);
        Map<UUID, List<Payment>> payments = paymentsOf(ids);
        List<Sale> sales = new ArrayList<>();
        for (Header h : headers) {
            sales.add(new Sale(h.id(), h.companyId(), h.branchId(), h.warehouseId(), h.channel(), h.externalReference(), h.status(),
                h.currency(), lines.getOrDefault(h.id(), List.of()), payments.getOrDefault(h.id(), List.of()), h.folio(), h.total(),
                h.received(), h.change(), h.actorUserId(), h.createdAt(), h.completedAt(), h.cancelledAt(), h.cancelReason()));
        }
        return sales;
    }

    private Map<UUID, List<SaleLine>> linesOf(Collection<UUID> ids) {
        Map<UUID, List<SaleLine>> byId = new LinkedHashMap<>();
        jdbc.query("SELECT sale_id, line_number, product_id, sku, name, quantity, unit_price FROM sales.sale_line WHERE sale_id IN ("
                + placeholders(ids.size()) + ") ORDER BY sale_id, line_number",
            (ResultSet rs) -> {
                byId.computeIfAbsent(rs.getObject("sale_id", UUID.class), k -> new ArrayList<>()).add(new SaleLine(
                    rs.getInt("line_number"), rs.getObject("product_id", UUID.class), rs.getString("sku"), rs.getString("name"),
                    rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price")));
            }, ids.toArray());
        return byId;
    }

    private Map<UUID, List<Payment>> paymentsOf(Collection<UUID> ids) {
        Map<UUID, List<Payment>> byId = new LinkedHashMap<>();
        jdbc.query("SELECT sale_id, method, amount, reference FROM sales.sale_payment WHERE sale_id IN ("
                + placeholders(ids.size()) + ") ORDER BY sale_id, payment_number",
            (ResultSet rs) -> {
                byId.computeIfAbsent(rs.getObject("sale_id", UUID.class), k -> new ArrayList<>()).add(new Payment(
                    PaymentMethod.valueOf(rs.getString("method")), rs.getBigDecimal("amount"), rs.getString("reference")));
            }, ids.toArray());
        return byId;
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    private Header toHeader(ResultSet rs, int row) throws SQLException {
        return new Header(rs.getObject("id", UUID.class), rs.getString("company_id"), rs.getString("branch_id"),
            rs.getObject("warehouse_id", UUID.class), SaleChannel.valueOf(rs.getString("channel")),
            rs.getString("external_reference"), SaleStatus.valueOf(rs.getString("status")), rs.getString("currency"),
            rs.getString("folio"), rs.getBigDecimal("total"), rs.getBigDecimal("received"), rs.getBigDecimal("change_amount"),
            rs.getString("actor_user_id"), instant(rs, "created_at"), instant(rs, "completed_at"), instant(rs, "cancelled_at"),
            rs.getString("cancel_reason"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime at(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** Los datos de la fila de la venta, antes de unirles sus líneas y pagos. */
    private record Header(UUID id, String companyId, String branchId, UUID warehouseId, SaleChannel channel,
                          String externalReference, SaleStatus status, String currency, String folio, BigDecimal total,
                          BigDecimal received, BigDecimal change, String actorUserId, Instant createdAt, Instant completedAt,
                          Instant cancelledAt, String cancelReason) { }
}
