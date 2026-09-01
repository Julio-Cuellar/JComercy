package com.jclinical.inventory.infra.adapters.out.persistence;

import com.jclinical.inventory.domain.model.PurchaseReceipt;
import com.jclinical.inventory.domain.model.PurchaseReceiptLine;
import com.jclinical.inventory.domain.ports.out.PurchaseReceiptRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SqlPurchaseReceiptRepository implements PurchaseReceiptRepositoryPort {
    private final SpringDataPurchaseReceiptRepository receiptRepository;
    private final SpringDataPurchaseReceiptLineRepository lineRepository;

    @Override
    public PurchaseReceipt save(PurchaseReceipt receipt) {
        PurchaseReceiptEntity saved = receiptRepository.save(toEntity(receipt));
        lineRepository.saveAll(receipt.getLines().stream().map(this::toEntity).toList());
        return toDomain(saved, receipt.getLines());
    }

    @Override
    public List<PurchaseReceipt> findByPurchaseOrderId(UUID purchaseOrderId) {
        return receiptRepository.findByPurchaseOrderIdOrderByReceivedAtDesc(purchaseOrderId).stream()
                .map(entity -> toDomain(entity, lineRepository.findByReceiptId(entity.getId()).stream()
                        .map(this::toDomain)
                        .toList()))
                .toList();
    }

    private PurchaseReceiptEntity toEntity(PurchaseReceipt receipt) {
        return PurchaseReceiptEntity.builder()
                .id(receipt.getId())
                .clinicId(receipt.getClinicId())
                .purchaseOrderId(receipt.getPurchaseOrderId())
                .receivedAt(receipt.getReceivedAt())
                .notes(receipt.getNotes())
                .createdAt(receipt.getCreatedAt())
                .build();
    }

    private PurchaseReceiptLineEntity toEntity(PurchaseReceiptLine line) {
        return PurchaseReceiptLineEntity.builder()
                .id(line.getId())
                .receiptId(line.getReceiptId())
                .purchaseOrderLineId(line.getPurchaseOrderLineId())
                .materialId(line.getMaterialId())
                .materialName(line.getMaterialName())
                .quantity(line.getQuantity())
                .unitCost(line.getUnitCost())
                .lotNumber(line.getLotNumber())
                .expirationDate(line.getExpirationDate())
                .inventoryMovementId(line.getInventoryMovementId())
                .build();
    }

    private PurchaseReceipt toDomain(PurchaseReceiptEntity entity, List<PurchaseReceiptLine> lines) {
        return PurchaseReceipt.builder()
                .id(entity.getId())
                .clinicId(entity.getClinicId())
                .purchaseOrderId(entity.getPurchaseOrderId())
                .receivedAt(entity.getReceivedAt())
                .notes(entity.getNotes())
                .lines(lines)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private PurchaseReceiptLine toDomain(PurchaseReceiptLineEntity entity) {
        return PurchaseReceiptLine.builder()
                .id(entity.getId())
                .receiptId(entity.getReceiptId())
                .purchaseOrderLineId(entity.getPurchaseOrderLineId())
                .materialId(entity.getMaterialId())
                .materialName(entity.getMaterialName())
                .quantity(entity.getQuantity())
                .unitCost(entity.getUnitCost())
                .lotNumber(entity.getLotNumber())
                .expirationDate(entity.getExpirationDate())
                .inventoryMovementId(entity.getInventoryMovementId())
                .build();
    }
}
