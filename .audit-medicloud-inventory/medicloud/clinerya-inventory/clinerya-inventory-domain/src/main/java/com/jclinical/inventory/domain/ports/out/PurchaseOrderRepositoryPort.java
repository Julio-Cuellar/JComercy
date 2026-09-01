package com.jclinical.inventory.domain.ports.out;

import com.jclinical.inventory.domain.model.PurchaseOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepositoryPort {
    PurchaseOrder save(PurchaseOrder order);

    Optional<PurchaseOrder> findByIdAndClinicId(UUID orderId, UUID clinicId);

    Optional<PurchaseOrder> findByIdAndClinicIdForUpdate(UUID orderId, UUID clinicId);

    List<PurchaseOrder> findByClinicId(UUID clinicId);
}
