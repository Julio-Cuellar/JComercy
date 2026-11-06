package com.jcode.jcomercy.inventory.infra.outbox;

import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryOutboxEventJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventoryOutboxEventSpringDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InventoryOutboxDispatcher.class);

    private static final int BATCH_SIZE = 50;

    private final InventoryOutboxEventSpringDataRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public InventoryOutboxDispatcher(
            InventoryOutboxEventSpringDataRepository repository,
            @Autowired(required = false) RabbitTemplate rabbitTemplate,
            @Value("${outbox.rabbit.exchange:jcomercy.events}") String exchange
    ) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    // Sin @Transactional a propósito (ERR-019): esperar la confirmación del broker puede tardar hasta 5 s por evento, y
    // todo el ciclo en una transacción retenía una conexión de la base de datos y no dejaba ver como publicados los
    // eventos ya confirmados hasta el final. Cada repository.save confirma por su cuenta.
    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:5000}")
    public void publishPending() {
        if (rabbitTemplate == null) {
            return;
        }

        // Los listos, del más antiguo al más nuevo: los que esperan su reintento no ocupan sitio (ERR-022).
        List<InventoryOutboxEventJpaEntity> events = repository.findDispatchable(Instant.now(), Pageable.ofSize(BATCH_SIZE));
        if (events.isEmpty()) {
            return;
        }

        log.info("Procesando eventos Outbox de Inventario cantidad={}", events.size());
        for (InventoryOutboxEventJpaEntity event : events) {
            try {
                CorrelationData correlation = new CorrelationData(event.getEventId().toString());
                rabbitTemplate.convertAndSend(exchange, event.getEventType(), event.getPayload(), message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setMessageId(event.getEventId().toString());
                    message.getMessageProperties().setHeader("eventType", event.getEventType());
                    message.getMessageProperties().setHeader("schemaVersion", event.getSchemaVersion());
                    message.getMessageProperties().setHeader("companyId", event.getCompanyId());
                    message.getMessageProperties().setHeader("branchId", event.getBranchId());
                    message.getMessageProperties().setHeader("actorUserId", event.getActorUserId());
                    message.getMessageProperties().setHeader("correlationId", event.getCorrelationId());
                    return message;
                }, correlation);

                var confirmation = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (confirmation == null || !confirmation.isAck()) {
                    throw new IllegalStateException("RabbitMQ rechazó el evento: " +
                            (confirmation == null ? "sin confirmación" : confirmation.getReason()));
                }

                event.markPublished();
                log.info("Evento Outbox de Inventario publicado eventId={} eventType={} exchange={}",
                        event.getEventId(), event.getEventType(), exchange);
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                boolean brokerUnavailable = isBrokerUnavailable(ex);
                if (brokerUnavailable) {
                    event.markBrokerUnavailable(describe(ex));
                } else {
                    event.markFailed(describe(ex));
                }
                log.error("Error publicando evento Outbox de Inventario eventId={} eventType={} retryCount={}",
                        event.getEventId(), event.getEventType(), event.getRetryCount(), ex);
                repository.save(event);
                if (brokerUnavailable) {
                    // Seguir con los demás solo sumaría esperas por evento y los sacaría de orden (ERR-021): se reintenta
                    // desde el primero en el siguiente ciclo.
                    log.warn("El broker no responde: se corta el ciclo y los eventos restantes quedan para el siguiente");
                    return;
                }
                continue;
            }
            repository.save(event);
        }
    }

    /**
     * Un broker caído o que no confirma (conexión rechazada, tiempo agotado, canal roto) afecta a todos los eventos por
     * igual; un rechazo del broker, en cambio, es solo de ese evento.
     */
    private static boolean isBrokerUnavailable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof AmqpException || cause instanceof TimeoutException
                    || cause instanceof IOException || cause instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }

    /** El texto que queda guardado cuando falla un evento. Una espera agotada no trae mensaje, y sin él nadie sabría por qué el evento sigue pendiente. */
    private static String describe(Exception ex) {
        if (ex instanceof java.util.concurrent.TimeoutException) {
            return "El broker no confirmó el evento en 5 segundos.";
        }
        String message = ex.getMessage();
        return message != null && !message.isBlank() ? message : ex.getClass().getName();
    }
}
