package com.jcode.jcomercy.inventory.application.service;

import com.jcode.jcomercy.inventory.application.exception.InventoryApplicationException;
import com.jcode.jcomercy.inventory.domain.model.product.Product;
import com.jcode.jcomercy.inventory.domain.model.product.ProductStatus;
import com.jcode.jcomercy.inventory.domain.model.stock.InventoryMovement;
import com.jcode.jcomercy.inventory.domain.model.stock.InventoryStatus;
import com.jcode.jcomercy.inventory.domain.model.stock.MovementType;
import com.jcode.jcomercy.inventory.domain.model.stock.StockBalance;
import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseScope;
import com.jcode.jcomercy.inventory.domain.port.InventoryMovementRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.ProductRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.SerialNumberGeneratorPort;
import com.jcode.jcomercy.inventory.domain.port.StockBalanceRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.WarehouseBranchAccessRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.WarehouseLocationRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.WarehouseRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

/** First operational stock commands. Costs are recorded, but cost valuation strategies are introduced separately. */
public class StockApplicationService {
    private static final Logger log = LoggerFactory.getLogger(StockApplicationService.class);
    private final WarehouseRepositoryPort warehouses;
    private final WarehouseLocationRepositoryPort locations;
    private final WarehouseBranchAccessRepositoryPort branchAccess;
    private final ProductRepositoryPort products;
    private final StockBalanceRepositoryPort balances;
    private final InventoryMovementRepositoryPort movements;
    private final SerialNumberGeneratorPort serials;
    private final com.jcode.jcomercy.inventory.domain.port.InventoryOutboxPort outboxPort;

    public StockApplicationService(WarehouseRepositoryPort warehouses, WarehouseLocationRepositoryPort locations,
                                   WarehouseBranchAccessRepositoryPort branchAccess, ProductRepositoryPort products,
                                   StockBalanceRepositoryPort balances, InventoryMovementRepositoryPort movements,
                                   SerialNumberGeneratorPort serials) {
        this(warehouses, locations, branchAccess, products, balances, movements, serials, null);
    }

    public StockApplicationService(WarehouseRepositoryPort warehouses, WarehouseLocationRepositoryPort locations,
                                   WarehouseBranchAccessRepositoryPort branchAccess, ProductRepositoryPort products,
                                   StockBalanceRepositoryPort balances, InventoryMovementRepositoryPort movements,
                                   SerialNumberGeneratorPort serials,
                                   com.jcode.jcomercy.inventory.domain.port.InventoryOutboxPort outboxPort) {
        this.warehouses = warehouses;
        this.locations = locations;
        this.branchAccess = branchAccess;
        this.products = products;
        this.balances = balances;
        this.movements = movements;
        this.serials = serials;
        this.outboxPort = outboxPort;
    }

    @Transactional
    public StockOperationResult receive(ReceiveCommand command) {
        log.info("Iniciando recepción de stock companyId={} branchId={} warehouseId={} productId={} quantity={}",
                command.companyId(), command.branchId(), command.warehouseId(), command.productId(), command.quantity());
        return post(new StockCommand(command.companyId(), command.branchId(), command.warehouseId(), command.locationId(),
                command.productId(), command.quantity(), command.unitCode(), command.unitCost(), command.lotId(),
                command.serialNumber(), command.expirationDate(), command.inventoryStatus(), command.referenceType(),
                command.referenceId(), null, command.actorUserId(), command.correlationId(), false), MovementType.RECEIPT);
    }

    @Transactional
    public StockOperationResult issue(IssueCommand command) {
        return issue(command, false);
    }

    @Transactional
    public StockOperationResult issue(IssueCommand command, boolean negativeStockPermission) {
        log.info("Iniciando salida de stock companyId={} branchId={} warehouseId={} productId={} quantity={}",
                command.companyId(), command.branchId(), command.warehouseId(), command.productId(), command.quantity());
        return post(new StockCommand(command.companyId(), command.branchId(), command.warehouseId(), command.locationId(),
                command.productId(), command.quantity(), command.unitCode(), command.unitCost(), command.lotId(),
                command.serialNumber(), command.expirationDate(), command.inventoryStatus(), command.referenceType(),
                command.referenceId(), null, command.actorUserId(), command.correlationId(), negativeStockPermission), MovementType.ISSUE);
    }

    @Transactional
    public StockOperationResult adjust(AdjustmentCommand command) {
        return adjust(command, false);
    }

