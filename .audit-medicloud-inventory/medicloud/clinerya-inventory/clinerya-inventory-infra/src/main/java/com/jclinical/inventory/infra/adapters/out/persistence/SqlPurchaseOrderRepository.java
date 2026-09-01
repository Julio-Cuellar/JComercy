package com.jclinical.inventory.infra.adapters.out.persistence;

import com.jclinical.inventory.domain.model.PurchaseOrder;
import com.jclinical.inventory.domain.model.PurchaseOrderLine;
import com.jclinical.inventory.domain.ports.out.PurchaseOrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SqlPurchaseOrderRepository implements PurchaseOrderRepositoryPort {
    private final SpringDataPurchaseOrderRepository orderRepository;
    private final SpringDataPurchaseOrderLineRepository lineRepository;

    @Override
    public PurchaseOrder save(PurchaseOrder order) {
        PurchaseOrderEntity saved = orderRepository.save(toEntity(order));
        lineRepository.saveAll(order.getLines().stream().map(this::toEntity).toList());
        return toDomain(saved, order.getLines());
    }

    @Override
    public Optional<PurchaseOrder> findByIdAndClinicId(UUID orderId, UUID clinicId) {
        return orderRepository.findByIdAndClinicId(orderId, clinicId).map(this::loadAggregate);
    }

    @Override
    public Optional<PurchaseOrder> findByIdAndClinicIdForUpdate(UUID orderId, UUID clinicId) {
        return orderRepository.findForUpdate(orderId, clinicId).map(this::loadAggregate);
    }

    @Override
    public List<PurchaseOrder> findByClinicId(UUID clinicId) {
        return orderRepository.findByClinicIdOrderByCreatedAtDesc(clinicId).stream()
                .map(this::loadAggregate)
                .toList();
    }

    private PurchaseOrder loadAggregate(PurchaseOrderEntity entity) {
        List<PurchaseOrderLine> lines = lineRepository.findByPurchaseOrderIdOrderByLineOrderAsc(entity.getId())
                .stream()
                .map(this::toDomain)
                .toList();
        return toDomain(entity, lines);
    }

    private PurchaseOrderEntity toEntity(PurchaseOrder order) {
        return PurchaseOrderEntity.builder()
                .id(order.getId())
                .clinicId(order.getClinicId())
                .supplierId(order.getSupplierId())
                .supplierName(order.getSupplierName())
                .folio(order.getFolio())
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                .expectedDate(order.getExpectedDate())
                .notes(order.getNotes())
                .bankAccountId(order.getBankAccountId())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private PurchaseOrderLineEntity toEntity(PurchaseOrderLine line) {
        return PurchaseOrderLineEntity.builder()
                .id(line.getId())
                .purchaseOrderId(line.getPurchaseOrderId())
                .materialId(line.getMaterialId())
                .materialName(line.getMaterialName())
                .unitOfMeasure(line.getUnitOfMeasure())
                .orderedQuantity(line.getOrderedQuantity())
                .receivedQuantity(line.getReceivedQuantity())
                .unitCost(line.getUnitCost())
                .lineOrder(line.getLineOrder())
                .build();
    }

    private PurchaseOrder toDomain(PurchaseOrderEntity entity, List<PurchaseOrderLine> lines) {
        return PurchaseOrder.builder()
                .id(entity.getId())
                .clinicId(entity.getClinicId())
                .supplierId(entity.getSupplierId())
                .supplierName(entity.getSupplierName())
                .folio(entity.getFolio())
                .status(entity.getStatus())
                .orderDate(entity.getOrderDate())
                .expectedDate(entity.getExpectedDate())
                .notes(entity.getNotes())
                .bankAccountId(entity.getBankAccountId())
                .lines(lines)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private PurchaseOrderLine toDomain(PurchaseOrderLineEntity entity) {
        return PurchaseOrderLine.builder()
                .id(entity.getId())
                .purchaseOrderId(entity.getPurchaseOrderId())
                .materialId(entity.getMaterialId())
                .materialName(entity.getMaterialName())
                .unitOfMeasure(entity.getUnitOfMeasure())
                .orderedQuantity(entity.getOrderedQuantity())
                .receivedQuantity(entity.getReceivedQuantity())
                .unitCost(entity.getUnitCost())
                .lineOrder(entity.getLineOrder())
                .build();
    }
}
