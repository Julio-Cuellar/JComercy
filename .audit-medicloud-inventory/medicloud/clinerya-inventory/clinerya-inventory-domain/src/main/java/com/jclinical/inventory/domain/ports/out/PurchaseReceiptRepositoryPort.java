package com.jclinical.inventory.domain.ports.out;

import com.jclinical.inventory.domain.model.PurchaseReceipt;

import java.util.List;
import java.util.UUID;

public interface PurchaseReceiptRepositoryPort {
    PurchaseReceipt save(PurchaseReceipt receipt);

    List<PurchaseReceipt> findByPurchaseOrderId(UUID purchaseOrderId);
}
