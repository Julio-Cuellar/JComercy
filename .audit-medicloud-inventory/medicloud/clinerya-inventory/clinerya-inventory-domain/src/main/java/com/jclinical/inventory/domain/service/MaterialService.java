package com.jclinical.inventory.domain.service;

import com.jclinical.inventory.domain.model.Material;
import com.jclinical.inventory.domain.ports.in.ManageMaterialUseCase;
import com.jclinical.inventory.domain.ports.out.MaterialRepositoryPort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MaterialService implements ManageMaterialUseCase {

    private final MaterialRepositoryPort materialRepository;

    public MaterialService(MaterialRepositoryPort materialRepository) {
        this.materialRepository = materialRepository;
    }

    @Override
    public Material createMaterial(UUID clinicId, CreateMaterialCommand command) {
        validate(command.name(), command.unitOfMeasure(), command.quantityPerPresentation(), command.unitCost());
        validateMinimumStock(command.minimumStock());
        validateSalePrice(command.saleEnabled(), command.salePrice());

        Material material = Material.builder()
                .id(UUID.randomUUID())
                .clinicId(clinicId)
                .name(command.name().trim())
                .category(normalizeOptional(command.category()))
                .internalCode(normalizeOptional(command.internalCode()))
                .brand(normalizeOptional(command.brand()))
                .description(normalizeOptional(command.description()))
                .unitOfMeasure(command.unitOfMeasure().trim())
                .presentationName(normalizeOptional(command.presentationName()))
                .quantityPerPresentation(command.quantityPerPresentation())
                .unitCost(command.unitCost())
                .currentStock(BigDecimal.ZERO)
                .minimumStock(command.minimumStock())
                .saleEnabled(command.saleEnabled())
                .salePrice(command.saleEnabled() ? command.salePrice() : null)
                .tracksBatches(command.tracksBatches())
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return materialRepository.save(material);
    }

    @Override
    public Material updateMaterial(UUID materialId, UUID clinicId, UpdateMaterialCommand command) {
        validate(command.name(), command.unitOfMeasure(), command.quantityPerPresentation(), command.unitCost());
        validateMinimumStock(command.minimumStock());
        validateSalePrice(command.saleEnabled(), command.salePrice());

        Material material = materialRepository.findByIdAndClinicId(materialId, clinicId)
                .orElseThrow(() -> new IllegalArgumentException("El material no existe en esta clínica."));

        material.setName(command.name().trim());
        material.setCategory(normalizeOptional(command.category()));
        material.setInternalCode(normalizeOptional(command.internalCode()));
        material.setBrand(normalizeOptional(command.brand()));
        material.setDescription(normalizeOptional(command.description()));
        material.setUnitOfMeasure(command.unitOfMeasure().trim());
        material.setPresentationName(normalizeOptional(command.presentationName()));
        material.setQuantityPerPresentation(command.quantityPerPresentation());
        material.setUnitCost(command.unitCost());
        material.setMinimumStock(command.minimumStock());
        material.setSaleEnabled(command.saleEnabled());
        material.setSalePrice(command.saleEnabled() ? command.salePrice() : null);
        material.setTracksBatches(command.tracksBatches());
        material.setActive(command.active());
        material.setUpdatedAt(LocalDateTime.now());

        return materialRepository.save(material);
    }

    @Override
    public void deactivateMaterial(UUID materialId, UUID clinicId) {
        Material material = materialRepository.findByIdAndClinicId(materialId, clinicId)
                .orElseThrow(() -> new IllegalArgumentException("El material no existe en esta clínica."));
        material.setActive(false);
        material.setUpdatedAt(LocalDateTime.now());
        materialRepository.save(material);
    }

    @Override
    public Optional<Material> getMaterial(UUID materialId, UUID clinicId) {
        return materialRepository.findByIdAndClinicId(materialId, clinicId);
    }

    @Override
    public List<Material> getMaterialsByClinic(UUID clinicId, boolean includeInactive) {
        return materialRepository.findByClinicId(clinicId, includeInactive);
    }

    private void validate(String name, String unitOfMeasure, BigDecimal quantityPerPresentation, BigDecimal unitCost) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del material es obligatorio.");
        }
        if (unitOfMeasure == null || unitOfMeasure.isBlank()) {
            throw new IllegalArgumentException("La unidad de medida es obligatoria.");
        }
        if (quantityPerPresentation != null && quantityPerPresentation.signum() <= 0) {
            throw new IllegalArgumentException("El contenido por presentaciÃ³n debe ser mayor a cero.");
        }
        if (unitCost == null || unitCost.signum() < 0) {
            throw new IllegalArgumentException("El costo unitario debe ser mayor o igual a cero.");
        }
    }

    private void validateMinimumStock(BigDecimal minimumStock) {
        if (minimumStock != null && minimumStock.signum() < 0) {
            throw new IllegalArgumentException("El stock mínimo no puede ser negativo.");
        }
    }

    private void validateSalePrice(boolean saleEnabled, BigDecimal salePrice) {
        if (saleEnabled && (salePrice == null || salePrice.signum() < 0)) {
            throw new IllegalArgumentException("Si el material está habilitado para venta, el precio de venta es obligatorio y debe ser mayor o igual a cero.");
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