    @Transactional
    public StockOperationResult adjust(AdjustmentCommand command, boolean negativeStockPermission) {
        var type = command.direction() == AdjustmentDirection.IN ? MovementType.ADJUSTMENT_IN : MovementType.ADJUSTMENT_OUT;
        log.info("Iniciando ajuste de stock type={} companyId={} warehouseId={} productId={} quantity={}", type,
                command.companyId(), command.warehouseId(), command.productId(), command.quantity());
        return post(new StockCommand(command.companyId(), command.branchId(), command.warehouseId(), command.locationId(),
                command.productId(), command.quantity(), command.unitCode(), command.unitCost(), command.lotId(),
                command.serialNumber(), command.expirationDate(), command.inventoryStatus(), command.referenceType(),
                command.referenceId(), command.reason(), command.actorUserId(), command.correlationId(), negativeStockPermission), type);
    }

    @Transactional(readOnly = true)
    public List<StockBalance> listBalances(String companyId, UUID productId) {
        var result = productId == null ? balances.findByCompanyId(companyId)
                : balances.findByCompanyIdAndProductId(companyId, productId);
        log.info("Saldos consultados companyId={} productId={} total={}", companyId, productId, result.size());
        return result;
    }

    @Transactional(readOnly = true)
    public List<InventoryMovement> listMovements(String companyId, UUID productId) {
        var result = productId == null ? movements.findByCompanyId(companyId)
                : movements.findByCompanyIdAndProductId(companyId, productId);
        log.info("Movimientos consultados companyId={} productId={} total={}", companyId, productId, result.size());
        return result;
    }

    /**
     * Devuelve al saldo lo que salió con esa referencia (por ejemplo, una venta que se cancela): una entrada RETURN por
     * cada salida, al mismo saldo y con el costo original. Una referencia solo se devuelve una vez. La mercancía vuelve
     * como disponible: la salida no guarda de qué estado de inventario salió.
     *
     * <p>Se bloquean los saldos en orden de id, igual que las reservas, y solo después se comprueba que la referencia no
     * se haya devuelto ya: dos devoluciones a la vez se turnan y la segunda ve la primera.
     */
    @Transactional
    public ReturnResult returnByReference(ReturnCommand command) {
        var referenceType = optional(command.referenceType());
        var referenceId = optional(command.referenceId());
        if (referenceType == null || referenceId == null) {
            throw new InventoryApplicationException("referenceType and referenceId are required");
        }
        log.info("Iniciando devolución de stock companyId={} branchId={} referencia={}/{}", command.companyId(),
                command.branchId(), referenceType, referenceId);
        var issues = movements.findByCompanyIdAndReference(command.companyId(), referenceType, referenceId).stream()
                .filter(movement -> movement.type() == MovementType.ISSUE).toList();
        if (issues.isEmpty()) {
            throw InventoryApplicationException.notFound("no stock issue found for this reference");
        }
        for (var issue : issues) {
            operableWarehouse(command.companyId(), command.branchId(), issue.warehouseId());
        }
        // Solo se buscan los ids: leer un saldo antes de bloquearlo dejaría una copia vieja si otra devolución lo cambia
        // mientras esta espera el bloqueo, y al guardar fallaría por la versión.
        var byBalance = new TreeMap<UUID, List<InventoryMovement>>();
        for (var issue : issues) {
            var balanceId = balances.findIdByIdentity(issue.warehouseId(), issue.locationId(), issue.productId(), issue.lotId(),
                            issue.serialNumber(), InventoryStatus.AVAILABLE)
                    .orElseThrow(() -> InventoryApplicationException.notFound("stock balance not found"));
            byBalance.computeIfAbsent(balanceId, id -> new ArrayList<>()).add(issue);
        }
        var locked = new TreeMap<UUID, StockBalance>();
        for (var id : byBalance.keySet()) {
            locked.put(id, balances.findByIdForUpdate(id).orElseThrow(() -> InventoryApplicationException.notFound("stock balance not found")));
        }
        // Con los saldos bloqueados, otra devolución de la misma referencia ya terminó o todavía no empezó.
        if (movements.findByCompanyIdAndReference(command.companyId(), referenceType, referenceId).stream()
                .anyMatch(movement -> movement.type() == MovementType.RETURN)) {
            log.warn("Devolución rechazada: la referencia ya se devolvió companyId={} referencia={}/{}", command.companyId(),
                    referenceType, referenceId);
            throw new InventoryApplicationException("this reference has already been returned");
        }
        var returned = new ArrayList<InventoryMovement>();
        for (var entry : byBalance.entrySet()) {
            var current = locked.get(entry.getKey());
            for (var issue : entry.getValue()) {
                var now = Instant.now();
                var result = persist(current.receive(issue.quantity(), null), new InventoryMovement(UUID.randomUUID(),
                        UUID.randomUUID(), issue.companyId(), command.branchId(), issue.warehouseId(), issue.locationId(),
                        issue.productId(), MovementType.RETURN, issue.quantity(), issue.unitCode(), issue.unitCost(),
                        issue.totalCost(), issue.lotId(), issue.serialNumber(), issue.expirationDate(), issue.referenceType(),
                        issue.referenceId(), command.reason(), now, command.actorUserId(), command.correlationId(), now));
                current = result.balance();
                returned.add(result.movement());
            }
        }
        log.info("Devolución de stock aplicada referencia={}/{} movimientos={}", referenceType, referenceId, returned.size());
        return new ReturnResult(List.copyOf(returned));
    }

