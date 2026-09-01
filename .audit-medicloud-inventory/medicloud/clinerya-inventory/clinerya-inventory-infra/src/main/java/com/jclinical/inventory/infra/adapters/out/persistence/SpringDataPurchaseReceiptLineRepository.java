package com.jclinical.inventory.infra.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataPurchaseReceiptLineRepository extends JpaRepository<PurchaseReceiptLineEntity, UUID> {
    List<PurchaseReceiptLineEntity> findByReceiptId(UUID receiptId);
}
