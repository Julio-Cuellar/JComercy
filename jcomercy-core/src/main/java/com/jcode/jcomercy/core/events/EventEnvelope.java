package com.jcode.jcomercy.core.events;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * El sobre de todo evento de integración que sale de un módulo de JComercy ({@code EventEnvelope} en los AsyncAPI). Los
 * módulos publican en el mismo exchange; si cada uno armara su propio cuerpo, un consumidor tendría que entender una
 * forma por módulo (ERR-026). Aquí se arma una sola vez.
 *
 * <p>Siempre salen los 14 campos, en el mismo orden: lo que un módulo no conoce (por ejemplo, inventario no sabe la
 * cuenta) va como {@code null} explícito y no como campo ausente. {@code sequence} la asigna el módulo al registrar el
 * evento; {@code occurredAt} se publica como texto ISO-8601.
 */
public record EventEnvelope(
    String eventId,
    String eventType,
    String schemaVersion,
    Long aggregateVersion,
    long sequence,
    Instant occurredAt,
    String producerModule,
    String correlationId,
    String causationId,
    String accountId,
    String companyId,
    String branchId,
    String actorUserId,
    Object data
) {
    public EventEnvelope {
        requireText(eventId, "eventId");
        requireText(eventType, "eventType");
        requireText(schemaVersion, "schemaVersion");
        requireText(producerModule, "producerModule");
        requireText(correlationId, "correlationId");
        if (occurredAt == null) {
            throw new IllegalArgumentException("El evento necesita la fecha en que ocurrió (occurredAt).");
        }
        if (data == null) {
            throw new IllegalArgumentException("El evento necesita su contenido (data).");
        }
    }

    /** El cuerpo del mensaje como mapa, listo para serializar a JSON con el {@code ObjectMapper} del módulo. */
    public Map<String, Object> toBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("eventType", eventType);
        body.put("schemaVersion", schemaVersion);
        body.put("aggregateVersion", aggregateVersion);
        body.put("sequence", sequence);
        body.put("occurredAt", occurredAt.toString());
        body.put("producerModule", producerModule);
        body.put("correlationId", correlationId);
        body.put("causationId", causationId);
        body.put("accountId", accountId);
        body.put("companyId", companyId);
        body.put("branchId", branchId);
        body.put("actorUserId", actorUserId);
        body.put("data", data);
        return body;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El evento necesita " + name + ".");
        }
    }
}
