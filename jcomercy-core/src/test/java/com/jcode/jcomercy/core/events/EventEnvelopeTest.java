package com.jcode.jcomercy.core.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventEnvelopeTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-11-06T18:30:00.123456Z");

    private static EventEnvelope envelope(String accountId, Long aggregateVersion) {
        return new EventEnvelope("evt_1", "inventory.stock.issued", "1.0", aggregateVersion, 7L, OCCURRED_AT,
            "inventory", "cor_1", null, accountId, "cmp_1", "br_1", "usr_1", Map.of("quantity", 2));
    }

    @Test
    void theBodyHasTheContractFieldsInOrderAndKeepsUnknownValuesAsExplicitNulls() {
        Map<String, Object> body = envelope(null, null).toBody();

        assertEquals(List.of("eventId", "eventType", "schemaVersion", "aggregateVersion", "sequence", "occurredAt",
            "producerModule", "correlationId", "causationId", "accountId", "companyId", "branchId", "actorUserId", "data"),
            List.copyOf(body.keySet()));
        assertTrue(body.containsKey("accountId") && body.get("accountId") == null, "accountId desconocido va como nulo, no ausente");
        assertNull(body.get("aggregateVersion"));
        assertNull(body.get("causationId"));
    }

    @Test
    void theBodyCarriesTheValuesAndTheDateAsText() {
        Map<String, Object> body = envelope("acc_1", 3L).toBody();

        assertEquals("evt_1", body.get("eventId"));
        assertEquals("inventory", body.get("producerModule"));
        assertEquals(7L, body.get("sequence"));
        assertEquals(3L, body.get("aggregateVersion"));
        assertEquals("acc_1", body.get("accountId"));
        assertEquals("2026-11-06T18:30:00.123456Z", body.get("occurredAt"));
        assertEquals(Map.of("quantity", 2), body.get("data"));
    }

    @Test
    void anEventNeedsItsIdentityAndItsData() {
        assertThrows(IllegalArgumentException.class, () -> new EventEnvelope(" ", "t", "1.0", null, 1L, OCCURRED_AT, "m", "c", null, null, null, null, null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new EventEnvelope("e", "", "1.0", null, 1L, OCCURRED_AT, "m", "c", null, null, null, null, null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new EventEnvelope("e", "t", "1.0", null, 1L, OCCURRED_AT, "m", null, null, null, null, null, null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new EventEnvelope("e", "t", "1.0", null, 1L, null, "m", "c", null, null, null, null, null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new EventEnvelope("e", "t", "1.0", null, 1L, OCCURRED_AT, "m", "c", null, null, null, null, null, null));
    }
}
