package com.jclinical.inventory.infra.adapters.in.web;

import com.jclinical.inventory.domain.model.PurchaseOrder;
import com.jclinical.inventory.domain.model.PurchaseOrderLine;
import com.jclinical.inventory.domain.model.PurchaseReceipt;
import com.jclinical.inventory.domain.model.PurchaseReceiptLine;
import com.jclinical.inventory.domain.model.Supplier;
import com.jclinical.inventory.domain.model.SupplierMaterial;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.AddSupplierMaterialCommand;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.CreatePurchaseOrderCommand;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.CreatePurchaseOrderLineCommand;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.CreateSupplierCommand;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.ReceivePurchaseOrderCommand;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.ReceivePurchaseOrderLineCommand;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase.UpdateSupplierCommand;
import com.jclinical.inventory.infra.adapters.in.web.dto.PurchaseOrderRequest;
import com.jclinical.inventory.infra.adapters.in.web.dto.PurchaseOrderResponse;
import com.jclinical.inventory.infra.adapters.in.web.dto.PurchaseReceiptRequest;
import com.jclinical.inventory.infra.adapters.in.web.dto.PurchaseReceiptResponse;
import com.jclinical.inventory.infra.adapters.in.web.dto.SupplierRequest;
import com.jclinical.inventory.infra.adapters.in.web.dto.SupplierResponse;
import com.jclinical.inventory.infra.adapters.in.web.dto.SupplierMaterialRequest;
import com.jclinical.inventory.infra.adapters.in.web.dto.SupplierMaterialResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinics/{clinicId}")
@RequiredArgsConstructor
public class PurchasingController {
    private final ManagePurchasingUseCase purchasingUseCase;

    @GetMapping("/suppliers")
    public ResponseEntity<List<SupplierResponse>> listSuppliers(@PathVariable UUID clinicId) {
        return ResponseEntity.ok(purchasingUseCase.listSuppliers(clinicId).stream().map(this::toResponse).toList());
    }

