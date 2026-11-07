package com.jcode.jcomercy.inventory.infra.persistence.adapter;

import com.jcode.jcomercy.inventory.domain.model.stock.InventoryMovement;
import com.jcode.jcomercy.inventory.domain.port.InventoryMovementRepositoryPort;
import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryMovementJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventoryMovementJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class InventoryMovementRepositoryAdapter implements InventoryMovementRepositoryPort {
    private final InventoryMovementJpaRepository repository;
    public InventoryMovementRepositoryAdapter(InventoryMovementJpaRepository repository) { this.repository = repository; }
    @Override public InventoryMovement save(InventoryMovement value) { return toDomain(repository.save(toEntity(value))); }
    @Override public List<InventoryMovement> findByCompanyId(String companyId) { return repository.findByCompanyIdOrderByOccurredAtDesc(companyId).stream().map(this::toDomain).toList(); }
    @Override public List<InventoryMovement> findByCompanyIdAndProductId(String companyId, UUID productId) { return repository.findByCompanyIdAndProductIdOrderByOccurredAtDesc(companyId, productId).stream().map(this::toDomain).toList(); }
    @Override public List<InventoryMovement> findByCompanyIdAndReference(String companyId, String referenceType, String referenceId) {
        return repository.findByCompanyIdAndReferenceTypeAndReferenceIdOrderByOccurredAtAsc(companyId, referenceType, referenceId)
                .stream().map(this::toDomain).toList();
    }
    private InventoryMovementJpaEntity toEntity(InventoryMovement value) {
        return new InventoryMovementJpaEntity(value.id(), value.publicMovementId(), value.companyId(), value.branchId(),
                value.warehouseId(), value.locationId(), value.productId(), value.type(), value.quantity(), value.unitCode(),
                value.unitCost(), value.totalCost(), value.lotId(), value.serialNumber(), value.expirationDate(),
                value.referenceType(), value.referenceId(), value.reason(), value.occurredAt(), value.actorUserId(),
                value.correlationId(), value.createdAt());
    }
    private InventoryMovement toDomain(InventoryMovementJpaEntity value) {
        return new InventoryMovement(value.getId(), value.getPublicMovementId(), value.getCompanyId(), value.getBranchId(),
                value.getWarehouseId(), value.getLocationId(), value.getProductId(), value.getType(), value.getQuantity(),
                value.getUnitCode(), value.getUnitCost(), value.getTotalCost(), value.getLotId(), value.getSerialNumber(),
                value.getExpirationDate(), value.getReferenceType(), value.getReferenceId(), value.getReason(),
                value.getOccurredAt(), value.getActorUserId(), value.getCorrelationId(), value.getCreatedAt());
    }
}
