package com.jcode.jcomercy.companymanagement.infra.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.companymanagement.infra.persistence.entity.OutboxEventJpaEntity;
import com.jcode.jcomercy.core.events.EventEnvelope;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.OutboxSpringDataRepository;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int BATCH_SIZE = 50;
    private final OutboxSpringDataRepository repository; private final RabbitTemplate rabbitTemplate; private final String exchange; private final ObjectMapper objectMapper;
    public OutboxDispatcher(OutboxSpringDataRepository repository, RabbitTemplate rabbitTemplate, @Value("${outbox.rabbit.exchange:jcomercy.events}") String exchange, ObjectMapper objectMapper){this.repository=repository;this.rabbitTemplate=rabbitTemplate;this.exchange=exchange;this.objectMapper=objectMapper;}
    // Sin @Transactional a propósito (ERR-019): esperar la confirmación del broker puede tardar hasta 5 s por evento, y
    // todo el ciclo en una transacción retenía una conexión de la base de datos y no dejaba ver como publicados los
    // eventos ya confirmados hasta el final. Cada repository.save confirma por su cuenta.
    @Scheduled(initialDelayString="${outbox.publisher.initial-delay-ms:0}", fixedDelayString="${outbox.publisher.fixed-delay-ms:5000}")
    public void publishPending() {
        // Los listos, del más antiguo al más nuevo, sean nuevos o reintentos vencidos (BIT-038).
        List<OutboxEventJpaEntity> events = repository.findDispatchable(Instant.now(), Pageable.ofSize(BATCH_SIZE));
        if (!events.isEmpty()) log.info("Procesando eventos Outbox cantidad={}", events.size());
        for (OutboxEventJpaEntity event : events) {
            try {
                CorrelationData correlation = new CorrelationData(event.getEventId());
                rabbitTemplate.convertAndSend(exchange, event.getEventType(), envelopeOf(event), message -> {
                    message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setMessageId(event.getEventId());
                    message.getMessageProperties().setHeader("eventType", event.getEventType());
                    message.getMessageProperties().setHeader("schemaVersion", event.getSchemaVersion());
                    message.getMessageProperties().setHeader("accountId", event.getAccountId());
                    message.getMessageProperties().setHeader("companyId", event.getCompanyId());
                    message.getMessageProperties().setHeader("branchId", event.getBranchId());
                    return message;
                }, correlation);
                var confirmation = correlation.getFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
                if (confirmation == null || !confirmation.isAck()) throw new IllegalStateException("RabbitMQ rechazó el evento: " + (confirmation == null ? "sin confirmación" : confirmation.getReason()));
                event.markPublished();
                log.info("Evento publicado eventId={} eventType={} exchange={}", event.getEventId(), event.getEventType(), exchange);
            }
            catch (Exception ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                boolean brokerUnavailable = isBrokerUnavailable(ex);
                if (brokerUnavailable) event.markBrokerUnavailable(describe(ex)); else event.markFailed(describe(ex));
                log.error("Error publicando evento eventId={} eventType={}", event.getEventId(), event.getEventType(), ex);
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
     * igual; un rechazo del broker o un evento que no se puede armar es, en cambio, solo de ese evento.
     */
    private static boolean isBrokerUnavailable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            // JsonProcessingException es una IOException, pero habla del evento y no del broker.
            if (cause instanceof JsonProcessingException) return false;
            if (cause instanceof AmqpException || cause instanceof TimeoutException
                || cause instanceof IOException || cause instanceof InterruptedException) return true;
        }
        return false;
    }

    /**
     * El cuerpo del mensaje: el sobre que describe el AsyncAPI ({@code EventEnvelope}), armado con las columnas de la fila
     * y con el payload guardado como {@code data}. Hasta R4 solo se publicaba el {@code data}, sin identificador, fecha ni
     * correlación (ERR-023).
     */
    private String envelopeOf(OutboxEventJpaEntity event) throws JsonProcessingException {
        // El mismo armador que usa inventario (jcomercy-core): una sola forma de sobre en el exchange compartido (ERR-026).
        EventEnvelope envelope = new EventEnvelope(event.getEventId(), event.getEventType(), event.getSchemaVersion(),
            event.getAggregateVersion(), event.getSequence(), event.getOccurredAt(), event.getProducerModule(),
            event.getCorrelationId(), event.getCausationId(), event.getAccountId(), event.getCompanyId(), event.getBranchId(),
            event.getActorUserId(), objectMapper.readTree(event.getPayloadJson()));
        return objectMapper.writeValueAsString(envelope.toBody());
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
