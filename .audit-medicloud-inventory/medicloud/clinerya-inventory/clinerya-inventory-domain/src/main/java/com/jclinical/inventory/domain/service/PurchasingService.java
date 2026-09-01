package com.jclinical.inventory.domain.service;

import com.jclinical.core.events.DomainEventPublisherPort;
import com.jclinical.core.events.DomainEventRoutingKeys;
import com.jclinical.core.events.PurchaseOrderCreatedEvent;
import com.jclinical.inventory.domain.model.InventoryMovement;
import com.jclinical.inventory.domain.model.Material;
import com.jclinical.inventory.domain.model.PurchaseOrder;
import com.jclinical.inventory.domain.model.PurchaseOrderLine;
import com.jclinical.inventory.domain.model.PurchaseOrderStatus;
import com.jclinical.inventory.domain.model.PurchaseReceipt;
import com.jclinical.inventory.domain.model.PurchaseReceiptLine;
import com.jclinical.inventory.domain.model.Supplier;
import com.jclinical.inventory.domain.model.SupplierMaterial;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.AddSupplierMaterialCommand;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.CreatePurchaseOrderCommand;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.CreatePurchaseOrderLineCommand;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.CreateSupplierCommand;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.ReceivePurchaseOrderCommand;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.ReceivePurchaseOrderLineCommand;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.UpdateSupplierCommand;
import com.jclinical.inventory.domain.ports.out.MaterialRepositoryPort;
import com.jclinical.inventory.domain.ports.out.PurchaseOrderRepositoryPort;
import com.jclinical.inventory.domain.ports.out.PurchaseReceiptRepositoryPort;
import com.jclinical.inventory.domain.ports.out.SupplierRepositoryPort;
import com.jclinical.inventory.domain.ports.out.SupplierMaterialRepositoryPort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class PurchasingService {

    private final SupplierRepositoryPort supplierRepository;
    private final SupplierMaterialRepositoryPort supplierMaterialRepository;
    private final PurchaseOrderRepositoryPort orderRepository;
    private final PurchaseReceiptRepositoryPort receiptRepository;
    private final MaterialRepositoryPort materialRepository;
    private final InventoryMovementService movementService;
    private final DomainEventPublisherPort eventPublisher;

    public PurchasingService(
            SupplierRepositoryPort supplierRepository,
            SupplierMaterialRepositoryPort supplierMaterialRepository,
            PurchaseOrderRepositoryPort orderRepository,
            PurchaseReceiptRepositoryPort receiptRepository,
            MaterialRepositoryPort materialRepository,
            InventoryMovementService movementService,
            DomainEventPublisherPort eventPublisher) {
        this.supplierRepository = supplierRepository;
        this.supplierMaterialRepository = supplierMaterialRepository;
        this.orderRepository = orderRepository;
        this.receiptRepository = receiptRepository;
        this.materialRepository = materialRepository;
        this.movementService = movementService;
        this.eventPublisher = eventPublisher;
    }

    public Supplier createSupplier(UUID clinicId, CreateSupplierCommand command) {
        requireClinic(clinicId);
        String name = requireText(command.name(), "El nombre del proveedor es obligatorio.");
        LocalDateTime now = LocalDateTime.now();
        return supplierRepository.save(Supplier.builder()
                .id(UUID.randomUUID())
                .clinicId(clinicId)
                .name(name)
                .contactName(normalize(command.contactName()))
                .phone(normalize(command.phone()))
                .email(normalizeEmail(command.email()))
                .taxId(normalize(command.taxId()))
                .notes(normalize(command.notes()))
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    public Supplier updateSupplier(UUID clinicId, UUID supplierId, UpdateSupplierCommand command) {
        Supplier supplier = requireSupplier(clinicId, supplierId);
        supplier.setName(requireText(command.name(), "El nombre del proveedor es obligatorio."));
        supplier.setContactName(normalize(command.contactName()));
        supplier.setPhone(normalize(command.phone()));
        supplier.setEmail(normalizeEmail(command.email()));
        supplier.setTaxId(normalize(command.taxId()));
        supplier.setNotes(normalize(command.notes()));
        supplier.setActive(command.active());
        supplier.setUpdatedAt(LocalDateTime.now());
        return supplierRepository.save(supplier);
    }

    public List<Supplier> listSuppliers(UUID clinicId) {
        requireClinic(clinicId);
        return supplierRepository.findByClinicId(clinicId);
    }

    public List<SupplierMaterial> listSupplierMaterials(UUID clinicId, UUID supplierId) {
        requireSupplier(clinicId, supplierId);
        return supplierMaterialRepository.findActiveBySupplierIdAndClinicId(supplierId, clinicId);
    }

    public SupplierMaterial addSupplierMaterial(UUID clinicId, UUID supplierId, AddSupplierMaterialCommand command) {
        requireSupplier(clinicId, supplierId);
        if (command.materialId() == null) {
            throw new IllegalArgumentException("Selecciona un material.");
        }
        Material material = materialRepository.findByIdAndClinicId(command.materialId(), clinicId)
                .orElseThrow(() -> new IllegalArgumentException("El material no existe en esta clínica."));
        if (!material.isActive()) {
            throw new IllegalStateException("No se puede asociar un material inactivo.");
        }
        BigDecimal unitCost = command.supplierUnitCost() != null
                ? command.supplierUnitCost()
                : material.getUnitCost();
        validatePositive(unitCost, "El costo del proveedor debe ser mayor a cero.");

        SupplierMaterial supplierMaterial = supplierMaterialRepository
                .findBySupplierIdAndMaterialId(supplierId, material.getId())
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    return SupplierMaterial.builder()
                            .id(UUID.randomUUID())
                            .clinicId(clinicId)
                            .supplierId(supplierId)
                            .materialId(material.getId())
                            .materialName(material.getName())
                            .unitOfMeasure(material.getUnitOfMeasure())
                            .receiptCount(0)
                            .active(true)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                });
        supplierMaterial.activate(unitCost);
        supplierMaterial.setMaterialName(material.getName());
        supplierMaterial.setUnitOfMeasure(material.getUnitOfMeasure());
        return supplierMaterialRepository.save(supplierMaterial);
    }

    public void removeSupplierMaterial(UUID clinicId, UUID supplierId, UUID materialId) {
        requireSupplier(clinicId, supplierId);
        SupplierMaterial supplierMaterial = supplierMaterialRepository
                .findBySupplierIdAndMaterialId(supplierId, materialId)
                .filter(item -> item.getClinicId().equals(clinicId))
                .orElseThrow(() -> new IllegalArgumentException("El material no está asociado a este proveedor."));
        supplierMaterial.deactivate();
        supplierMaterialRepository.save(supplierMaterial);
    }

    public PurchaseOrder createPurchaseOrder(UUID clinicId, CreatePurchaseOrderCommand command) {
        requireClinic(clinicId);
        Supplier supplier = requireSupplier(clinicId, command.supplierId());
        if (!supplier.isActive()) {
            throw new IllegalStateException("No se puede crear una orden para un proveedor inactivo.");
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new IllegalArgumentException("La orden debe incluir al menos un material.");
        }

        LocalDate orderDate = command.orderDate() != null ? command.orderDate() : LocalDate.now();
        if (command.expectedDate() != null && command.expectedDate().isBefore(orderDate)) {
            throw new IllegalArgumentException("La fecha estimada de entrega no puede ser anterior a la orden.");
        }

        UUID orderId = UUID.randomUUID();
        Set<UUID> materialIds = new HashSet<>();
        List<PurchaseOrderLine> lines = new ArrayList<>();
        int lineOrder = 0;
        for (CreatePurchaseOrderLineCommand lineCommand : command.lines()) {
            if (lineCommand == null || lineCommand.materialId() == null) {
                throw new IllegalArgumentException("Todas las partidas deben indicar un material.");
            }
            if (!materialIds.add(lineCommand.materialId())) {
                throw new IllegalArgumentException("Un material no puede repetirse en la misma orden.");
            }
            validatePositive(lineCommand.quantity(), "La cantidad solicitada debe ser mayor a cero.");
            validatePositive(lineCommand.unitCost(), "El costo unitario debe ser mayor a cero.");
            Material material = materialRepository.findByIdAndClinicId(lineCommand.materialId(), clinicId)
                    .orElseThrow(() -> new IllegalArgumentException("Uno de los materiales no existe en esta clínica."));
            if (!material.isActive()) {
                throw new IllegalStateException("El material '" + material.getName() + "' está inactivo.");
            }
            lines.add(PurchaseOrderLine.builder()
                    .id(UUID.randomUUID())
                    .purchaseOrderId(orderId)
                    .materialId(material.getId())
                    .materialName(material.getName())
                    .unitOfMeasure(material.getUnitOfMeasure())
                    .orderedQuantity(lineCommand.quantity())
                    .receivedQuantity(BigDecimal.ZERO)
                    .unitCost(lineCommand.unitCost())
                    .lineOrder(lineOrder++)
                    .build());
        }

        LocalDateTime now = LocalDateTime.now();
        String suffix = orderId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        PurchaseOrder order = PurchaseOrder.builder()
                .id(orderId)
                .clinicId(clinicId)
                .supplierId(supplier.getId())
                .supplierName(supplier.getName())
                .folio("OC-" + orderDate.toString().replace("-", "") + "-" + suffix)
                .status(PurchaseOrderStatus.DRAFT)
                .orderDate(orderDate)
                .expectedDate(command.expectedDate())
                .notes(normalize(command.notes()))
                .bankAccountId(command.bankAccountId())
                .lines(lines)
                .createdAt(now)
                .updatedAt(now)
                .build();
        PurchaseOrder savedOrder = orderRepository.save(order);

        if (savedOrder.getBankAccountId() != null) {
            BigDecimal totalAmount = savedOrder.total();
            eventPublisher.publish(
                    DomainEventRoutingKeys.PURCHASE_ORDER_CREATED,
                    new PurchaseOrderCreatedEvent(
                            UUID.randomUUID(),
                            savedOrder.getClinicId(),
                            savedOrder.getId(),
                            savedOrder.getFolio(),
                            savedOrder.getSupplierName(),
                            totalAmount,
                            savedOrder.getBankAccountId(),
                            now
                    )
            );
        }

        return savedOrder;
    }

    public PurchaseOrder getPurchaseOrder(UUID clinicId, UUID orderId) {
        return orderRepository.findByIdAndClinicId(orderId, clinicId)
                .orElseThrow(() -> new IllegalArgumentException("La orden de compra no existe."));
    }

    public List<PurchaseOrder> listPurchaseOrders(UUID clinicId) {
        requireClinic(clinicId);
        return orderRepository.findByClinicId(clinicId);
    }

    public PurchaseOrder markPurchaseOrderOrdered(UUID clinicId, UUID orderId) {
        PurchaseOrder order = requireOrderForUpdate(clinicId, orderId);
        order.markOrdered();
        return orderRepository.save(order);
    }

    public PurchaseOrder cancelPurchaseOrder(UUID clinicId, UUID orderId) {
        PurchaseOrder order = requireOrderForUpdate(clinicId, orderId);
        order.cancel();
        return orderRepository.save(order);
    }

    public PurchaseReceipt receivePurchaseOrder(UUID clinicId, UUID orderId, ReceivePurchaseOrderCommand command) {
        PurchaseOrder order = requireOrderForUpdate(clinicId, orderId);
        if (order.getStatus() != PurchaseOrderStatus.ORDERED
                && order.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new IllegalStateException("Solo se pueden recibir órdenes enviadas o parcialmente recibidas.");
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos una partida para recibir.");
        }

        UUID receiptId = UUID.randomUUID();
        LocalDateTime receivedAt = command.receivedAt() != null ? command.receivedAt() : LocalDateTime.now();
        Set<UUID> receivedLineIds = new HashSet<>();
        List<PurchaseReceiptLine> receiptLines = new ArrayList<>();

        for (ReceivePurchaseOrderLineCommand lineCommand : command.lines()) {
            if (lineCommand == null || lineCommand.purchaseOrderLineId() == null) {
                throw new IllegalArgumentException("Todas las recepciones deben indicar una partida.");
            }
            if (!receivedLineIds.add(lineCommand.purchaseOrderLineId())) {
                throw new IllegalArgumentException("Una partida no puede repetirse en la misma recepción.");
            }
            validatePositive(lineCommand.quantity(), "La cantidad recibida debe ser mayor a cero.");
            validatePositive(lineCommand.unitCost(), "El costo recibido debe ser mayor a cero.");

            PurchaseOrderLine orderLine = order.getLines().stream()
                    .filter(line -> line.getId().equals(lineCommand.purchaseOrderLineId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Una partida no pertenece a esta orden."));
            Material material = materialRepository.findByIdAndClinicId(orderLine.getMaterialId(), clinicId)
                    .orElseThrow(() -> new IllegalArgumentException("El material de la partida ya no existe."));
            if (material.isTracksBatches()) {
                requireText(lineCommand.lotNumber(), "El lote es obligatorio para " + material.getName() + ".");
                if (lineCommand.expirationDate() == null) {
                    throw new IllegalArgumentException("La caducidad es obligatoria para " + material.getName() + ".");
                }
            }

            orderLine.receive(lineCommand.quantity());
            InventoryMovement movement = movementService.registerPurchaseReceipt(
                    clinicId,
                    material.getId(),
                    lineCommand.quantity(),
                    receivedAt,
                    lineCommand.unitCost(),
                    normalize(lineCommand.lotNumber()),
                    lineCommand.expirationDate(),
                    order.getId(),
                    "Recepción " + order.getFolio()
            );
            updateSupplierMaterialHistory(order, material, lineCommand.unitCost(), receivedAt);
            receiptLines.add(PurchaseReceiptLine.builder()
                    .id(UUID.randomUUID())
                    .receiptId(receiptId)
                    .purchaseOrderLineId(orderLine.getId())
                    .materialId(material.getId())
                    .materialName(material.getName())
                    .quantity(lineCommand.quantity())
                    .unitCost(lineCommand.unitCost())
                    .lotNumber(normalize(lineCommand.lotNumber()))
                    .expirationDate(lineCommand.expirationDate())
                    .inventoryMovementId(movement.getId())
                    .build());
        }

        order.refreshReceptionStatus();
        orderRepository.save(order);
        PurchaseReceipt receipt = PurchaseReceipt.builder()
                .id(receiptId)
                .clinicId(clinicId)
                .purchaseOrderId(orderId)
                .receivedAt(receivedAt)
                .notes(normalize(command.notes()))
                .lines(receiptLines)
                .createdAt(LocalDateTime.now())
                .build();
        return receiptRepository.save(receipt);
    }

    public List<PurchaseReceipt> listReceipts(UUID clinicId, UUID orderId) {
        getPurchaseOrder(clinicId, orderId);
        return receiptRepository.findByPurchaseOrderId(orderId);
    }

    private void updateSupplierMaterialHistory(
            PurchaseOrder order,
            Material material,
            BigDecimal unitCost,
            LocalDateTime suppliedAt) {
        SupplierMaterial supplierMaterial = supplierMaterialRepository
                .findBySupplierIdAndMaterialId(order.getSupplierId(), material.getId())
                .orElseGet(() -> SupplierMaterial.builder()
                        .id(UUID.randomUUID())
                        .clinicId(order.getClinicId())
                        .supplierId(order.getSupplierId())
                        .materialId(material.getId())
                        .materialName(material.getName())
                        .unitOfMeasure(material.getUnitOfMeasure())
                        .receiptCount(0)
                        .active(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build());
        supplierMaterial.setMaterialName(material.getName());
        supplierMaterial.setUnitOfMeasure(material.getUnitOfMeasure());
        supplierMaterial.markSupplied(unitCost, suppliedAt);
        supplierMaterialRepository.save(supplierMaterial);
    }

    private Supplier requireSupplier(UUID clinicId, UUID supplierId) {
        if (supplierId == null) {
            throw new IllegalArgumentException("Selecciona un proveedor.");
        }
        return supplierRepository.findByIdAndClinicId(supplierId, clinicId)
                .orElseThrow(() -> new IllegalArgumentException("El proveedor no existe."));
    }

    private PurchaseOrder requireOrderForUpdate(UUID clinicId, UUID orderId) {
        return orderRepository.findByIdAndClinicIdForUpdate(orderId, clinicId)
                .orElseThrow(() -> new IllegalArgumentException("La orden de compra no existe."));
    }

    private void requireClinic(UUID clinicId) {
        if (clinicId == null) {
            throw new IllegalArgumentException("La clínica es obligatoria.");
        }
    }

    private String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeEmail(String value) {
        String email = normalize(value);
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    private void validatePositive(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
