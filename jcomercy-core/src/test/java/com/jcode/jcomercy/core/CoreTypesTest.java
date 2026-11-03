package com.jcode.jcomercy.core;

import com.jcode.jcomercy.core.context.RequestContext;
import com.jcode.jcomercy.core.events.DomainEvent;
import com.jcode.jcomercy.core.identifiers.CorrelationId;
import com.jcode.jcomercy.core.identifiers.EventId;
import com.jcode.jcomercy.core.identifiers.PublicUserId;
import com.jcode.jcomercy.core.pagination.PageRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoreTypesTest {

    @Test
    void createsUserRequestContextWithCorrelationId() {
        var context = RequestContext.forUser(PublicUserId.newId());

        assertEquals(Optional.empty(), context.publicCompanyId());
        assertEquals(32, context.correlationId().value().toString().replace("-", "").length());
    }

    @Test
    void rejectsInvalidEventVersion() {
        assertThrows(IllegalArgumentException.class, () -> new DomainEvent<>(
                new EventId(UUID.randomUUID()),
                "CompanyCreated",
                0,
                Instant.now(),
                "company-management",
                CorrelationId.newId(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "payload"));
    }

    @Test
    void createsFirstPageRequest() {
        var request = PageRequest.firstPage(25);

        assertEquals(0, request.page());
        assertEquals(25, request.size());
    }
}
