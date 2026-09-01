package com.jclinical.inventory.infra.adapters.out.persistence;

import com.jclinical.inventory.domain.model.SupplierMaterial;
import com.jclinical.inventory.domain.ports.out.SupplierMaterialRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SqlSupplierMaterialRepository implements SupplierMaterialRepositoryPort {
    private final SpringDataSupplierMaterialRepository repository;

    @Override
    public SupplierMaterial save(SupplierMaterial supplierMaterial) {
        return toDomain(repository.save(toEntity(supplierMaterial)));
    }

    @Override
    public Optional<SupplierMaterial> findBySupplierIdAndMaterialId(UUID supplierId, UUID materialId) {
        return repository.findBySupplierIdAndMaterialId(supplierId, materialId).map(this::toDomain);
    }

    @Override
    public List<SupplierMaterial> findActiveBySupplierIdAndClinicId(UUID supplierId, UUID clinicId) {
        return repository.findBySupplierIdAndClinicIdAndActiveTrueOrderByMaterialNameAsc(supplierId, clinicId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private SupplierMaterialEntity toEntity(SupplierMaterial item) {
        return SupplierMaterialEntity.builder()
                .id(item.getId())
                .clinicId(item.getClinicId())
                .supplierId(item.getSupplierId())
                .materialId(item.getMaterialId())
                .materialName(item.getMaterialName())
                .unitOfMeasure(item.getUnitOfMeasure())
                .supplierUnitCost(item.getSupplierUnitCost())
                .lastSuppliedAt(item.getLastSuppliedAt())
                .receiptCount(item.getReceiptCount())
                .active(item.isActive())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private SupplierMaterial toDomain(SupplierMaterialEntity entity) {
        return SupplierMaterial.builder()
                .id(entity.getId())
                .clinicId(entity.getClinicId())
                .supplierId(entity.getSupplierId())
                .materialId(entity.getMaterialId())
                .materialName(entity.getMaterialName())
                .unitOfMeasure(entity.getUnitOfMeasure())
                .supplierUnitCost(entity.getSupplierUnitCost())
                .lastSuppliedAt(entity.getLastSuppliedAt())
                .receiptCount(entity.getReceiptCount())
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
