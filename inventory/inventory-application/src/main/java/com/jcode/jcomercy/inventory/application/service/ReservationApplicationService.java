package com.jcode.jcomercy.inventory.application.service;

import com.jcode.jcomercy.inventory.application.exception.InventoryApplicationException;
import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;
import com.jcode.jcomercy.inventory.domain.model.product.ProductStatus;
import com.jcode.jcomercy.inventory.domain.model.stock.InventoryMovement;
import com.jcode.jcomercy.inventory.domain.model.stock.InventoryStatus;
import com.jcode.jcomercy.inventory.domain.model.stock.MovementType;
import com.jcode.jcomercy.inventory.domain.model.stock.ReservationStatus;
import com.jcode.jcomercy.inventory.domain.model.stock.StockBalance;
import com.jcode.jcomercy.inventory.domain.model.stock.StockReservation;
import com.jcode.jcomercy.inventory.domain.model.warehouse.Warehouse;
import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseScope;
import com.jcode.jcomercy.inventory.domain.port.InventoryMovementRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.InventoryOutboxPort;
import com.jcode.jcomercy.inventory.domain.port.ProductRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.StockBalanceRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.StockReservationRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.WarehouseBranchAccessRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.WarehouseRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sets stock aside for a business reference (for example a sales order line) so it cannot be sold twice, and later
 * ships it (commit) or gives it back (release / expiration). One reference covers one product in one warehouse; an
 * order with several products uses one reference per line.
 *
 * <p>Every operation locks the affected balances first, always in id order, so concurrent callers are serialized
 * and cannot deadlock. Reservations are only read after the locks are held.
 */
