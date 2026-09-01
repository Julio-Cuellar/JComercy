package com.jclinical.inventory.infra.adapters.in.web;

import com.jclinical.inventory.domain.model.Material;
import com.jclinical.inventory.domain.ports.in.ManageMaterialUseCase;
import com.jclinical.inventory.domain.ports.in.ManageMaterialUseCase.CreateMaterialCommand;
import com.jclinical.inventory.domain.ports.in.ManageMaterialUseCase.UpdateMaterialCommand;
import com.jclinical.inventory.infra.adapters.in.web.dto.CreateMaterialRequest;
import com.jclinical.inventory.infra.adapters.in.web.dto.MaterialResponse;
import com.jclinical.inventory.infra.adapters.in.web.dto.UpdateMaterialRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final ManageMaterialUseCase materialUseCase;

    @PostMapping
    public ResponseEntity<MaterialResponse> createMaterial(
            @PathVariable UUID clinicId,
            @RequestBody CreateMaterialRequest request) {
        CreateMaterialCommand command = new CreateMaterialCommand(
                request.name(),
                request.category(),
                request.internalCode(),
                request.brand(),
                request.description(),
                request.unitOfMeasure(),
                request.presentationName(),
                request.quantityPerPresentation(),
                request.unitCost(),
                request.minimumStock(),
                request.saleEnabled(),
                request.salePrice(),
                request.tracksBatches()
        );
        Material material = materialUseCase.createMaterial(clinicId, command);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(material));
    }

    @GetMapping
    public ResponseEntity<List<MaterialResponse>> getMaterials(
            @PathVariable UUID clinicId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        List<MaterialResponse> responses = materialUseCase.getMaterialsByClinic(clinicId, includeInactive).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{materialId}")
    public ResponseEntity<MaterialResponse> getMaterial(
            @PathVariable UUID clinicId,
            @PathVariable UUID materialId) {
        return materialUseCase.getMaterial(materialId, clinicId)
                .map(material -> ResponseEntity.ok(toResponse(material)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{materialId}")
    public ResponseEntity<MaterialResponse> updateMaterial(
            @PathVariable UUID clinicId,
            @PathVariable UUID materialId,
            @RequestBody UpdateMaterialRequest request) {
        UpdateMaterialCommand command = new UpdateMaterialCommand(
                request.name(),
                request.category(),
                request.internalCode(),
                request.brand(),
                request.description(),
                request.unitOfMeasure(),
                request.presentationName(),
                request.quantityPerPresentation(),
                request.unitCost(),
                request.minimumStock(),
                request.saleEnabled(),
                request.salePrice(),
                request.tracksBatches(),
                request.active()
        );
        Material material = materialUseCase.updateMaterial(materialId, clinicId, command);
        return ResponseEntity.ok(toResponse(material));
    }

    @DeleteMapping("/{materialId}")
    public ResponseEntity<Void> deactivateMaterial(
            @PathVariable UUID clinicId,
            @PathVariable UUID materialId) {
        materialUseCase.deactivateMaterial(materialId, clinicId);
        return ResponseEntity.noContent().build();
    }

    private MaterialResponse toResponse(Material material) {
        return new MaterialResponse(
                material.getId(),
                material.getClinicId(),
                material.getName(),
                material.getCategory(),
                material.getInternalCode(),
                material.getBrand(),
                material.getDescription(),
                material.getUnitOfMeasure(),
                material.getPresentationName(),
                material.getQuantityPerPresentation(),
                material.getUnitCost(),
                material.getCurrentStock(),
                material.getReservedQuantity(),
                material.availableQuantity(),
                material.getMinimumStock(),
                material.isBelowMinimumStock(),
                material.isSaleEnabled(),
                material.getSalePrice(),
                material.isTracksBatches(),
                material.isActive(),
                material.getCreatedAt(),
                material.getUpdatedAt()
        );
    }
}
