package com.jclinical.inventory.infra.adapters.in.web;

import com.jclinical.inventory.domain.model.InventoryMovement;
import com.jclinical.inventory.domain.model.Material;
import com.jclinical.inventory.domain.ports.in.ManageInventoryMovementUseCase;
import com.jclinical.inventory.domain.ports.in.ManageMaterialUseCase;
import com.jclinical.inventory.infra.adapters.in.web.dto.GeneralLedgerEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/inventory-movements")
@RequiredArgsConstructor
public class InventoryLedgerController {

    private final ManageInventoryMovementUseCase movementUseCase;
    private final ManageMaterialUseCase materialUseCase;

    @GetMapping
    public ResponseEntity<List<GeneralLedgerEntryResponse>> getClinicLedger(
            @PathVariable UUID clinicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Map<UUID, Material> materialsById = materialUseCase.getMaterialsByClinic(clinicId, true).stream()
                .collect(java.util.stream.Collectors.toMap(Material::getId, Function.identity()));

        List<GeneralLedgerEntryResponse> responses = movementUseCase.listMovementsByClinic(clinicId, page, size).stream()
                .map(movement -> toResponse(movement, materialsById.get(movement.getMaterialId())))
                .toList();
        return ResponseEntity.ok(responses);
    }

    private GeneralLedgerEntryResponse toResponse(InventoryMovement movement, Material material) {
        return new GeneralLedgerEntryResponse(
                movement.getId(),
                movement.getMaterialId(),
                material != null ? material.getName() : "(material eliminado)",
                material != null ? material.getUnitOfMeasure() : "",
                movement.getType(),
                movement.getQuantity(),
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
