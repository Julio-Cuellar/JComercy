package com.jcode.jcomercy.sales.infra.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.core.events.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publica al broker los eventos de ventas que el Outbox tiene pendientes. Aplica lo que inventario aprendió con sus
 * errores: no retiene una transacción mientras espera la confirmación del broker (ERR-019), corta el ciclo si el broker no
 * responde para no sumar esperas ni sacar los eventos de orden (ERR-021), y deja esperar a un evento que el broker rechaza
 * sin tapar a los demás (ERR-022). Los eventos se entregan al menos una vez: quien los consuma descarta repetidos por su
 * {@code eventId}.
 */
@Component
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class SaleOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(SaleOutboxDispatcher.class);

    private static final int BATCH_SIZE = 50;
    private static final String PRODUCER_MODULE = "sales";
    // Solo se lee y se escribe JSON ya hecho (el data guardado y textos): no hace falta el ObjectMapper de Spring.
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public SaleOutboxDispatcher(JdbcTemplate jdbc, @Autowired(required = false) RabbitTemplate rabbitTemplate,
                                @Value("${outbox.rabbit.exchange:jcomercy.events}") String exchange) {
        this.jdbc = jdbc;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    // Sin @Transactional a propósito: esperar la confirmación del broker puede tardar hasta 5 s por evento, y todo el ciclo
    // en una transacción retendría una conexión de la base de datos. Cada actualización confirma por su cuenta.
    @Scheduled(initialDelayString = "${outbox.publisher.initial-delay-ms:0}",
            fixedDelayString = "${outbox.publisher.fixed-delay-ms:5000}")
    public void publishPending() {
        if (rabbitTemplate == null) {
            return;
        }
        // Los listos, en el orden en que se registraron: los que esperan su reintento no ocupan sitio.
        List<Row> events = jdbc.query("""
            SELECT id, event_id, event_type, schema_version, company_id, branch_id, actor_user_id, correlation_id, payload,
                   sequence, created_at, retry_count
            FROM sales.outbox_event
            WHERE status = 'PENDING' AND (next_retry_at IS NULL OR next_retry_at <= ?)
            ORDER BY sequence
            FETCH FIRST """ + " " + BATCH_SIZE + " ROWS ONLY", (rs, i) -> new Row(rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class), rs.getString("event_type"), rs.getString("schema_version"),
                rs.getString("company_id"), rs.getString("branch_id"), rs.getString("actor_user_id"),
                rs.getString("correlation_id"), rs.getString("payload"), rs.getLong("sequence"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getInt("retry_count")),
            OffsetDateTime.now(ZoneOffset.UTC));
        if (events.isEmpty()) {
            return;
        }

        log.info("Procesando eventos Outbox de Ventas cantidad={}", events.size());
        for (Row event : events) {
            try {
                CorrelationData correlation = new CorrelationData(event.eventId().toString());
                rabbitTemplate.convertAndSend(exchange, event.eventType(), bodyOf(event), message -> {
                    message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setMessageId(event.eventId().toString());
                    message.getMessageProperties().setHeader("eventType", event.eventType());
                    message.getMessageProperties().setHeader("schemaVersion", event.schemaVersion());
                    message.getMessageProperties().setHeader("companyId", event.companyId());
                    message.getMessageProperties().setHeader("branchId", event.branchId());
                    message.getMessageProperties().setHeader("actorUserId", event.actorUserId());
                    message.getMessageProperties().setHeader("correlationId", event.correlationId());
                    return message;
                }, correlation);

                var confirmation = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (confirmation == null || !confirmation.isAck()) {
                    throw new IllegalStateException("RabbitMQ rechazó el evento: "
                            + (confirmation == null ? "sin confirmación" : confirmation.getReason()));
                }
                jdbc.update("UPDATE sales.outbox_event SET status = 'PUBLISHED', published_at = ?, last_error = NULL, "
                        + "next_retry_at = NULL WHERE id = ?", OffsetDateTime.now(ZoneOffset.UTC), event.id());
                log.info("Evento Outbox de Ventas publicado eventId={} eventType={} exchange={}", event.eventId(), event.eventType(),
                        exchange);
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                boolean brokerUnavailable = isBrokerUnavailable(ex);
                int retries = event.retryCount() + 1;
                if (brokerUnavailable) {
                    // Sin espera propia: el ciclo ya se corta, y esperar solo lo sacaría de orden frente a los que vienen detrás.
                    jdbc.update("UPDATE sales.outbox_event SET retry_count = ?, last_error = ?, next_retry_at = NULL WHERE id = ?",
                            retries, describe(ex), event.id());
                } else {
                    // El broker rechazó el evento o no se pudo armar: espera 5 s por intento, hasta 300 s.
                    jdbc.update("UPDATE sales.outbox_event SET retry_count = ?, last_error = ?, next_retry_at = ? WHERE id = ?",
                            retries, describe(ex), OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(Math.min(300, 5L * retries)),
                            event.id());
                }
                log.error("Error publicando evento Outbox de Ventas eventId={} eventType={} retryCount={}", event.eventId(),
                        event.eventType(), retries, ex);
                if (brokerUnavailable) {
                    log.warn("El broker no responde: se corta el ciclo y los eventos restantes quedan para el siguiente");
                    return;
                }
            }
        }
    }

    /** El cuerpo del mensaje: el sobre {@code EventEnvelope} de jcomercy-core, con las columnas de la fila y el payload como {@code data}. */
    private String bodyOf(Row event) throws JsonProcessingException {
        JsonNode data = JSON.readTree(event.payload());
        EventEnvelope envelope = new EventEnvelope(event.eventId().toString(), event.eventType(), event.schemaVersion(), null,
                event.sequence(), event.createdAt(), PRODUCER_MODULE, event.correlationId(), null, null, event.companyId(),
                event.branchId(), event.actorUserId(), data);
        return JSON.writeValueAsString(envelope.toBody());
    }

    /**
     * Un broker caído o que no confirma (conexión rechazada, tiempo agotado, canal roto) afecta a todos los eventos por
     * igual; un rechazo del broker, en cambio, es solo de ese evento.
     */
    private static boolean isBrokerUnavailable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            // JsonProcessingException es una IOException, pero habla del evento y no del broker.
            if (cause instanceof JsonProcessingException) {
                return false;
            }
            if (cause instanceof AmqpException || cause instanceof TimeoutException
                    || cause instanceof IOException || cause instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }

    /** El texto que queda guardado cuando falla un evento. Una espera agotada no trae mensaje, y sin él nadie sabría por qué sigue pendiente. */
    private static String describe(Exception ex) {
        if (ex instanceof TimeoutException) {
            return "El broker no confirmó el evento en 5 segundos.";
        }
        String message = ex.getMessage();
        return message != null && !message.isBlank() ? message : ex.getClass().getName();
    }

    private record Row(UUID id, UUID eventId, String eventType, String schemaVersion, String companyId, String branchId,
                       String actorUserId, String correlationId, String payload, long sequence, Instant createdAt, int retryCount) { }
}
