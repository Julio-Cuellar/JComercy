package com.jclinical.inventory.infra.adapters.out.persistence;

import com.jclinical.inventory.domain.model.InventoryBatch;
import com.jclinical.inventory.domain.ports.out.InventoryBatchRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SqlInventoryBatchRepository implements InventoryBatchRepositoryPort {

    private final SpringDataInventoryBatchRepository springRepository;
    private final InventoryBatchMapper mapper;

    @Override
    public InventoryBatch save(InventoryBatch batch) {
        InventoryBatchEntity entity = mapper.toEntity(batch);
        InventoryBatchEntity saved = springRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<InventoryBatch> findByIdAndClinicId(UUID batchId, UUID clinicId) {
        return springRepository.findByIdAndClinicId(batchId, clinicId).map(mapper::toDomain);
    }

    @Override
    public Optional<InventoryBatch> findByIdAndClinicIdForUpdate(UUID batchId, UUID clinicId) {
        return springRepository.findByIdAndClinicIdForUpdate(batchId, clinicId).map(mapper::toDomain);
    }

    @Override
    public List<InventoryBatch> findByMaterialIdAndClinicId(UUID materialId, UUID clinicId) {
        return springRepository.findByMaterialIdAndClinicIdOrderByExpirationAsc(materialId, clinicId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<InventoryBatch> findFirstAvailableForConsumption(UUID materialId, UUID clinicId, BigDecimal quantity) {
        return springRepository.findAvailableForConsumption(materialId, clinicId, quantity, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(mapper::toDomain);
    }

    @Override
    public List<InventoryBatch> findAvailableForConsumptionForUpdate(UUID materialId, UUID clinicId) {
        return springRepository.findAvailableForConsumptionForUpdate(materialId, clinicId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<InventoryBatch> findExpiredWithRemainingForUpdate(UUID clinicId, LocalDate asOfDate) {
        return springRepository.findExpiredWithRemainingForUpdate(clinicId, asOfDate)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}