    private com.jcode.jcomercy.inventory.domain.model.warehouse.Warehouse operableWarehouse(String companyId, String branchId,
                                                                                         UUID warehouseId) {
        var warehouse = warehouses.findById(warehouseId)
                .orElseThrow(() -> InventoryApplicationException.notFound("warehouse not found"));
        if (!warehouse.companyId().equals(companyId)) throw new InventoryApplicationException("warehouse does not belong to company");
        if (!warehouse.acceptsMovements()) throw new InventoryApplicationException("warehouse is not active");
        if (warehouse.scope() == WarehouseScope.BRANCH_OWNED && !branchId.equals(warehouse.branchId())) {
            throw new InventoryApplicationException("branch cannot operate this warehouse");
        }
        if (warehouse.scope() == WarehouseScope.COMPANY_SHARED
                && !branchAccess.existsByWarehouseIdAndBranchId(warehouse.id(), branchId)) {
            throw new InventoryApplicationException("branch does not have access to this shared warehouse");
        }
        return warehouse;
    }

    private StockOperationResult post(StockCommand command, MovementType type) {
        var warehouse = operableWarehouse(command.companyId(), command.branchId(), command.warehouseId());
        // Se bloquea la ubicación: si el producto aún no tiene saldo aquí, dos movimientos a la vez lo crearían los dos
        // (la restricción única no los frena porque lote y serie son nulos). Los demás flujos bloquean solo saldos.
        var location = locations.findByIdForUpdate(command.locationId())
                .orElseThrow(() -> InventoryApplicationException.notFound("warehouse location not found"));
        if (!location.warehouseId().equals(warehouse.id())) throw new InventoryApplicationException("location does not belong to warehouse");
        if (!location.acceptsStock()) throw new InventoryApplicationException("warehouse location does not accept stock");
        var product = products.findById(command.productId()).orElseThrow(() -> InventoryApplicationException.notFound("product not found"));
        if (!product.companyId().equals(command.companyId()) || product.status() != ProductStatus.ACTIVE) {
            throw new InventoryApplicationException("product is not active for company");
        }
        if ((type == MovementType.ISSUE || type == MovementType.ADJUSTMENT_OUT)
                && product.allowNegativeStock() && !command.negativeStockPermission()) {
            throw new InventoryApplicationException("negative stock requires explicit permission");
        }
        if (!product.stockUnitCode().equalsIgnoreCase(command.unitCode())) {
            throw new InventoryApplicationException("unitCode must match the product stock unit in this stage");
        }
        if (command.inventoryStatus() == null) throw new InventoryApplicationException("inventoryStatus is required");
        if (command.quantity() == null || command.quantity().signum() <= 0) throw new InventoryApplicationException("quantity must be greater than zero");

        var tracking = tracking(product, command, type);
        var existing = balances.findForUpdate(warehouse.id(), location.id(), product.id(), tracking.lotId(),
                tracking.serialNumber(), command.inventoryStatus());
        if (type == MovementType.RECEIPT || type == MovementType.ADJUSTMENT_IN) {
            if (tracking.serialNumber() != null && balances.existsByCompanyIdAndSerialNumber(command.companyId(), tracking.serialNumber())) {
                throw new InventoryApplicationException("serialNumber already exists for company");
            }
            var updated = existing.map(balance -> balance.receive(command.quantity(), command.unitCost()))
                    .orElseGet(() -> new StockBalance(UUID.randomUUID(), command.companyId(), command.branchId(), warehouse.id(),
                            location.id(), product.id(), tracking.lotId(), tracking.serialNumber(), tracking.expirationDate(),
                            command.inventoryStatus(), command.quantity(), BigDecimal.ZERO, command.unitCost(), "DEFAULT", 0));
            return persist(updated, movement(command, type, tracking));
        }
        var balance = existing.orElseThrow(() -> InventoryApplicationException.notFound("stock balance not found"));
        var updated = balance.issue(command.quantity(), product.allowNegativeStock());
        return persist(updated, movement(command, type, tracking));
    }

