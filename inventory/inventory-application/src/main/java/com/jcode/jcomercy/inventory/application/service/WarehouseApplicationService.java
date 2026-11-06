package com.jcode.jcomercy.inventory.application.service;

import com.jcode.jcomercy.inventory.application.exception.InventoryApplicationException;
import com.jcode.jcomercy.inventory.domain.model.warehouse.Warehouse;
import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseBranchAccess;
import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseLocation;
import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseScope;
import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseStatus;
import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseType;
import com.jcode.jcomercy.inventory.domain.port.WarehouseBranchAccessRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.WarehouseLocationRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.WarehouseRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.UnitOfMeasureRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.CompanyUnitOfMeasureRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WarehouseApplicationService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseApplicationService.class);

    private final WarehouseRepositoryPort warehouseRepository;
    private final WarehouseBranchAccessRepositoryPort accessRepository;
    private final WarehouseLocationRepositoryPort locationRepository;
    private final UnitOfMeasureRepositoryPort units;
    private final CompanyUnitOfMeasureRepositoryPort companyUnits;
    private final com.jcode.jcomercy.inventory.domain.port.InventoryOutboxPort outboxPort;

    public WarehouseApplicationService(WarehouseRepositoryPort warehouseRepository,
                                       WarehouseBranchAccessRepositoryPort accessRepository,
                                       WarehouseLocationRepositoryPort locationRepository) {
        this(warehouseRepository, accessRepository, locationRepository, null, null, null);
    }

    public WarehouseApplicationService(WarehouseRepositoryPort warehouseRepository,
                                       WarehouseBranchAccessRepositoryPort accessRepository,
                                       WarehouseLocationRepositoryPort locationRepository,
                                       UnitOfMeasureRepositoryPort units,
                                       CompanyUnitOfMeasureRepositoryPort companyUnits) {
        this(warehouseRepository, accessRepository, locationRepository, units, companyUnits, null);
    }

    public WarehouseApplicationService(WarehouseRepositoryPort warehouseRepository,
                                       WarehouseBranchAccessRepositoryPort accessRepository,
                                       WarehouseLocationRepositoryPort locationRepository,
                                       UnitOfMeasureRepositoryPort units,
                                       CompanyUnitOfMeasureRepositoryPort companyUnits,
                                       com.jcode.jcomercy.inventory.domain.port.InventoryOutboxPort outboxPort) {
        this.warehouseRepository = warehouseRepository;
        this.accessRepository = accessRepository;
        this.locationRepository = locationRepository;
        this.units = units;
        this.companyUnits = companyUnits;
        this.outboxPort = outboxPort;
    }

    @Transactional
    public Warehouse createWarehouse(CreateWarehouseCommand command) {
        log.info("Iniciando creación de almacén companyId={} scope={} branchId={} code={}", command.companyId(),
                command.scope(), command.branchId(), command.code());
        if (warehouseRepository.existsByCompanyIdAndCode(command.companyId(), command.code())) {
            log.warn("Creación de almacén rechazada por código duplicado companyId={} code={}", command.companyId(),
                    command.code());
            throw new InventoryApplicationException("warehouse code already exists for company");
        }
        if (command.defaultWarehouse() && command.scope() == WarehouseScope.BRANCH_OWNED && command.branchId() != null) {
            boolean hasDefault = warehouseRepository.findByBranchId(command.branchId()).stream().anyMatch(Warehouse::defaultWarehouse);
            if (hasDefault) {
                throw new InventoryApplicationException("branch already has a default warehouse");
            }
        }
        var warehouse = new Warehouse(UUID.randomUUID(), UUID.randomUUID(), command.companyId(), command.scope(),
                command.branchId(), command.code(), command.name(), command.type(), WarehouseStatus.DRAFT,
                command.defaultWarehouse(), command.address(), command.phone(), command.email(), command.managerId(),
                command.timezone(), command.currency(), 0);
        var saved = warehouseRepository.save(warehouse);
        log.info("Almacén persistido id={} publicWarehouseId={} companyId={} branchId={}", saved.id(),
                saved.publicWarehouseId(), saved.companyId(), saved.branchId());
        if (outboxPort != null) {
            outboxPort.publish("inventory.warehouse.created", saved.companyId(), saved.branchId(), command.actorUserId(), null,
                    java.util.Map.of("warehouseId", saved.id().toString(), "publicWarehouseId", saved.publicWarehouseId(), "code", saved.code(), "name", saved.name()));
        }
        return saved;
    }

    @Transactional
    public Warehouse createBranchWarehouse(String companyId, String branchId, CreateBranchWarehouseCommand command) {
        return createWarehouse(new CreateWarehouseCommand(companyId, WarehouseScope.BRANCH_OWNED, branchId,
                command.code(), command.name(), command.type(), command.defaultWarehouse(), command.address(),
                command.phone(), command.email(), command.managerId(), command.timezone(), command.currency(),
                command.actorUserId()));
    }

    @Transactional(readOnly = true)
    public List<Warehouse> listByCompany(String companyId) {
        var result = warehouseRepository.findByCompanyId(companyId);
        log.info("Almacenes consultados por empresa companyId={} total={}", companyId, result.size());
        return result;
    }

    @Transactional(readOnly = true)
    public List<Warehouse> listByBranch(String branchId) {
        var warehouses = new java.util.LinkedHashMap<UUID, Warehouse>();
        warehouseRepository.findByBranchId(branchId).forEach(warehouse -> warehouses.put(warehouse.id(), warehouse));
        warehouseRepository.findByIds(accessRepository.findWarehouseIdsByBranchId(branchId))
                .forEach(warehouse -> warehouses.put(warehouse.id(), warehouse));
        var result = List.copyOf(warehouses.values());
        log.info("Almacenes consultados por sucursal branchId={} total={}", branchId, result.size());
        return result;
    }

    @Transactional
    public Warehouse activate(UUID warehouseId, String actorUserId) {
        var warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> InventoryApplicationException.notFound("warehouse not found"));
        if (warehouse.scope() == WarehouseScope.COMPANY_SHARED) {
            if (!accessRepository.existsActiveByWarehouseId(warehouseId)) {
                throw new InventoryApplicationException("company-shared warehouse must have at least one authorized branch before activation");
            }
        }
        var activated = warehouseRepository.save(warehouse.activate());
        log.info("Almacén activado warehouseId={} companyId={}", activated.id(), activated.companyId());
        if (outboxPort != null) {
            outboxPort.publish("inventory.warehouse.activated", activated.companyId(), activated.branchId(), actorUserId, null,
                    java.util.Map.of("warehouseId", activated.id().toString(), "publicWarehouseId", activated.publicWarehouseId(), "status", activated.status().name()));
        }
        return activated;
    }

    @Transactional
    public WarehouseBranchAccess grantBranchAccess(GrantBranchAccessCommand command) {
        log.info("Iniciando acceso de almacén warehouseId={} branchId={}", command.warehouseId(), command.branchId());
        var warehouse = warehouseRepository.findById(command.warehouseId())
                .orElseThrow(() -> {
                    log.warn("Acceso rechazado: almacén no encontrado warehouseId={}", command.warehouseId());
                    return InventoryApplicationException.notFound("warehouse not found");
                });
        if (warehouse.scope() != WarehouseScope.COMPANY_SHARED) {
            log.warn("Acceso rechazado: almacén no compartido warehouseId={} scope={}", command.warehouseId(), warehouse.scope());
            throw new InventoryApplicationException("branch access is only valid for company-shared warehouses");
        }
        if (accessRepository.existsByWarehouseIdAndBranchId(command.warehouseId(), command.branchId())) {
            log.warn("Acceso rechazado por duplicidad warehouseId={} branchId={}", command.warehouseId(), command.branchId());
            throw new InventoryApplicationException("branch access already exists");
        }
        var saved = accessRepository.save(new WarehouseBranchAccess(UUID.randomUUID(), command.warehouseId(),
                command.branchId(), WarehouseBranchAccess.AccessStatus.ACTIVE, command.assignedBy()));
        log.info("Acceso de almacén persistido accessId={} warehouseId={} branchId={}", saved.id(), saved.warehouseId(), saved.branchId());
        return saved;
    }

    @Transactional
    public WarehouseLocation createLocation(CreateLocationCommand command) {
        log.info("Iniciando creación de ubicación warehouseId={} code={}", command.warehouseId(), command.code());
        var warehouse = warehouseRepository.findById(command.warehouseId())
                .orElseThrow(() -> {
                    log.warn("Creación de ubicación rechazada: almacén no encontrado warehouseId={}", command.warehouseId());
                    return InventoryApplicationException.notFound("warehouse not found");
                });
        if (warehouse.status() == WarehouseStatus.INACTIVE || warehouse.status() == WarehouseStatus.BLOCKED) {
            log.warn("Creación de ubicación rechazada por estado warehouseId={} status={}", command.warehouseId(), warehouse.status());
            throw new InventoryApplicationException("location cannot be created in an inactive warehouse");
        }
        if (locationRepository.existsByWarehouseIdAndCode(command.warehouseId(), command.code())) {
            log.warn("Creación de ubicación rechazada por código duplicado warehouseId={} code={}", command.warehouseId(), command.code());
            throw new InventoryApplicationException("location code already exists for warehouse");
        }
        if (command.capacityUnitCode() != null && !command.capacityUnitCode().isBlank() && !unitExists(warehouse.companyId(), command.capacityUnitCode())) {
            log.warn("Creación de ubicación rechazada: unidad de capacidad inexistente warehouseId={} unit={}",
                    command.warehouseId(), command.capacityUnitCode());
            throw new InventoryApplicationException("capacityUnitCode does not reference an active unit");
        }
        if (command.parentLocationId() != null && locationRepository.findByWarehouseId(command.warehouseId()).stream()
                .noneMatch(location -> location.id().equals(command.parentLocationId()))) {
            log.warn("Creación de ubicación rechazada: padre fuera del almacén warehouseId={} parentLocationId={}",
                    command.warehouseId(), command.parentLocationId());
            throw new InventoryApplicationException("parent location does not belong to warehouse");
        }
        var saved = locationRepository.save(new WarehouseLocation(UUID.randomUUID(), UUID.randomUUID(), command.warehouseId(),
                command.parentLocationId(), command.code(), command.name(), command.type(),
                com.jcode.jcomercy.inventory.domain.model.warehouse.LocationStatus.ACTIVE,
                command.capacity(), command.capacityUnitCode()));
        log.info("Ubicación persistida id={} publicLocationId={} warehouseId={}", saved.id(), saved.publicLocationId(), saved.warehouseId());
        return saved;
    }

    private boolean unitExists(String companyId, String code) {
        if (units != null && units.findByCode(code).isPresent()) return true;
        return companyUnits != null && companyUnits.findActiveByCompanyIdAndCode(companyId, code).isPresent();
    }

    public record CreateWarehouseCommand(String companyId, WarehouseScope scope, String branchId, String code,
                                         String name, WarehouseType type, boolean defaultWarehouse, String address,
                                         String phone, String email, String managerId, String timezone,
                                         String currency, String actorUserId) {
    }

    public record CreateBranchWarehouseCommand(String code, String name, WarehouseType type,
                                               boolean defaultWarehouse, String address, String phone, String email,
                                               String managerId, String timezone, String currency, String actorUserId) {
    }

    public record GrantBranchAccessCommand(UUID warehouseId, String branchId, String assignedBy) {
    }

    public record CreateLocationCommand(UUID warehouseId, UUID parentLocationId, String code, String name,
                                       com.jcode.jcomercy.inventory.domain.model.warehouse.LocationType type,
                                       BigDecimal capacity, String capacityUnitCode) {
    }
}
