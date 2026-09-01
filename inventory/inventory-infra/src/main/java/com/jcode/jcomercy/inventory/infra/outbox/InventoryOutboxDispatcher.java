package com.jcode.jcomercy.inventory.infra.outbox;

import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryOutboxEventJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventoryOutboxEventSpringDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class InventoryOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InventoryOutboxDispatcher.class);

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

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:5000}")
    @Transactional
    public void publishPending() {
        if (rabbitTemplate == null) {
            return;
        }

        List<InventoryOutboxEventJpaEntity> events = repository.findTop50ByStatusOrderByCreatedAtAsc("PENDING");
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
                event.markFailed(ex.getMessage());
                log.error("Error publicando evento Outbox de Inventario eventId={} eventType={} retryCount={}",
                        event.getEventId(), event.getEventType(), event.getRetryCount(), ex);
            }
            repository.save(event);
        }
    }
}
