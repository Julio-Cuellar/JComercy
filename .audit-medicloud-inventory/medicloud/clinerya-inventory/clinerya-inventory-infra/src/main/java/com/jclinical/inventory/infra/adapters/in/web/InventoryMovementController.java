package com.jclinical.inventory.infra.adapters.in.web;

import com.jclinical.inventory.domain.model.InventoryBatch;
import com.jclinical.inventory.domain.model.InventoryMovement;
import com.jclinical.inventory.domain.ports.in.ManageInventoryMovementUseCase;
import com.jclinical.inventory.domain.ports.in.ManageInventoryMovementUseCase.RegisterMovementCommand;
import com.jclinical.inventory.infra.adapters.in.web.dto.BatchResponse;
import com.jclinical.inventory.infra.adapters.in.web.dto.InventoryMovementResponse;
import com.jclinical.inventory.infra.adapters.in.web.dto.RegisterInventoryMovementRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/materials/{materialId}")
@RequiredArgsConstructor
public class InventoryMovementController {

    private final ManageInventoryMovementUseCase movementUseCase;

    @PostMapping("/movements")
    public ResponseEntity<InventoryMovementResponse> registerMovement(
            @PathVariable UUID clinicId,
            @PathVariable UUID materialId,
            @RequestBody RegisterInventoryMovementRequest request) {
        RegisterMovementCommand command = new RegisterMovementCommand(
                request.type(),
                request.quantity(),
                request.presentationQuantity(),
                request.movementDate(),
                request.notes(),
                request.lotNumber(),
                request.expirationDate(),
                request.batchId()
        );
        InventoryMovement movement = switch (request.type()) {
            case PURCHASE_ENTRY -> movementUseCase.registerPurchaseEntry(clinicId, materialId, command);
            case ADJUSTMENT_IN, ADJUSTMENT_OUT -> movementUseCase.registerAdjustment(clinicId, materialId, command);
            case SALE_EXIT -> movementUseCase.registerSaleExit(clinicId, materialId, command);
            case USAGE_EXIT -> throw new IllegalArgumentException("USAGE_EXIT solo puede registrarse desde una visita clínica.");
        };
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(movement));
    }

    @GetMapping("/movements")
    public ResponseEntity<List<InventoryMovementResponse>> getMovements(
            @PathVariable UUID clinicId,
            @PathVariable UUID materialId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        List<InventoryMovementResponse> responses = movementUseCase.listMovementsByMaterial(clinicId, materialId, page, size).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/batches")
    public ResponseEntity<List<BatchResponse>> getBatches(
            @PathVariable UUID clinicId,
            @PathVariable UUID materialId) {
        List<BatchResponse> responses = movementUseCase.listBatchesByMaterial(clinicId, materialId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
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

    private BatchResponse toResponse(InventoryBatch batch) {
        return new BatchResponse(
                batch.getId(),
                batch.getMaterialId(),
                batch.getLotNumber(),
                batch.getExpirationDate(),
                batch.getInitialQuantity(),
                batch.getRemainingQuantity(),
                batch.getUnitCostAtEntry(),
                batch.isDepleted(),
                batch.isExpired(),
                batch.getCreatedAt()
        );
    }
}
