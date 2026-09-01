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
public class MaterialReservation {
    private UUID id;
    private UUID clinicId;
    private UUID appointmentId;
    private UUID materialId;
    private String materialName;
    private BigDecimal quantity;
    private MaterialReservationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime releasedAt;

    public void release() {
        if (status != MaterialReservationStatus.RESERVED) {
            return;
        }
        this.status = MaterialReservationStatus.RELEASED;
        this.releasedAt = LocalDateTime.now();
    }
}
