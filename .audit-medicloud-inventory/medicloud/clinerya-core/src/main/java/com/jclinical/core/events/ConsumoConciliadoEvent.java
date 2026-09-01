package com.jclinical.core.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ConsumoConciliadoEvent(
        UUID eventId,
        UUID clinicId,
        UUID patientId,
        UUID visitId,
        UUID quotationId,
        UUID quotationItemId,
        LocalDateTime occurredAt,
        List<MaterialLine> materials
) {

    public record MaterialLine(
            UUID materialId,
            String materialName,
            BigDecimal actualQuantity,
            BigDecimal unitCostAtMovement
    ) {}
}
