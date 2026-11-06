package com.jcode.jcomercy.inventory.infra;

import com.jcode.jcomercy.inventory.domain.port.InventoryOutboxPort;
import com.jcode.jcomercy.inventory.infra.outbox.InventoryOutboxDispatcher;
import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryOutboxEventJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventoryOutboxEventSpringDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@SpringBootTest(properties = "server.port=0")
class InventoryOutboxIntegrationTest {

    @Autowired private InventoryOutboxPort outboxPort;
    @Autowired private InventoryOutboxEventSpringDataRepository outboxRepository;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
    }

    @Test
    void outboxPortPersistsEventWithCorrectEnvelope() {
        String companyId = "cmp_outbox_test";
        String branchId = "br_outbox_test";
        String actorUserId = "user_outbox_test";
        String correlationId = "cor_outbox_123";

        outboxPort.publish(
                "inventory.stock.received",
                companyId,
                branchId,
                actorUserId,
                correlationId,
                Map.of("productId", "prod-1", "quantity", 50)
        );

        List<InventoryOutboxEventJpaEntity> pending = outboxRepository.findDispatchable(java.time.Instant.now(), org.springframework.data.domain.Pageable.ofSize(50));
        assertThat(pending).hasSize(1);

        InventoryOutboxEventJpaEntity event = pending.get(0);
        assertThat(event.getEventType()).isEqualTo("inventory.stock.received");
        assertThat(event.getCompanyId()).isEqualTo(companyId);
        assertThat(event.getBranchId()).isEqualTo(branchId);
        assertThat(event.getActorUserId()).isEqualTo(actorUserId);
        assertThat(event.getCorrelationId()).isEqualTo(correlationId);
        assertThat(event.getStatus()).isEqualTo("PENDING");
        assertThat(event.getPayload()).contains("\"schemaVersion\":\"1.0\"");
        assertThat(event.getPayload()).contains("\"productId\":\"prod-1\"");
    }

    @Test
    void dispatcherPublishesPendingEventsAndUpdatesStatusToPublishedWhenRabbitMqAvailable() {
        outboxPort.publish(
                "inventory.stock.issued",
                "cmp_outbox_200",
                "br_outbox_200",
                "user_test",
                "cor_test",
                Map.of("quantity", 20)
        );

        RabbitTemplate mockRabbit = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            if (correlationData != null) {
                correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            }
            return null;
        }).when(mockRabbit).convertAndSend(anyString(), anyString(), anyString(), any(), any(CorrelationData.class));

        InventoryOutboxDispatcher dispatcher = new InventoryOutboxDispatcher(outboxRepository, mockRabbit, "jcomercy.events");
        dispatcher.publishPending();

        List<InventoryOutboxEventJpaEntity> events = outboxRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getStatus()).isEqualTo("PUBLISHED");
        assertThat(events.get(0).getPublishedAt()).isNotNull();
    }

    @Test
    void dispatcherRetainsPendingStatusAndIncrementsRetryWhenRabbitMqFails() {
        outboxPort.publish(
                "inventory.stock.adjusted",
                "cmp_outbox_300",
                "br_outbox_300",
                "user_test",
                "cor_test",
                Map.of("quantity", 5)
        );

        RabbitTemplate mockRabbit = mock(RabbitTemplate.class);
        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(mockRabbit).convertAndSend(anyString(), anyString(), anyString(), any(), any(CorrelationData.class));

        InventoryOutboxDispatcher dispatcher = new InventoryOutboxDispatcher(outboxRepository, mockRabbit, "jcomercy.events");
        dispatcher.publishPending();

        List<InventoryOutboxEventJpaEntity> events = outboxRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getStatus()).isEqualTo("PENDING");
        assertThat(events.get(0).getRetryCount()).isEqualTo(1);
        assertThat(events.get(0).getLastError()).contains("RabbitMQ connection refused");
    }
}
