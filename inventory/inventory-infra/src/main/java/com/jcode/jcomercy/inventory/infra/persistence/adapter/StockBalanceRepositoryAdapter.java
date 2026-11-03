package com.jcode.jcomercy.inventory.infra.persistence.adapter;

import com.jcode.jcomercy.inventory.domain.model.stock.InventoryStatus;
import com.jcode.jcomercy.inventory.domain.model.stock.StockBalance;
import com.jcode.jcomercy.inventory.domain.port.StockBalanceRepositoryPort;
import com.jcode.jcomercy.inventory.infra.persistence.entity.StockBalanceJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.StockBalanceJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class StockBalanceRepositoryAdapter implements StockBalanceRepositoryPort {
    private final StockBalanceJpaRepository repository;
    public StockBalanceRepositoryAdapter(StockBalanceJpaRepository repository) { this.repository = repository; }
    @Override public StockBalance save(StockBalance value) { return toDomain(repository.save(toEntity(value))); }
    @Override public Optional<StockBalance> findForUpdate(UUID warehouseId, UUID locationId, UUID productId, String lotId,
                                                          String serialNumber, InventoryStatus status) {
        return repository.findForUpdate(warehouseId, locationId, productId, lotId, serialNumber, status).map(this::toDomain);
    }
    @Override public List<StockBalance> findAllocatableForUpdate(String companyId, UUID warehouseId, UUID productId,
                                                                 InventoryStatus status, UUID locationId) {
        var entities = locationId == null
                ? repository.findAllocatableForUpdate(companyId, warehouseId, productId, status)
                : repository.findAllocatableInLocationForUpdate(companyId, warehouseId, productId, status, locationId);
        return entities.stream().map(this::toDomain).toList();
    }
    @Override public Optional<StockBalance> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(this::toDomain);
    }
    @Override public boolean existsByCompanyIdAndSerialNumber(String companyId, String serialNumber) {
        return repository.existsByCompanyIdAndSerialNumber(companyId, serialNumber);
    }
    @Override public List<StockBalance> findByCompanyId(String companyId) { return repository.findByCompanyId(companyId).stream().map(this::toDomain).toList(); }
    @Override public List<StockBalance> findByCompanyIdAndProductId(String companyId, UUID productId) {
        return repository.findByCompanyIdAndProductId(companyId, productId).stream().map(this::toDomain).toList();
    }
    private StockBalanceJpaEntity toEntity(StockBalance value) {
        long jpaVersion = value.version() > 0 ? value.version() - 1 : 0;
        return new StockBalanceJpaEntity(value.id(), value.companyId(), value.branchId(), value.warehouseId(), value.locationId(),
                value.productId(), value.lotId(), value.serialNumber(), value.expirationDate(), value.inventoryStatus(),
                value.onHand(), value.reserved(), value.unitCost(), value.costingMethod(), jpaVersion);
    }
    private StockBalance toDomain(StockBalanceJpaEntity value) {
        return new StockBalance(value.getId(), value.getCompanyId(), value.getBranchId(), value.getWarehouseId(), value.getLocationId(),
                value.getProductId(), value.getLotId(), value.getSerialNumber(), value.getExpirationDate(), value.getInventoryStatus(),
                value.getOnHand(), value.getReserved(), value.getUnitCost(), value.getCostingMethod(), value.getVersion());
    }
}
