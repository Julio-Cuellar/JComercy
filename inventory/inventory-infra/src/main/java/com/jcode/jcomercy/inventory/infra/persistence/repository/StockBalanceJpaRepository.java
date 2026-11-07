package com.jcode.jcomercy.inventory.infra.persistence.repository;

import com.jcode.jcomercy.inventory.domain.model.stock.InventoryStatus;
import com.jcode.jcomercy.inventory.infra.persistence.entity.StockBalanceJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockBalanceJpaRepository extends JpaRepository<StockBalanceJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select balance from StockBalanceJpaEntity balance
             where balance.warehouseId = :warehouseId and balance.locationId = :locationId
               and balance.productId = :productId and balance.inventoryStatus = :inventoryStatus
               and ((:lotId is null and balance.lotId is null) or balance.lotId = :lotId)
               and ((:serialNumber is null and balance.serialNumber is null) or balance.serialNumber = :serialNumber)
            """)
    Optional<StockBalanceJpaEntity> findForUpdate(@Param("warehouseId") UUID warehouseId,
                                                   @Param("locationId") UUID locationId,
                                                   @Param("productId") UUID productId,
                                                   @Param("lotId") String lotId,
                                                   @Param("serialNumber") String serialNumber,
                                                   @Param("inventoryStatus") InventoryStatus inventoryStatus);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select balance from StockBalanceJpaEntity balance
             where balance.companyId = :companyId and balance.warehouseId = :warehouseId
               and balance.productId = :productId and balance.inventoryStatus = :inventoryStatus
             order by balance.id
            """)
    List<StockBalanceJpaEntity> findAllocatableForUpdate(@Param("companyId") String companyId,
                                                          @Param("warehouseId") UUID warehouseId,
                                                          @Param("productId") UUID productId,
                                                          @Param("inventoryStatus") InventoryStatus inventoryStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select balance from StockBalanceJpaEntity balance
             where balance.companyId = :companyId and balance.warehouseId = :warehouseId
               and balance.productId = :productId and balance.inventoryStatus = :inventoryStatus
               and balance.locationId = :locationId
             order by balance.id
            """)
    List<StockBalanceJpaEntity> findAllocatableInLocationForUpdate(@Param("companyId") String companyId,
                                                                    @Param("warehouseId") UUID warehouseId,
                                                                    @Param("productId") UUID productId,
                                                                    @Param("inventoryStatus") InventoryStatus inventoryStatus,
                                                                    @Param("locationId") UUID locationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select balance from StockBalanceJpaEntity balance where balance.id = :id")
    Optional<StockBalanceJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select balance.id from StockBalanceJpaEntity balance
             where balance.warehouseId = :warehouseId and balance.locationId = :locationId
               and balance.productId = :productId and balance.inventoryStatus = :inventoryStatus
               and ((:lotId is null and balance.lotId is null) or balance.lotId = :lotId)
               and ((:serialNumber is null and balance.serialNumber is null) or balance.serialNumber = :serialNumber)
            """)
    Optional<UUID> findIdByIdentity(@Param("warehouseId") UUID warehouseId,
                                    @Param("locationId") UUID locationId,
                                    @Param("productId") UUID productId,
                                    @Param("lotId") String lotId,
                                    @Param("serialNumber") String serialNumber,
                                    @Param("inventoryStatus") InventoryStatus inventoryStatus);

    boolean existsByCompanyIdAndSerialNumber(String companyId, String serialNumber);
    List<StockBalanceJpaEntity> findByCompanyId(String companyId);
    List<StockBalanceJpaEntity> findByCompanyIdAndProductId(String companyId, UUID productId);
}