    private StockOperationResult persist(StockBalance balance, InventoryMovement movement) {
        var savedBalance = balances.save(balance);
        var savedMovement = movements.save(movement);
        log.info("Movimiento de inventario aplicado movementId={} type={} balanceId={} onHand={} reserved={}",
                savedMovement.publicMovementId(), savedMovement.type(), savedBalance.id(), savedBalance.onHand(), savedBalance.reserved());
        if (outboxPort != null) {
            String eventType = switch (savedMovement.type()) {
                case RECEIPT -> "inventory.stock.received";
                case ISSUE -> "inventory.stock.issued";
                case ADJUSTMENT_IN, ADJUSTMENT_OUT -> "inventory.stock.adjusted";
                default -> "inventory.stock.movement";
            };
            outboxPort.publish(eventType, savedMovement.companyId(), savedMovement.branchId(), savedMovement.actorUserId(),
                    savedMovement.correlationId(), java.util.Map.of(
                            "movementId", savedMovement.id().toString(),
                            "publicMovementId", savedMovement.publicMovementId(),
                            "productId", savedMovement.productId().toString(),
                            "warehouseId", savedMovement.warehouseId().toString(),
                            "locationId", savedMovement.locationId().toString(),
                            "type", savedMovement.type().name(),
                            "quantity", savedMovement.quantity(),
                            "onHand", savedBalance.onHand()
                    ));
        }
        return new StockOperationResult(savedMovement, savedBalance);
    }

    private Tracking tracking(Product product, StockCommand command, MovementType type) {
        var lotId = optional(command.lotId());
        var serial = optional(command.serialNumber());
        var expiration = command.expirationDate();
        if (product.trackingMode().requiresLot() && lotId == null) throw new InventoryApplicationException("lotId is required for this product");
        if (product.trackingMode().requiresSerial()) {
            if (command.quantity().compareTo(BigDecimal.ONE) != 0) throw new InventoryApplicationException("serialized products require quantity equal to one");
            if (serial == null && (type == MovementType.RECEIPT || type == MovementType.ADJUSTMENT_IN)) serial = serials.next(product.companyId());
            if (serial == null) throw new InventoryApplicationException("serialNumber is required for stock output");
        } else if (serial != null) {
            throw new InventoryApplicationException("serialNumber is not supported by this product");
        }
        if (product.trackingMode().supportsExpiration() && expiration == null) throw new InventoryApplicationException("expirationDate is required for this product");
        if (!product.trackingMode().supportsExpiration() && expiration != null) throw new InventoryApplicationException("expirationDate is not supported by this product");
        return new Tracking(lotId, serial, expiration);
    }

    private InventoryMovement movement(StockCommand command, MovementType type, Tracking tracking) {
        var now = Instant.now();
        var totalCost = command.unitCost() == null ? null : command.unitCost().multiply(command.quantity());
        return new InventoryMovement(UUID.randomUUID(), UUID.randomUUID(), command.companyId(), command.branchId(),
                command.warehouseId(), command.locationId(), command.productId(), type, command.quantity(), command.unitCode(),
                command.unitCost(), totalCost, tracking.lotId(), tracking.serialNumber(), tracking.expirationDate(),
                optional(command.referenceType()), optional(command.referenceId()), command.reason(), now, command.actorUserId(),
                command.correlationId(), now);
    }

    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim().toUpperCase(); }

    public enum AdjustmentDirection { IN, OUT }
    public record ReceiveCommand(String companyId, String branchId, UUID warehouseId, UUID locationId, UUID productId,
                                 BigDecimal quantity, String unitCode, BigDecimal unitCost, String lotId,
                                 String serialNumber, LocalDate expirationDate, InventoryStatus inventoryStatus,
                                 String referenceType, String referenceId, String actorUserId, String correlationId) { }
    public record IssueCommand(String companyId, String branchId, UUID warehouseId, UUID locationId, UUID productId,
                               BigDecimal quantity, String unitCode, BigDecimal unitCost, String lotId,
                               String serialNumber, LocalDate expirationDate, InventoryStatus inventoryStatus,
                               String referenceType, String referenceId, String actorUserId, String correlationId) { }
    public record AdjustmentCommand(String companyId, String branchId, UUID warehouseId, UUID locationId, UUID productId,
                                   BigDecimal quantity, String unitCode, BigDecimal unitCost, String lotId,
                                   String serialNumber, LocalDate expirationDate, InventoryStatus inventoryStatus,
                                   String referenceType, String referenceId, String reason, String actorUserId,
                                   String correlationId, AdjustmentDirection direction) { }
    public record StockOperationResult(InventoryMovement movement, StockBalance balance) { }
    public record ReturnCommand(String companyId, String branchId, String referenceType, String referenceId, String reason,
                                String actorUserId, String correlationId) { }
    public record ReturnResult(List<InventoryMovement> returned) { }
    private record StockCommand(String companyId, String branchId, UUID warehouseId, UUID locationId, UUID productId,
                                BigDecimal quantity, String unitCode, BigDecimal unitCost, String lotId,
                                String serialNumber, LocalDate expirationDate, InventoryStatus inventoryStatus,
                                String referenceType, String referenceId, String reason, String actorUserId,
                                String correlationId, boolean negativeStockPermission) { }
    private record Tracking(String lotId, String serialNumber, LocalDate expirationDate) { }
}
