package com.jcode.jcomercy.inventory.infra.persistence.repository;

import com.jcode.jcomercy.inventory.infra.persistence.entity.WarehouseJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface WarehouseJpaRepository extends JpaRepository<WarehouseJpaEntity, UUID> {

    Optional<WarehouseJpaEntity> findByCompanyIdAndCode(String companyId, String code);

    boolean existsByCompanyIdAndCode(String companyId, String code);

    List<WarehouseJpaEntity> findByCompanyId(String companyId);

    List<WarehouseJpaEntity> findByCompanyIdAndBranchId(String companyId, String branchId);

    List<WarehouseJpaEntity> findByBranchId(String branchId);

    List<WarehouseJpaEntity> findByIdIn(List<UUID> ids);
}
