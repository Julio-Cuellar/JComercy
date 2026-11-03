package com.jcode.jcomercy.inventory.infra.persistence.adapter;

import com.jcode.jcomercy.inventory.domain.model.warehouse.Warehouse;
import com.jcode.jcomercy.inventory.domain.port.WarehouseRepositoryPort;
import com.jcode.jcomercy.inventory.infra.persistence.entity.WarehouseJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.WarehouseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public class WarehouseRepositoryAdapter implements WarehouseRepositoryPort {

    private final WarehouseJpaRepository repository;

    public WarehouseRepositoryAdapter(WarehouseJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Warehouse save(Warehouse warehouse) {
        return toDomain(repository.save(toEntity(warehouse)));
    }

    @Override
    public Optional<Warehouse> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Warehouse> findByCompanyId(String companyId) {
        return repository.findByCompanyId(companyId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Warehouse> findByBranchId(String branchId) {
        return repository.findByBranchId(branchId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Warehouse> findByIds(List<UUID> ids) {
        return ids.isEmpty() ? List.of() : repository.findByIdIn(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByCompanyIdAndCode(String companyId, String code) {
        return repository.existsByCompanyIdAndCode(companyId, code);
    }

    private WarehouseJpaEntity toEntity(Warehouse w) {
        return new WarehouseJpaEntity(w.id(), w.publicWarehouseId(), w.companyId(), w.scope(), w.branchId(),
                w.code(), w.name(), w.type(), w.status(), w.defaultWarehouse(), w.address(), w.phone(),
                w.email(), w.managerId(), w.timezone(), w.currency(), w.version());
    }

    private Warehouse toDomain(WarehouseJpaEntity w) {
        return new Warehouse(w.getId(), w.getPublicWarehouseId(), w.getCompanyId(), w.getScope(), w.getBranchId(),
                w.getCode(), w.getName(), w.getType(), w.getStatus(), w.isDefaultWarehouse(), w.getAddress(),
                w.getPhone(), w.getEmail(), w.getManagerId(), w.getTimezone(), w.getCurrency(), w.getVersion());
    }
}
