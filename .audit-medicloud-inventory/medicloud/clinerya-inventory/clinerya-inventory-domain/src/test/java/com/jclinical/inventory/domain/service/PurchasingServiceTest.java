package com.jclinical.inventory.domain.service;

import com.jclinical.inventory.domain.model.InventoryBatch;
import com.jclinical.inventory.domain.model.InventoryMovement;
import com.jclinical.inventory.domain.model.Material;
import com.jclinical.inventory.domain.model.MovementType;
import com.jclinical.inventory.domain.model.PurchaseOrder;
import com.jclinical.inventory.domain.model.PurchaseOrderStatus;
import com.jclinical.inventory.domain.model.PurchaseReceipt;
import com.jclinical.inventory.domain.model.Supplier;
import com.jclinical.inventory.domain.model.SupplierMaterial;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.CreatePurchaseOrderCommand;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.CreatePurchaseOrderLineCommand;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.ReceivePurchaseOrderCommand;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.ReceivePurchaseOrderLineCommand;
import com.jclinical.inventory.domain.ports.out.InventoryBatchRepositoryPort;
import com.jclinical.inventory.domain.ports.out.InventoryMovementRepositoryPort;
import com.jclinical.inventory.domain.ports.out.MaterialRepositoryPort;
import com.jclinical.inventory.domain.ports.out.PurchaseOrderRepositoryPort;
import com.jclinical.inventory.domain.ports.out.PurchaseReceiptRepositoryPort;
import com.jclinical.inventory.domain.ports.out.SupplierRepositoryPort;
import com.jclinical.inventory.domain.ports.out.SupplierMaterialRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PurchasingServiceTest {

    @Test
    void partialReceiptUpdatesStockWeightedCostBatchAndKardex() {
        UUID clinicId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();

        InMemorySupplierRepository suppliers = new InMemorySupplierRepository();
        suppliers.save(Supplier.builder()
                .id(supplierId)
                .clinicId(clinicId)
                .name("Dental Supply")
                .active(true)
                .build());

        InMemoryMaterialRepository materials = new InMemoryMaterialRepository();
        materials.save(Material.builder()
                .id(materialId)
                .clinicId(clinicId)
                .name("Anestésico")
                .unitOfMeasure("cartucho")
                .unitCost(new BigDecimal("5.0000"))
                .currentStock(new BigDecimal("10.0000"))
                .reservedQuantity(BigDecimal.ZERO)
                .tracksBatches(true)
                .active(true)
                .build());

        InMemoryMovementRepository movements = new InMemoryMovementRepository();
        InMemoryBatchRepository batches = new InMemoryBatchRepository();
        InventoryMovementService movementService = new InventoryMovementService(
                materials,
                movements,
                batches,
                (routingKey, payload) -> { }
        );
        InMemoryPurchaseOrderRepository orders = new InMemoryPurchaseOrderRepository();
        InMemoryPurchaseReceiptRepository receipts = new InMemoryPurchaseReceiptRepository();
        InMemorySupplierMaterialRepository supplierMaterials = new InMemorySupplierMaterialRepository();
        PurchasingService service = new PurchasingService(
                suppliers,
                supplierMaterials,
                orders,
                receipts,
                materials,
                movementService,
                (routingKey, payload) -> { }
        );

        PurchaseOrder order = service.createPurchaseOrder(clinicId, new CreatePurchaseOrderCommand(
                supplierId,
                LocalDate.now(),
                LocalDate.now().plusDays(2),
                null,
                null,
                List.of(new CreatePurchaseOrderLineCommand(
                        materialId,
                        new BigDecimal("4.0000"),
                        new BigDecimal("9.0000")
                ))
        ));
        service.markPurchaseOrderOrdered(clinicId, order.getId());

        PurchaseReceipt receipt = service.receivePurchaseOrder(clinicId, order.getId(), new ReceivePurchaseOrderCommand(
                null,
                "Entrega parcial",
                List.of(new ReceivePurchaseOrderLineCommand(
                        order.getLines().getFirst().getId(),
                        new BigDecimal("2.0000"),
                        new BigDecimal("9.0000"),
                        "LOT-001",
                        LocalDate.now().plusYears(1)
                ))
        ));

        PurchaseOrder updatedOrder = orders.findByIdAndClinicId(order.getId(), clinicId).orElseThrow();
        Material updatedMaterial = materials.findByIdAndClinicId(materialId, clinicId).orElseThrow();
        InventoryMovement movement = movements.items.getFirst();
        InventoryBatch batch = batches.items.getFirst();
        SupplierMaterial supplierMaterial = supplierMaterials
                .findBySupplierIdAndMaterialId(supplierId, materialId)
                .orElseThrow();

        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, updatedOrder.getStatus());
        assertEquals(new BigDecimal("2.0000"), updatedOrder.getLines().getFirst().getReceivedQuantity());
        assertEquals(new BigDecimal("12.0000"), updatedMaterial.getCurrentStock());
        assertEquals(new BigDecimal("5.6667"), updatedMaterial.getUnitCost());
        assertEquals("PURCHASE_ORDER", movement.getReferenceType());
        assertEquals(order.getId(), movement.getReferenceId());
        assertEquals(new BigDecimal("9.0000"), movement.getUnitCostAtMovement());
        assertEquals("LOT-001", batch.getLotNumber());
        assertEquals(new BigDecimal("2.0000"), batch.getRemainingQuantity());
        assertNotNull(receipt.getLines().getFirst().getInventoryMovementId());
        assertEquals(new BigDecimal("9.0000"), supplierMaterial.getSupplierUnitCost());
        assertEquals(1, supplierMaterial.getReceiptCount());
        assertNotNull(supplierMaterial.getLastSuppliedAt());
    }

    private static class InMemorySupplierRepository implements SupplierRepositoryPort {
        private final Map<UUID, Supplier> items = new HashMap<>();
        public Supplier save(Supplier supplier) { items.put(supplier.getId(), supplier); return supplier; }
        public Optional<Supplier> findByIdAndClinicId(UUID id, UUID clinicId) { return Optional.ofNullable(items.get(id)).filter(item -> item.getClinicId().equals(clinicId)); }
        public List<Supplier> findByClinicId(UUID clinicId) { return items.values().stream().filter(item -> item.getClinicId().equals(clinicId)).toList(); }
    }

    private static class InMemoryMaterialRepository implements MaterialRepositoryPort {
        private final Map<UUID, Material> items = new HashMap<>();
        public Material save(Material material) { items.put(material.getId(), material); return material; }
        public Optional<Material> findByIdAndClinicId(UUID id, UUID clinicId) { return Optional.ofNullable(items.get(id)).filter(item -> item.getClinicId().equals(clinicId)); }
        public Optional<Material> findByIdAndClinicIdForUpdate(UUID id, UUID clinicId) { return findByIdAndClinicId(id, clinicId); }
        public List<Material> findByClinicId(UUID clinicId, boolean includeInactive) { return items.values().stream().filter(item -> item.getClinicId().equals(clinicId)).toList(); }
    }

    private static class InMemoryMovementRepository implements InventoryMovementRepositoryPort {
        private final List<InventoryMovement> items = new ArrayList<>();
        public InventoryMovement save(InventoryMovement movement) { items.add(movement); return movement; }
        public List<InventoryMovement> findByMaterialIdAndClinicId(UUID materialId, UUID clinicId, int page, int size) { return items; }
        public List<InventoryMovement> findByClinicId(UUID clinicId, int page, int size) { return items; }
        public Optional<InventoryMovement> findByReferenceAndMaterial(MovementType type, String referenceType, UUID referenceId, UUID materialId) { return Optional.empty(); }
    }

    private static class InMemoryBatchRepository implements InventoryBatchRepositoryPort {
        private final List<InventoryBatch> items = new ArrayList<>();
        public InventoryBatch save(InventoryBatch batch) { items.removeIf(item -> item.getId().equals(batch.getId())); items.add(batch); return batch; }
        public Optional<InventoryBatch> findByIdAndClinicId(UUID id, UUID clinicId) { return items.stream().filter(item -> item.getId().equals(id) && item.getClinicId().equals(clinicId)).findFirst(); }
        public Optional<InventoryBatch> findByIdAndClinicIdForUpdate(UUID id, UUID clinicId) { return findByIdAndClinicId(id, clinicId); }
        public List<InventoryBatch> findByMaterialIdAndClinicId(UUID materialId, UUID clinicId) { return items.stream().filter(item -> item.getMaterialId().equals(materialId) && item.getClinicId().equals(clinicId)).toList(); }
        public Optional<InventoryBatch> findFirstAvailableForConsumption(UUID materialId, UUID clinicId, BigDecimal quantity) { return Optional.empty(); }
        public List<InventoryBatch> findAvailableForConsumptionForUpdate(UUID materialId, UUID clinicId) { return findByMaterialIdAndClinicId(materialId, clinicId); }
        public List<InventoryBatch> findExpiredWithRemainingForUpdate(UUID clinicId, LocalDate asOfDate) {
            return items.stream()
                    .filter(item -> item.getClinicId().equals(clinicId))
                    .filter(item -> item.getExpirationDate() != null && !item.getExpirationDate().isAfter(asOfDate))
                    .filter(item -> item.getRemainingQuantity() != null && item.getRemainingQuantity().signum() > 0)
                    .toList();
        }
    }

    private static class InMemoryPurchaseOrderRepository implements PurchaseOrderRepositoryPort {
        private final Map<UUID, PurchaseOrder> items = new HashMap<>();
        public PurchaseOrder save(PurchaseOrder order) { items.put(order.getId(), order); return order; }
        public Optional<PurchaseOrder> findByIdAndClinicId(UUID id, UUID clinicId) { return Optional.ofNullable(items.get(id)).filter(item -> item.getClinicId().equals(clinicId)); }
        public Optional<PurchaseOrder> findByIdAndClinicIdForUpdate(UUID id, UUID clinicId) { return findByIdAndClinicId(id, clinicId); }
        public List<PurchaseOrder> findByClinicId(UUID clinicId) { return items.values().stream().filter(item -> item.getClinicId().equals(clinicId)).toList(); }
    }

    private static class InMemoryPurchaseReceiptRepository implements PurchaseReceiptRepositoryPort {
        private final List<PurchaseReceipt> items = new ArrayList<>();
        public PurchaseReceipt save(PurchaseReceipt receipt) { items.add(receipt); return receipt; }
        public List<PurchaseReceipt> findByPurchaseOrderId(UUID orderId) { return items.stream().filter(item -> item.getPurchaseOrderId().equals(orderId)).toList(); }
    }

    private static class InMemorySupplierMaterialRepository implements SupplierMaterialRepositoryPort {
        private final Map<UUID, SupplierMaterial> items = new HashMap<>();
        public SupplierMaterial save(SupplierMaterial item) { items.put(item.getId(), item); return item; }
        public Optional<SupplierMaterial> findBySupplierIdAndMaterialId(UUID supplierId, UUID materialId) {
            return items.values().stream()
                    .filter(item -> item.getSupplierId().equals(supplierId) && item.getMaterialId().equals(materialId))
                    .findFirst();
        }
        public List<SupplierMaterial> findActiveBySupplierIdAndClinicId(UUID supplierId, UUID clinicId) {
            return items.values().stream()
                    .filter(item -> item.getSupplierId().equals(supplierId)
                            && item.getClinicId().equals(clinicId)
                            && item.isActive())
                    .toList();
        }
    }
}
