package com.jcode.jcomercy.inventory.infra.persistence.adapter;

import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseLocation;
import com.jcode.jcomercy.inventory.domain.port.WarehouseLocationRepositoryPort;
import com.jcode.jcomercy.inventory.infra.persistence.entity.WarehouseLocationJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.WarehouseLocationJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class WarehouseLocationRepositoryAdapter implements WarehouseLocationRepositoryPort {

    private final WarehouseLocationJpaRepository repository;

    public WarehouseLocationRepositoryAdapter(WarehouseLocationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public WarehouseLocation save(WarehouseLocation location) {
        return toDomain(repository.save(toEntity(location)));
    }

    @Override
    public List<WarehouseLocation> findByWarehouseId(UUID warehouseId) {
        return repository.findByWarehouseId(warehouseId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<WarehouseLocation> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Map<UUID, List<WarehouseLocation>> findByWarehouseIds(List<UUID> warehouseIds) {
        if (warehouseIds.isEmpty()) return Map.of();
        return repository.findByWarehouseIdIn(warehouseIds).stream().map(this::toDomain)
                .collect(Collectors.groupingBy(WarehouseLocation::warehouseId));
    }

    @Override
    public boolean existsByWarehouseIdAndCode(UUID warehouseId, String code) {
        return repository.existsByWarehouseIdAndCode(warehouseId, code);
    }

    private WarehouseLocationJpaEntity toEntity(WarehouseLocation l) {
        return new WarehouseLocationJpaEntity(l.id(), l.publicLocationId(), l.warehouseId(), l.parentLocationId(),
                l.code(), l.name(), l.type(), l.status(), l.capacity(), l.capacityUnitCode());
    }

    private WarehouseLocation toDomain(WarehouseLocationJpaEntity l) {
        return new WarehouseLocation(l.getId(), l.getPublicLocationId(), l.getWarehouseId(), l.getParentLocationId(),
                l.getCode(), l.getName(), l.getType(), l.getStatus(), l.getCapacity(), l.getCapacityUnitCode());
    }
}