public class ReservationApplicationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationApplicationService.class);

    private final WarehouseRepositoryPort warehouses;
    private final WarehouseBranchAccessRepositoryPort branchAccess;
    private final ProductRepositoryPort products;
    private final StockBalanceRepositoryPort balances;
    private final StockReservationRepositoryPort reservations;
    private final InventoryMovementRepositoryPort movements;
    private final InventoryOutboxPort outboxPort;

    public ReservationApplicationService(WarehouseRepositoryPort warehouses,
                                         WarehouseBranchAccessRepositoryPort branchAccess,
                                         ProductRepositoryPort products, StockBalanceRepositoryPort balances,
                                         StockReservationRepositoryPort reservations,
                                         InventoryMovementRepositoryPort movements, InventoryOutboxPort outboxPort) {
        this.warehouses = warehouses;
        this.branchAccess = branchAccess;
        this.products = products;
        this.balances = balances;
        this.reservations = reservations;
        this.movements = movements;
        this.outboxPort = outboxPort;
    }

    @Transactional
    public ReservationResult reserve(ReserveCommand command) {
        log.info("Iniciando reserva companyId={} branchId={} warehouseId={} productId={} quantity={} referencia={}/{}",
                command.companyId(), command.branchId(), command.warehouseId(), command.productId(),
                command.quantity(), command.referenceType(), command.referenceId());
        var warehouse = operableWarehouse(command.companyId(), command.branchId(), command.warehouseId());
        var product = products.findById(command.productId())
                .orElseThrow(() -> InventoryApplicationException.notFound("product not found"));
        if (!product.companyId().equals(command.companyId()) || product.status() != ProductStatus.ACTIVE) {
            throw new InventoryApplicationException("product is not active for company");
        }
        if (!product.stockUnitCode().equalsIgnoreCase(command.unitCode())) {
            throw new InventoryApplicationException("unitCode must match the product stock unit in this stage");
        }
        if (command.quantity() == null || command.quantity().signum() <= 0) {
            throw new InventoryApplicationException("quantity must be greater than zero");
        }
        var referenceType = StockReservation.normalizeReference(command.referenceType());
        var referenceId = StockReservation.normalizeReference(command.referenceId());

        // Locks first: two simultaneous reservations of the same product are serialized here, so the duplicate-reference
        // check below already sees whatever the first one committed.
        var candidates = balances.findAllocatableForUpdate(command.companyId(), warehouse.id(), product.id(),
                InventoryStatus.AVAILABLE, command.locationId());
        if (reservations.existsActiveByReference(command.companyId(), referenceType, referenceId)) {
            log.warn("Reserva rechazada: la referencia ya tiene reservas activas companyId={} referencia={}/{}",
                    command.companyId(), referenceType, referenceId);
            throw new InventoryApplicationException("an active reservation already exists for this reference");
        }
        var free = candidates.stream().map(StockBalance::available).filter(value -> value.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (free.compareTo(command.quantity()) < 0) {
            log.warn("Reserva rechazada por stock insuficiente productId={} solicitado={} disponible={}",
                    product.id(), command.quantity(), free);
            throw new InventoryDomainException("insufficient available stock for reservation");
        }

        var now = Instant.now();
        var created = new ArrayList<StockReservation>();
        var remaining = command.quantity();
        for (var balance : candidates) {
            if (remaining.signum() == 0) break;
            var room = balance.available();
            if (room.signum() <= 0) continue;
            var take = room.min(remaining);
            var updated = balances.save(balance.reserve(take));
            created.add(reservations.save(new StockReservation(UUID.randomUUID(), UUID.randomUUID(),
                    command.companyId(), command.branchId(), warehouse.id(), updated.locationId(), product.id(),
                    updated.id(), take, ReservationStatus.ACTIVE, referenceType, referenceId, command.expiresAt(),
                    command.actorUserId(), command.correlationId(), now, now, 0)));
            remaining = remaining.subtract(take);
        }
        var availableAfter = availableFor(command.companyId(), warehouse.id(), product.id());
        for (var reservation : created) {
            publish("inventory.reservation.created", reservation, command.actorUserId(), command.correlationId(),
                    availableAfter, Map.of());
        }
        log.info("Reserva creada referencia={}/{} reservas={} disponibleDespues={}", referenceType, referenceId,
                created.size(), availableAfter);
        return new ReservationResult(List.copyOf(created), availableAfter);
    }

    @Transactional
    public ReservationResult release(ReferenceCommand command) {
        var scope = lockActive(command.companyId(), command.referenceType(), command.referenceId());
        var now = Instant.now();
        var released = new ArrayList<StockReservation>();
        for (var reservation : scope.reservations()) {
            requireBranchCanOperate(loadWarehouse(reservation.warehouseId()), command.branchId());
            var balance = lockedBalance(scope.balances(), reservation.stockBalanceId());
            scope.balances().put(balance.id(), balances.save(balance.releaseReservation(reservation.quantity())));
            released.add(reservations.save(reservation.release(now)));
        }
        var availableAfter = availableForFirst(command.companyId(), released);
        Map<String, Object> extra = command.reason() == null ? Map.of() : Map.of("reason", command.reason());
        for (var reservation : released) {
            publish("inventory.reservation.released", reservation, command.actorUserId(), command.correlationId(),
                    availableAfter, extra);
        }
        log.info("Reserva liberada referencia={}/{} reservas={} disponibleDespues={}", command.referenceType(),
                command.referenceId(), released.size(), availableAfter);
        return new ReservationResult(List.copyOf(released), availableAfter);
    }

    @Transactional
    public CommitResult commit(ReferenceCommand command) {
        var scope = lockActive(command.companyId(), command.referenceType(), command.referenceId());
        var now = Instant.now();
        var committed = new ArrayList<StockReservation>();
        var issued = new ArrayList<InventoryMovement>();
        var onHandAfter = new ArrayList<BigDecimal>();
        for (var reservation : scope.reservations()) {
            operableWarehouse(command.companyId(), command.branchId(), reservation.warehouseId());
            var product = products.findById(reservation.productId())
                    .orElseThrow(() -> InventoryApplicationException.notFound("product not found"));
            var balance = lockedBalance(scope.balances(), reservation.stockBalanceId());
            var updated = balances.save(balance.commitReservation(reservation.quantity()));
            scope.balances().put(updated.id(), updated);
            committed.add(reservations.save(reservation.commit(now)));
            onHandAfter.add(updated.onHand());
            var totalCost = balance.unitCost() == null ? null : balance.unitCost().multiply(reservation.quantity());
            issued.add(movements.save(new InventoryMovement(UUID.randomUUID(), UUID.randomUUID(), command.companyId(),
                    command.branchId(), reservation.warehouseId(), reservation.locationId(), reservation.productId(),
                    MovementType.ISSUE, reservation.quantity(), product.stockUnitCode(), balance.unitCost(), totalCost,
                    balance.lotId(), balance.serialNumber(), balance.expirationDate(), reservation.referenceType(),
                    reservation.referenceId(), null, now, command.actorUserId(), command.correlationId(), now)));
        }
        var availableAfter = availableForFirst(command.companyId(), committed);
        for (int i = 0; i < committed.size(); i++) {
            var reservation = committed.get(i);
            var movement = issued.get(i);
            publish("inventory.reservation.committed", reservation, command.actorUserId(), command.correlationId(),
                    availableAfter, Map.of("movementId", movement.id().toString()));
            publishIssued(movement, onHandAfter.get(i));
        }
        log.info("Reserva confirmada referencia={}/{} reservas={} disponibleDespues={}", command.referenceType(),
                command.referenceId(), committed.size(), availableAfter);
        return new CommitResult(List.copyOf(committed), List.copyOf(issued), availableAfter);
    }

    /** Gives back the stock of every active reservation whose deadline has passed. Returns how many expired. */
    @Transactional
    public int expireDue(Instant now, int limit) {
        var balanceIds = reservations.findDueBalanceIds(now, limit);
        if (balanceIds.isEmpty()) {
            return 0;
        }
        var locked = lockBalances(balanceIds);
        var expired = new ArrayList<StockReservation>();
        for (var reservation : reservations.findDue(now, limit)) {
            var balance = lockedBalance(locked, reservation.stockBalanceId());
            locked.put(balance.id(), balances.save(balance.releaseReservation(reservation.quantity())));
            expired.add(reservations.save(reservation.expire(now)));
        }
        for (var reservation : expired) {
            var availableAfter = availableFor(reservation.companyId(), reservation.warehouseId(), reservation.productId());
            publish("inventory.reservation.expired", reservation, null, reservation.correlationId(), availableAfter,
                    Map.of());
        }
        log.info("Reservas vencidas liberadas total={}", expired.size());
        return expired.size();
    }

    @Transactional(readOnly = true)
    public List<Availability> availability(String companyId, UUID productId) {
        var stock = productId == null ? balances.findByCompanyId(companyId)
                : balances.findByCompanyIdAndProductId(companyId, productId);
        var totals = new LinkedHashMap<List<UUID>, BigDecimal[]>();
        for (var balance : stock) {
            if (balance.inventoryStatus() != InventoryStatus.AVAILABLE) continue;
            var sums = totals.computeIfAbsent(List.of(balance.warehouseId(), balance.productId()),
                    key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            sums[0] = sums[0].add(balance.onHand());
            sums[1] = sums[1].add(balance.reserved());
        }
        var result = new ArrayList<Availability>();
        totals.forEach((key, sums) -> result.add(new Availability(key.get(0), key.get(1), sums[0], sums[1],
                sums[0].subtract(sums[1]).max(BigDecimal.ZERO))));
        return result;
    }

    // ---- internals ----

    private ActiveReferences lockActive(String companyId, String rawType, String rawId) {
        var referenceType = StockReservation.normalizeReference(rawType);
        var referenceId = StockReservation.normalizeReference(rawId);
        var balanceIds = reservations.findActiveBalanceIdsByReference(companyId, referenceType, referenceId);
        if (balanceIds.isEmpty()) {
            throw InventoryApplicationException.notFound("reservation not found");
        }
        var locked = lockBalances(balanceIds);
        var active = reservations.findActiveByReference(companyId, referenceType, referenceId);
        if (active.isEmpty()) {
            throw InventoryApplicationException.notFound("reservation not found");
        }
        return new ActiveReferences(active, locked);
    }

    private Map<UUID, StockBalance> lockBalances(List<UUID> orderedIds) {
        var locked = new LinkedHashMap<UUID, StockBalance>();
        for (var id : orderedIds) {
            locked.put(id, balances.findByIdForUpdate(id)
                    .orElseThrow(() -> InventoryApplicationException.notFound("stock balance not found")));
        }
        return locked;
    }

    private StockBalance lockedBalance(Map<UUID, StockBalance> locked, UUID id) {
        var balance = locked.get(id);
        if (balance == null) {
            balance = balances.findByIdForUpdate(id)
                    .orElseThrow(() -> InventoryApplicationException.notFound("stock balance not found"));
            locked.put(id, balance);
        }
        return balance;
    }

    private Warehouse operableWarehouse(String companyId, String branchId, UUID warehouseId) {
        var warehouse = loadWarehouse(warehouseId);
        if (!warehouse.companyId().equals(companyId)) {
            throw new InventoryApplicationException("warehouse does not belong to company");
        }
        if (!warehouse.acceptsMovements()) {
            throw new InventoryApplicationException("warehouse is not active");
        }
        requireBranchCanOperate(warehouse, branchId);
        return warehouse;
    }

    private Warehouse loadWarehouse(UUID warehouseId) {
        return warehouses.findById(warehouseId)
                .orElseThrow(() -> InventoryApplicationException.notFound("warehouse not found"));
    }

    private void requireBranchCanOperate(Warehouse warehouse, String branchId) {
        if (warehouse.scope() == WarehouseScope.BRANCH_OWNED && !branchId.equals(warehouse.branchId())) {
            throw new InventoryApplicationException("branch cannot operate this warehouse");
        }
        if (warehouse.scope() == WarehouseScope.COMPANY_SHARED
                && !branchAccess.existsByWarehouseIdAndBranchId(warehouse.id(), branchId)) {
            throw new InventoryApplicationException("branch does not have access to this shared warehouse");
        }
    }

    private BigDecimal availableForFirst(String companyId, List<StockReservation> group) {
        var first = group.get(0);
        return availableFor(companyId, first.warehouseId(), first.productId());
    }

    private BigDecimal availableFor(String companyId, UUID warehouseId, UUID productId) {
        return balances.findByCompanyIdAndProductId(companyId, productId).stream()
                .filter(balance -> balance.warehouseId().equals(warehouseId)
                        && balance.inventoryStatus() == InventoryStatus.AVAILABLE)
                .map(StockBalance::available)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .max(BigDecimal.ZERO);
    }

    private void publish(String eventType, StockReservation reservation, String actorUserId, String correlationId,
                         BigDecimal available, Map<String, Object> extra) {
        if (outboxPort == null) return;
        var data = new LinkedHashMap<String, Object>();
        data.put("reservationId", reservation.id().toString());
        data.put("publicReservationId", reservation.publicReservationId().toString());
        data.put("productId", reservation.productId().toString());
        data.put("warehouseId", reservation.warehouseId().toString());
        data.put("locationId", reservation.locationId().toString());
        data.put("stockBalanceId", reservation.stockBalanceId().toString());
        data.put("quantity", reservation.quantity());
        data.put("status", reservation.status().name());
        data.put("referenceType", reservation.referenceType());
        data.put("referenceId", reservation.referenceId());
        if (reservation.expiresAt() != null) data.put("expiresAt", reservation.expiresAt().toString());
        data.put("available", available);
        data.putAll(extra);
        outboxPort.publish(eventType, reservation.companyId(), reservation.branchId(), actorUserId, correlationId, data);
    }

    private void publishIssued(InventoryMovement movement, BigDecimal onHand) {
        if (outboxPort == null) return;
        outboxPort.publish("inventory.stock.issued", movement.companyId(), movement.branchId(), movement.actorUserId(),
                movement.correlationId(), Map.of(
                        "movementId", movement.id().toString(),
                        "publicMovementId", movement.publicMovementId(),
                        "productId", movement.productId().toString(),
                        "warehouseId", movement.warehouseId().toString(),
                        "locationId", movement.locationId().toString(),
                        "type", movement.type().name(),
                        "quantity", movement.quantity(),
                        "onHand", onHand,
                        "referenceType", movement.referenceType(),
                        "referenceId", movement.referenceId()));
    }

    public record ReserveCommand(String companyId, String branchId, UUID warehouseId, UUID locationId, UUID productId,
                                 BigDecimal quantity, String unitCode, String referenceType, String referenceId,
                                 Instant expiresAt, String actorUserId, String correlationId) { }

    public record ReferenceCommand(String companyId, String branchId, String referenceType, String referenceId,
                                   String reason, String actorUserId, String correlationId) { }

    public record ReservationResult(List<StockReservation> reservations, BigDecimal available) { }

    public record CommitResult(List<StockReservation> reservations, List<InventoryMovement> movements,
                               BigDecimal available) { }

    public record Availability(UUID warehouseId, UUID productId, BigDecimal onHand, BigDecimal reserved,
                               BigDecimal available) { }

    private record ActiveReferences(List<StockReservation> reservations, Map<UUID, StockBalance> balances) { }
}
