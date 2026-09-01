package com.jclinical.core.events;

import java.time.LocalDateTime;
import java.util.UUID;

public record AppointmentRescheduledEvent(
        UUID eventId,
        UUID clinicId,
        UUID appointmentId,
        LocalDateTime newStart,
        LocalDateTime newEnd,
        LocalDateTime rescheduledAt
) {}
