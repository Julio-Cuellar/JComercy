package com.jcode.jcomercy.sales.infra.outbox;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.sales.application.port.SaleEventsPort;
import com.jcode.jcomercy.sales.domain.model.sale.Sale;
import com.jcode.jcomercy.sales.domain.model.sale.SaleLine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deja los avisos de ventas en {@code sales.outbox_event}. No abre su propia transacción: se une a la de quien lo llama,
 * así que el evento se guarda o se revierte junto con la venta. Solo guarda el {@code data}; el sobre
 * ({@code EventEnvelope} de jcomercy-core) lo arma el despachador con las demás columnas.
 */
@Component
public class JdbcSaleEventsAdapter implements SaleEventsPort {
    private static final String COMPLETED = "sales.sale.completed";
    private static final String CANCELLED = "sales.sale.cancelled";

    private final JdbcTemplate jdbc;
    /** Escribe los importes y cantidades como números planos: sin notación científica ni los ceros de la base de datos. */
    private final ObjectMapper json;

    public JdbcSaleEventsAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.json = mapper.copy().enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    @Override
    public void completed(Sale sale, String correlationId) {
        publish(COMPLETED, sale, sale.actorUserId(), correlationId, null);
    }

    @Override
    public void cancelled(Sale sale, String actorUserId, String correlationId) {
        publish(CANCELLED, sale, actorUserId, correlationId, sale.cancelReason());
    }

    private void publish(String eventType, Sale sale, String actorUserId, String correlationId, String reason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("saleId", sale.id().toString());
        data.put("folio", sale.folio());
        data.put("channel", sale.channel().name());
        data.put("externalReference", sale.externalReference());
        data.put("status", sale.status().name());
        data.put("warehouseId", sale.warehouseId().toString());
        data.put("currency", sale.currency());
        data.put("total", sale.total());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (SaleLine line : sale.lines()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("productId", line.productId().toString());
            entry.put("quantity", line.quantity().stripTrailingZeros());
            entry.put("unitPrice", line.unitPrice());
            entry.put("lineTotal", line.lineTotal());
            lines.add(entry);
        }
        data.put("lines", lines);
        if (reason != null) {
            data.put("reason", reason);
        }
        String payload;
        try {
            payload = json.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("No se pudo serializar el evento Outbox", e);
        }
        // Crece con el orden de registro; con huecos si una transacción se revierte.
        long sequence = jdbc.queryForObject("SELECT nextval('sales.outbox_event_sequence')", Long.class);
        jdbc.update("""
            INSERT INTO sales.outbox_event
              (id, event_id, event_type, schema_version, company_id, branch_id, actor_user_id, correlation_id, payload, sequence,
               status, created_at, retry_count)
            VALUES (?, ?, ?, '1.0', ?, ?, ?, ?, ?, ?, 'PENDING', ?, 0)
            """, UUID.randomUUID(), UUID.randomUUID(), eventType, sale.companyId(), sale.branchId(), actorUserId,
            correlationId != null ? correlationId : "cor_" + UUID.randomUUID().toString().substring(0, 8), payload, sequence,
            OffsetDateTime.now(ZoneOffset.UTC));
    }
}
