package com.jclinical.inventory.infra.adapters.in.web;

import com.jclinical.inventory.domain.model.InventoryMovement;
import com.jclinical.inventory.domain.ports.in.ManageInventoryMovementUseCase;
import com.jclinical.inventory.infra.adapters.in.web.dto.InventoryMovementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/inventory-batches")
@RequiredArgsConstructor
public class ExpiredBatchWasteController {

    private final ManageInventoryMovementUseCase movementUseCase;

    @PostMapping("/regularize-expired")
    public ResponseEntity<List<InventoryMovementResponse>> regularizeExpired(
            @PathVariable UUID clinicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        List<InventoryMovementResponse> response = movementUseCase.registerExpiredBatchWastes(clinicId, asOfDate)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    private InventoryMovementResponse toResponse(InventoryMovement movement) {
        return new InventoryMovementResponse(
                movement.getId(),
                movement.getClinicId(),
                movement.getMaterialId(),
                movement.getType(),
                movement.getQuantity(),
                movement.getPresentationQuantity(),
                movement.getPresentationNameAtMovement(),
                movement.getQuantityPerPresentationAtMovement(),
                movement.getUnitCostAtMovement(),
                movement.getBatchId(),
                movement.getMovementDate(),
                movement.getReferenceType(),
                movement.getReferenceId(),
                movement.getNotes(),
                movement.getCreatedAt()
        );
    }
}
