package com.jcode.jcomercy.companymanagement.infra.outbox;

import com.jcode.jcomercy.companymanagement.infra.persistence.entity.OutboxEventJpaEntity;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.OutboxSpringDataRepository;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final OutboxSpringDataRepository repository; private final RabbitTemplate rabbitTemplate; private final String exchange;
    public OutboxDispatcher(OutboxSpringDataRepository repository, RabbitTemplate rabbitTemplate, @Value("${outbox.rabbit.exchange:jcomercy.events}") String exchange){this.repository=repository;this.rabbitTemplate=rabbitTemplate;this.exchange=exchange;}
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
                rabbitTemplate.convertAndSend(exchange, event.getEventType(), event.getPayloadJson(), message -> {
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

    /** El texto que queda guardado cuando falla un evento. Una espera agotada no trae mensaje, y sin él nadie sabría por qué el evento sigue pendiente. */
    private static String describe(Exception ex) {
        if (ex instanceof java.util.concurrent.TimeoutException) {
            return "El broker no confirmó el evento en 5 segundos.";
        }
        String message = ex.getMessage();
        return message != null && !message.isBlank() ? message : ex.getClass().getName();
    }
}