    @PostMapping("/suppliers")
    public ResponseEntity<SupplierResponse> createSupplier(
            @PathVariable UUID clinicId,
            @RequestBody SupplierRequest request) {
        Supplier supplier = purchasingUseCase.createSupplier(clinicId, new CreateSupplierCommand(
                request.name(),
                request.contactName(),
                request.phone(),
                request.email(),
                request.taxId(),
                request.notes()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(supplier));
    }

    @PutMapping("/suppliers/{supplierId}")
    public ResponseEntity<SupplierResponse> updateSupplier(
            @PathVariable UUID clinicId,
            @PathVariable UUID supplierId,
            @RequestBody SupplierRequest request) {
        Supplier supplier = purchasingUseCase.updateSupplier(clinicId, supplierId, new UpdateSupplierCommand(
                request.name(),
                request.contactName(),
                request.phone(),
                request.email(),
                request.taxId(),
                request.notes(),
                request.active() == null || request.active()
        ));
        return ResponseEntity.ok(toResponse(supplier));
    }

    @GetMapping("/suppliers/{supplierId}/materials")
    public ResponseEntity<List<SupplierMaterialResponse>> listSupplierMaterials(
            @PathVariable UUID clinicId,
            @PathVariable UUID supplierId) {
        return ResponseEntity.ok(purchasingUseCase.listSupplierMaterials(clinicId, supplierId).stream()
                .map(this::toResponse)
                .toList());
    }

    @PostMapping("/suppliers/{supplierId}/materials")
    public ResponseEntity<SupplierMaterialResponse> addSupplierMaterial(
            @PathVariable UUID clinicId,
            @PathVariable UUID supplierId,
            @RequestBody SupplierMaterialRequest request) {
        SupplierMaterial supplierMaterial = purchasingUseCase.addSupplierMaterial(
                clinicId,
                supplierId,
                new AddSupplierMaterialCommand(request.materialId(), request.supplierUnitCost())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(supplierMaterial));
    }

    @DeleteMapping("/suppliers/{supplierId}/materials/{materialId}")
    public ResponseEntity<Void> removeSupplierMaterial(
            @PathVariable UUID clinicId,
            @PathVariable UUID supplierId,
            @PathVariable UUID materialId) {
        purchasingUseCase.removeSupplierMaterial(clinicId, supplierId, materialId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/purchase-orders")
    public ResponseEntity<List<PurchaseOrderResponse>> listPurchaseOrders(@PathVariable UUID clinicId) {
        return ResponseEntity.ok(purchasingUseCase.listPurchaseOrders(clinicId).stream()
                .map(this::toResponse)
                .toList());
    }

    @GetMapping("/purchase-orders/{orderId}")
    public ResponseEntity<PurchaseOrderResponse> getPurchaseOrder(
            @PathVariable UUID clinicId,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(toResponse(purchasingUseCase.getPurchaseOrder(clinicId, orderId)));
    }

    @PostMapping("/purchase-orders")
    public ResponseEntity<PurchaseOrderResponse> createPurchaseOrder(
            @PathVariable UUID clinicId,
            @RequestBody PurchaseOrderRequest request) {
        List<CreatePurchaseOrderLineCommand> lines = request.lines() == null ? List.of() : request.lines().stream()
                .map(line -> new CreatePurchaseOrderLineCommand(line.materialId(), line.quantity(), line.unitCost()))
                .toList();
        PurchaseOrder order = purchasingUseCase.createPurchaseOrder(clinicId, new CreatePurchaseOrderCommand(
                request.supplierId(),
                request.orderDate(),
                request.expectedDate(),
                request.notes(),
                request.bankAccountId(),
                lines
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(order));
    }

    @PatchMapping("/purchase-orders/{orderId}/ordered")
    public ResponseEntity<PurchaseOrderResponse> markOrdered(
            @PathVariable UUID clinicId,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(toResponse(purchasingUseCase.markPurchaseOrderOrdered(clinicId, orderId)));
    }

    @PatchMapping("/purchase-orders/{orderId}/cancel")
    public ResponseEntity<PurchaseOrderResponse> cancel(
            @PathVariable UUID clinicId,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(toResponse(purchasingUseCase.cancelPurchaseOrder(clinicId, orderId)));
    }

    @GetMapping("/purchase-orders/{orderId}/receipts")
    public ResponseEntity<List<PurchaseReceiptResponse>> listReceipts(
            @PathVariable UUID clinicId,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(purchasingUseCase.listReceipts(clinicId, orderId).stream()
                .map(this::toResponse)
                .toList());
    }

    @PostMapping("/purchase-orders/{orderId}/receipts")
    public ResponseEntity<PurchaseReceiptResponse> receive(
            @PathVariable UUID clinicId,
            @PathVariable UUID orderId,
            @RequestBody PurchaseReceiptRequest request) {
        List<ReceivePurchaseOrderLineCommand> lines = request.lines() == null ? List.of() : request.lines().stream()
                .map(line -> new ReceivePurchaseOrderLineCommand(
                        line.purchaseOrderLineId(),
                        line.quantity(),
                        line.unitCost(),
                        line.lotNumber(),
                        line.expirationDate()
                ))
                .toList();
        PurchaseReceipt receipt = purchasingUseCase.receivePurchaseOrder(clinicId, orderId, new ReceivePurchaseOrderCommand(
                request.receivedAt(),
                request.notes(),
                lines
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(receipt));
    }

    private SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getClinicId(),
                supplier.getName(),
                supplier.getContactName(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getTaxId(),
                supplier.getNotes(),
                supplier.isActive(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt()
        );
    }

    private SupplierMaterialResponse toResponse(SupplierMaterial item) {
        return new SupplierMaterialResponse(
                item.getId(),
                item.getClinicId(),
                item.getSupplierId(),
                item.getMaterialId(),
                item.getMaterialName(),
                item.getUnitOfMeasure(),
                item.getSupplierUnitCost(),
                item.getLastSuppliedAt(),
                item.getReceiptCount(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder order) {
        List<PurchaseOrderResponse.Line> lines = order.getLines().stream().map(this::toResponse).toList();
        return new PurchaseOrderResponse(
                order.getId(),
                order.getClinicId(),
                order.getSupplierId(),
                order.getSupplierName(),
                order.getFolio(),
                order.getStatus(),
                order.getOrderDate(),
                order.getExpectedDate(),
                order.getNotes(),
                order.getBankAccountId(),
                order.total(),
                lines,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private PurchaseOrderResponse.Line toResponse(PurchaseOrderLine line) {
        return new PurchaseOrderResponse.Line(
                line.getId(),
                line.getMaterialId(),
                line.getMaterialName(),
                line.getUnitOfMeasure(),
                line.getOrderedQuantity(),
                line.getReceivedQuantity(),
                line.remainingQuantity(),
                line.getUnitCost(),
                line.subtotal()
        );
    }

    private PurchaseReceiptResponse toResponse(PurchaseReceipt receipt) {
        List<PurchaseReceiptResponse.Line> lines = receipt.getLines().stream().map(this::toResponse).toList();
        return new PurchaseReceiptResponse(
                receipt.getId(),
                receipt.getClinicId(),
                receipt.getPurchaseOrderId(),
                receipt.getReceivedAt(),
                receipt.getNotes(),
                receipt.total(),
                lines,
                receipt.getCreatedAt()
        );
    }

    private PurchaseReceiptResponse.Line toResponse(PurchaseReceiptLine line) {
        return new PurchaseReceiptResponse.Line(
                line.getId(),
                line.getPurchaseOrderLineId(),
                line.getMaterialId(),
                line.getMaterialName(),
                line.getQuantity(),
                line.getUnitCost(),
                line.getLotNumber(),
                line.getExpirationDate(),
                line.getInventoryMovementId(),
                line.subtotal()
        );
    }
}
