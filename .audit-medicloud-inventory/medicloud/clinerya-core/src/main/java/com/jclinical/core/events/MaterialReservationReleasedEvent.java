package com.jclinical.core.events;

import java.time.LocalDateTime;
import java.util.UUID;

public record MaterialReservationReleasedEvent(
        UUID eventId,
        UUID clinicId,
        UUID appointmentId,
        LocalDateTime releasedAt
) {}
