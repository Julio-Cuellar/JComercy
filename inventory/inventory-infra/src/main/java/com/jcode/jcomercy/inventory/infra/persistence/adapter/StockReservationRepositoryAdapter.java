package com.jcode.jcomercy.inventory.infra.persistence.adapter;

import com.jcode.jcomercy.inventory.domain.model.stock.ReservationStatus;
import com.jcode.jcomercy.inventory.domain.model.stock.StockReservation;
import com.jcode.jcomercy.inventory.domain.port.StockReservationRepositoryPort;
import com.jcode.jcomercy.inventory.infra.persistence.entity.StockReservationJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.StockReservationJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class StockReservationRepositoryAdapter implements StockReservationRepositoryPort {
    private final StockReservationJpaRepository repository;
    public StockReservationRepositoryAdapter(StockReservationJpaRepository repository) { this.repository = repository; }
    @Override public StockReservation save(StockReservation value) { return toDomain(repository.save(toEntity(value))); }
    @Override public List<StockReservation> findActiveByReference(String companyId, String referenceType, String referenceId) {
        return repository.findByCompanyIdAndReferenceTypeAndReferenceIdAndStatus(companyId, referenceType, referenceId,
                ReservationStatus.ACTIVE).stream().map(this::toDomain).toList();
    }
    @Override public List<UUID> findActiveBalanceIdsByReference(String companyId, String referenceType, String referenceId) {
        return repository.findBalanceIdsByReference(companyId, referenceType, referenceId, ReservationStatus.ACTIVE);
    }
    @Override public boolean existsActiveByReference(String companyId, String referenceType, String referenceId) {
        return repository.existsByCompanyIdAndReferenceTypeAndReferenceIdAndStatus(companyId, referenceType, referenceId,
                ReservationStatus.ACTIVE);
    }
    @Override public List<StockReservation> findDue(Instant now, int limit) {
        return repository.findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(ReservationStatus.ACTIVE, now,
                PageRequest.of(0, limit)).stream().map(this::toDomain).toList();
    }
    @Override public List<UUID> findDueBalanceIds(Instant now, int limit) {
        return repository.findDueBalanceIds(ReservationStatus.ACTIVE, now, PageRequest.of(0, limit));
    }
    private StockReservationJpaEntity toEntity(StockReservation value) {
        long jpaVersion = value.version() > 0 ? value.version() - 1 : 0;
        return new StockReservationJpaEntity(value.id(), value.publicReservationId(), value.companyId(), value.branchId(),
                value.warehouseId(), value.locationId(), value.productId(), value.stockBalanceId(), value.quantity(),
                value.status(), value.referenceType(), value.referenceId(), value.expiresAt(), value.actorUserId(),
                value.correlationId(), value.createdAt(), value.updatedAt(), jpaVersion);
    }
    private StockReservation toDomain(StockReservationJpaEntity value) {
        return new StockReservation(value.getId(), value.getPublicReservationId(), value.getCompanyId(), value.getBranchId(),
                value.getWarehouseId(), value.getLocationId(), value.getProductId(), value.getStockBalanceId(),
                value.getQuantity(), value.getStatus(), value.getReferenceType(), value.getReferenceId(),
                value.getExpiresAt(), value.getActorUserId(), value.getCorrelationId(), value.getCreatedAt(),
                value.getUpdatedAt(), value.getVersion());
    }
}
