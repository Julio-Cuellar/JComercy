package com.jclinical.core.events;

import java.time.LocalDateTime;
import java.util.UUID;

public record AppointmentDeletedEvent(
        UUID eventId,
        UUID clinicId,
        UUID appointmentId,
        UUID doctorStaffId,
        String externalCalendarEventId,
        LocalDateTime deletedAt
) {}
