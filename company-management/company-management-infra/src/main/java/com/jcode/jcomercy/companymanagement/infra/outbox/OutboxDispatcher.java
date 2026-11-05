package com.jcode.jcomercy.companymanagement.infra.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.companymanagement.infra.persistence.entity.OutboxEventJpaEntity;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.OutboxSpringDataRepository;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final OutboxSpringDataRepository repository; private final RabbitTemplate rabbitTemplate; private final String exchange; private final ObjectMapper objectMapper;
    public OutboxDispatcher(OutboxSpringDataRepository repository, RabbitTemplate rabbitTemplate, @Value("${outbox.rabbit.exchange:jcomercy.events}") String exchange, ObjectMapper objectMapper){this.repository=repository;this.rabbitTemplate=rabbitTemplate;this.exchange=exchange;this.objectMapper=objectMapper;}
    // Sin @Transactional a propósito (ERR-019): esperar la confirmación del broker puede tardar hasta 5 s por evento, y
    // todo el ciclo en una transacción retenía una conexión de la base de datos y no dejaba ver como publicados los
    // eventos ya confirmados hasta el final. Cada repository.save confirma por su cuenta.
    @Scheduled(fixedDelayString="${outbox.publisher.fixed-delay-ms:5000}")
    public void publishPending() {
        List<OutboxEventJpaEntity> events = repository.findTop50ByStatusAndNextRetryAtIsNullOrderByCreatedAtAsc("PENDING");
        events.addAll(repository.findTop50ByStatusAndNextRetryAtLessThanEqualOrderByCreatedAtAsc("PENDING", Instant.now()));
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
            catch (RuntimeException ex) { event.markFailed(describe(ex)); log.error("Error publicando evento eventId={} eventType={}", event.getEventId(), event.getEventType(), ex); }
            catch (Exception ex) { event.markFailed(describe(ex)); log.error("Error confirmando evento eventId={} eventType={}", event.getEventId(), event.getEventType(), ex); }
            repository.save(event);
        }
    }

    /**
     * El cuerpo del mensaje: el sobre que describe el AsyncAPI ({@code EventEnvelope}), armado con las columnas de la fila
     * y con el payload guardado como {@code data}. Hasta R4 solo se publicaba el {@code data}, sin identificador, fecha ni
     * correlación (ERR-023).
     */
    private String envelopeOf(OutboxEventJpaEntity event) throws JsonProcessingException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.getEventId());
        envelope.put("eventType", event.getEventType());
        envelope.put("schemaVersion", event.getSchemaVersion());
        envelope.put("aggregateVersion", event.getAggregateVersion());
        envelope.put("sequence", event.getSequence());
        envelope.put("occurredAt", event.getOccurredAt().toString());
        envelope.put("producerModule", event.getProducerModule());
        envelope.put("correlationId", event.getCorrelationId());
        envelope.put("causationId", event.getCausationId());
        envelope.put("accountId", event.getAccountId());
        envelope.put("companyId", event.getCompanyId());
        envelope.put("branchId", event.getBranchId());
        envelope.put("actorUserId", event.getActorUserId());
        envelope.put("data", objectMapper.readTree(event.getPayloadJson()));
        return objectMapper.writeValueAsString(envelope);
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
