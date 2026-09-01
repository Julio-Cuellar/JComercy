package com.jclinical.core.events;

import java.time.LocalDateTime;
import java.util.UUID;

public record AppointmentScheduledEvent(
        UUID eventId,
        UUID clinicId,
        UUID appointmentId,
        UUID doctorStaffId,
        LocalDateTime scheduledStart,
        LocalDateTime scheduledEnd,
        LocalDateTime scheduledAt
) {}
