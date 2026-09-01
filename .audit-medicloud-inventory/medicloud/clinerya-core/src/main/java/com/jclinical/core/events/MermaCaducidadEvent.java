package com.jclinical.core.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Momento 3 del ARE: ajuste de inventario por merma o caducidad (salida ADJUSTMENT_OUT).
 * Débito 52100 Merma y Caducidad / Crédito 12100 Almacén de Insumos Clínicos.
 */
public record MermaCaducidadEvent(
        UUID eventId,
        UUID clinicId,
        UUID materialId,
        String materialName,
        UUID movementId,
        BigDecimal quantity,
        BigDecimal unitCostAtMovement,
        LocalDateTime occurredAt
) {}
