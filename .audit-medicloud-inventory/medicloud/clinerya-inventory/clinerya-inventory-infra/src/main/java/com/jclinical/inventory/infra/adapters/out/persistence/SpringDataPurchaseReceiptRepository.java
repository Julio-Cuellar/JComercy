package com.jclinical.inventory.infra.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataPurchaseReceiptRepository extends JpaRepository<PurchaseReceiptEntity, UUID> {
    List<PurchaseReceiptEntity> findByPurchaseOrderIdOrderByReceivedAtDesc(UUID purchaseOrderId);
}
