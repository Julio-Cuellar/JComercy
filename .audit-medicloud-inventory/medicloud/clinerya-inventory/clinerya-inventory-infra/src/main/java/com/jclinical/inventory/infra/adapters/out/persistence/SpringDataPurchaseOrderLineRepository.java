package com.jclinical.inventory.infra.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataPurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLineEntity, UUID> {
    List<PurchaseOrderLineEntity> findByPurchaseOrderIdOrderByLineOrderAsc(UUID purchaseOrderId);
}
