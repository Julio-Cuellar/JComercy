package com.jclinical.inventory.infra.adapters.out.persistence;

import com.jclinical.inventory.domain.model.InventoryMovement;
import com.jclinical.inventory.domain.model.MovementType;
import com.jclinical.inventory.domain.ports.out.InventoryMovementRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SqlInventoryMovementRepository implements InventoryMovementRepositoryPort {

    private final SpringDataInventoryMovementRepository springRepository;
    private final InventoryMovementMapper mapper;

    @Override
    public InventoryMovement save(InventoryMovement movement) {
        InventoryMovementEntity entity = mapper.toEntity(movement);
        InventoryMovementEntity saved = springRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<InventoryMovement> findByMaterialIdAndClinicId(UUID materialId, UUID clinicId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "movementDate", "createdAt"));
        return springRepository.findByMaterialIdAndClinicIdOrderByMovementDateDescCreatedAtDesc(materialId, clinicId, pageRequest)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<InventoryMovement> findByClinicId(UUID clinicId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "movementDate", "createdAt"));
        return springRepository.findByClinicIdOrderByMovementDateDescCreatedAtDesc(clinicId, pageRequest)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<InventoryMovement> findByReferenceAndMaterial(
            MovementType type,
            String referenceType,
            UUID referenceId,
            UUID materialId) {
        return springRepository
                .findByTypeAndReferenceTypeAndReferenceIdAndMaterialId(type, referenceType, referenceId, materialId)
                .map(mapper::toDomain);
    }
}
