package com.jclinical.inventory.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMovement {
    private UUID id;
    private UUID clinicId;
    private UUID materialId;
    private MovementType type;
    private BigDecimal quantity;
    private BigDecimal presentationQuantity;
    private String presentationNameAtMovement;
    private BigDecimal quantityPerPresentationAtMovement;
    private BigDecimal unitCostAtMovement;
    private UUID batchId;
    private LocalDateTime movementDate;
    private String referenceType;
    private UUID referenceId;
    private String notes;
    private LocalDateTime createdAt;
}
