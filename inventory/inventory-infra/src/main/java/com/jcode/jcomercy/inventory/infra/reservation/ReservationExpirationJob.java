package com.jcode.jcomercy.inventory.infra.reservation;

import com.jcode.jcomercy.inventory.application.service.ReservationApplicationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Periodically gives back the stock of reservations whose deadline has passed. */
@Component
@ConditionalOnProperty(name = "inventory.reservations.expiration.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationExpirationJob {
    private static final int BATCH_SIZE = 100;

    private final ReservationApplicationService service;

    public ReservationExpirationJob(ReservationApplicationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${inventory.reservations.expiration.fixed-delay-ms:60000}")
    public void expireDueReservations() {
        service.expireDue(Instant.now(), BATCH_SIZE);
    }
}
