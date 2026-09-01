package com.jclinical.inventory.domain.ports.out;

import com.jclinical.inventory.domain.model.InventoryBatch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryBatchRepositoryPort {

    InventoryBatch save(InventoryBatch batch);

    Optional<InventoryBatch> findByIdAndClinicId(UUID batchId, UUID clinicId);

    Optional<InventoryBatch> findByIdAndClinicIdForUpdate(UUID batchId, UUID clinicId);

    List<InventoryBatch> findByMaterialIdAndClinicId(UUID materialId, UUID clinicId);

    Optional<InventoryBatch> findFirstAvailableForConsumption(UUID materialId, UUID clinicId, BigDecimal quantity);

    List<InventoryBatch> findAvailableForConsumptionForUpdate(UUID materialId, UUID clinicId);

    List<InventoryBatch> findExpiredWithRemainingForUpdate(UUID clinicId, LocalDate asOfDate);
}
