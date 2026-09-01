package com.jclinical.inventory.infra.config;

import com.jclinical.inventory.domain.model.PurchaseOrder;
import com.jclinical.inventory.domain.model.PurchaseReceipt;
import com.jclinical.inventory.domain.model.Supplier;
import com.jclinical.inventory.domain.model.SupplierMaterial;
import com.jclinical.inventory.domain.ports.in.ManagePurchasingUseCase;
import com.jclinical.inventory.domain.service.PurchasingService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Primary
@RequiredArgsConstructor
public class TransactionalPurchasingUseCase implements ManagePurchasingUseCase {
    private final PurchasingService service;

    @Override
    @Transactional
    public Supplier createSupplier(UUID clinicId, CreateSupplierCommand command) {
        return service.createSupplier(clinicId, command);
    }

    @Override
    @Transactional
    public Supplier updateSupplier(UUID clinicId, UUID supplierId, UpdateSupplierCommand command) {
        return service.updateSupplier(clinicId, supplierId, command);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Supplier> listSuppliers(UUID clinicId) {
        return service.listSuppliers(clinicId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierMaterial> listSupplierMaterials(UUID clinicId, UUID supplierId) {
        return service.listSupplierMaterials(clinicId, supplierId);
    }

    @Override
    @Transactional
    public SupplierMaterial addSupplierMaterial(UUID clinicId, UUID supplierId, AddSupplierMaterialCommand command) {
        return service.addSupplierMaterial(clinicId, supplierId, command);
    }

    @Override
    @Transactional
    public void removeSupplierMaterial(UUID clinicId, UUID supplierId, UUID materialId) {
        service.removeSupplierMaterial(clinicId, supplierId, materialId);
    }

    @Override
    @Transactional
    public PurchaseOrder createPurchaseOrder(UUID clinicId, CreatePurchaseOrderCommand command) {
        return service.createPurchaseOrder(clinicId, command);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrder getPurchaseOrder(UUID clinicId, UUID orderId) {
        return service.getPurchaseOrder(clinicId, orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrder> listPurchaseOrders(UUID clinicId) {
        return service.listPurchaseOrders(clinicId);
    }

    @Override
    @Transactional
    public PurchaseOrder markPurchaseOrderOrdered(UUID clinicId, UUID orderId) {
        return service.markPurchaseOrderOrdered(clinicId, orderId);
    }

    @Override
    @Transactional
    public PurchaseOrder cancelPurchaseOrder(UUID clinicId, UUID orderId) {
        return service.cancelPurchaseOrder(clinicId, orderId);
    }

    @Override
    @Transactional
    public PurchaseReceipt receivePurchaseOrder(UUID clinicId, UUID orderId, ReceivePurchaseOrderCommand command) {
        return service.receivePurchaseOrder(clinicId, orderId, command);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseReceipt> listReceipts(UUID clinicId, UUID orderId) {
        return service.listReceipts(clinicId, orderId);
    }
}
