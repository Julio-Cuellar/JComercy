package com.jclinical.inventory.domain.service;

import com.jclinical.inventory.domain.model.InventoryBatch;
import com.jclinical.inventory.domain.model.InventoryMovement;
import com.jclinical.inventory.domain.model.Material;
import com.jclinical.inventory.domain.model.MovementType;
import com.jclinical.inventory.domain.ports.in.ManageInventoryMovementUseCase.RegisterMovementCommand;
import com.jclinical.inventory.domain.ports.in.ManageInventoryMovementUseCase.RegisterUsageExitCommand;
import com.jclinical.inventory.domain.ports.out.InventoryBatchRepositoryPort;
import com.jclinical.inventory.domain.ports.out.InventoryMovementRepositoryPort;
import com.jclinical.inventory.domain.ports.out.MaterialRepositoryPort;
import com.jclinical.core.events.DomainEventPublisherPort;
import com.jclinical.core.events.DomainEventRoutingKeys;
import com.jclinical.core.events.MermaCaducidadEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class InventoryMovementService {

    private final MaterialRepositoryPort materialRepository;
    private final InventoryMovementRepositoryPort movementRepository;
    private final InventoryBatchRepositoryPort batchRepository;
    private final DomainEventPublisherPort eventPublisher;

    public InventoryMovementService(
            MaterialRepositoryPort materialRepository,
            InventoryMovementRepositoryPort movementRepository,
            InventoryBatchRepositoryPort batchRepository,
            DomainEventPublisherPort eventPublisher) {
        this.materialRepository = materialRepository;
        this.movementRepository = movementRepository;
        this.batchRepository = batchRepository;
        this.eventPublisher = eventPublisher;
    }

    public InventoryMovement registerPurchaseEntry(UUID clinicId, UUID materialId, RegisterMovementCommand command) {
        if (command.type() != MovementType.PURCHASE_ENTRY) {
            throw new IllegalArgumentException("El tipo de movimiento debe ser PURCHASE_ENTRY.");
        }
        return registerMovement(
                clinicId,
                materialId,
                command.type(),
                command.quantity(),
                command.presentationQuantity(),
                command.movementDate(),
                null,
                null,
                command.notes(),
                command.lotNumber(),
                command.expirationDate(),
                null,
                null
        );
    }

    public InventoryMovement registerPurchaseReceipt(
            UUID clinicId,
            UUID materialId,
            BigDecimal quantity,
            LocalDateTime movementDate,
            BigDecimal unitCost,
            String lotNumber,
            LocalDate expirationDate,
            UUID purchaseOrderId,
            String notes) {
        if (purchaseOrderId == null) {
            throw new IllegalArgumentException("La recepción debe estar ligada a una orden de compra.");
        }
        return registerMovement(
                clinicId,
                materialId,
                MovementType.PURCHASE_ENTRY,
                quantity,
                null,
                movementDate,
                "PURCHASE_ORDER",
                purchaseOrderId,
                notes,
                lotNumber,
                expirationDate,
                null,
                unitCost
        );
    }

    public InventoryMovement registerAdjustment(UUID clinicId, UUID materialId, RegisterMovementCommand command) {
        if (command.type() == null || !command.type().isAdjustment()) {
            throw new IllegalArgumentException("El ajuste debe ser ADJUSTMENT_IN o ADJUSTMENT_OUT.");
        }
        InventoryMovement movement = registerMovement(
                clinicId,
                materialId,
                command.type(),
                command.quantity(),
                command.presentationQuantity(),
                command.movementDate(),
                null,
                null,
                command.notes(),
                command.lotNumber(),
                command.expirationDate(),
                command.batchId(),
                null
        );

        if (command.type() == MovementType.ADJUSTMENT_OUT) {
            String materialName = materialRepository.findByIdAndClinicId(materialId, clinicId)
                    .map(Material::getName)
                    .orElse(null);
            eventPublisher.publish(DomainEventRoutingKeys.SUPPLY_WASTED, new MermaCaducidadEvent(
                    UUID.randomUUID(),
                    clinicId,
                    materialId,
                    materialName,
                    movement.getId(),
                    movement.getQuantity(),
                    movement.getUnitCostAtMovement(),
                    LocalDateTime.now()
            ));
        }

        return movement;
    }

    public InventoryMovement registerSaleExit(UUID clinicId, UUID materialId, RegisterMovementCommand command) {
        if (command.type() != MovementType.SALE_EXIT) {
            throw new IllegalArgumentException("El tipo de movimiento debe ser SALE_EXIT.");
        }
        Material material = materialRepository.findByIdAndClinicId(materialId, clinicId)
                .orElseThrow(() -> new IllegalArgumentException("El material no existe en esta clínica."));
        if (!material.isSaleEnabled()) {
            throw new IllegalStateException("Este material no está habilitado para venta directa.");
        }
        return registerMovement(
                clinicId,
                materialId,
                command.type(),
                command.quantity(),
                command.presentationQuantity(),
                command.movementDate(),
                null,
                null,
                command.notes(),
                null,
                null,
                command.batchId(),
                null
        );
    }

    public InventoryMovement registerUsageExit(UUID clinicId, UUID materialId, RegisterUsageExitCommand command) {
        if (command.referenceType() == null || command.referenceType().isBlank() || command.referenceId() == null) {
            throw new IllegalArgumentException("La salida por uso debe incluir referencia de origen.");
        }

        return movementRepository
                .findByReferenceAndMaterial(MovementType.USAGE_EXIT, command.referenceType(), command.referenceId(), materialId)
                .orElseGet(() -> registerMovement(
                        clinicId,
                        materialId,
                        MovementType.USAGE_EXIT,
                        command.quantity(),
                        null,
                        command.movementDate(),
                        command.referenceType(),
                        command.referenceId(),
                        command.notes(),
                        null,
                        null,
                        command.batchId(),
                        null
                ));
    }

    public List<InventoryMovement> listMovementsByMaterial(UUID clinicId, UUID materialId, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa.");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("El tamaño de página debe estar entre 1 y 100.");
        }
        ensureMaterialExists(materialId, clinicId);
        return movementRepository.findByMaterialIdAndClinicId(materialId, clinicId, page, size);
    }

    public List<InventoryMovement> listMovementsByClinic(UUID clinicId, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa.");
        }
        if (size < 1 || size > 200) {
            throw new IllegalArgumentException("El tamaño de página debe estar entre 1 y 200.");
        }
        return movementRepository.findByClinicId(clinicId, page, size);
    }

    public List<InventoryBatch> listBatchesByMaterial(UUID clinicId, UUID materialId) {
        ensureMaterialExists(materialId, clinicId);
        return batchRepository.findByMaterialIdAndClinicId(materialId, clinicId);
    }

    public List<InventoryMovement> registerExpiredBatchWastes(UUID clinicId, LocalDate asOfDate) {
        LocalDate cutoffDate = asOfDate != null ? asOfDate : LocalDate.now();
        return batchRepository.findExpiredWithRemainingForUpdate(clinicId, cutoffDate).stream()
                .filter(batch -> batch.getRemainingQuantity() != null && batch.getRemainingQuantity().signum() > 0)
                .map(batch -> registerAdjustment(
                        clinicId,
                        batch.getMaterialId(),
                        new RegisterMovementCommand(
                                MovementType.ADJUSTMENT_OUT,
                                batch.getRemainingQuantity(),
                                null,
                                LocalDateTime.now(),
                                "Merma por caducidad - lote " + (batch.getLotNumber() == null ? "sin folio" : batch.getLotNumber()),
                                null,
                                null,
                                batch.getId()
                        )
                ))
                .toList();
    }

    private InventoryMovement registerMovement(
            UUID clinicId,
            UUID materialId,
            MovementType type,
            BigDecimal quantity,
            BigDecimal presentationQuantity,
            LocalDateTime movementDate,
            String referenceType,
            UUID referenceId,
            String notes,
            String lotNumber,
            LocalDate expirationDate,
            UUID batchId,
            BigDecimal entryUnitCost) {
        Material material = materialRepository.findByIdAndClinicIdForUpdate(materialId, clinicId)
                .orElseThrow(() -> new IllegalArgumentException("El material no existe en esta clínica."));
        if (!material.isActive()) {
            throw new IllegalStateException("No se pueden registrar movimientos para un material inactivo.");
        }

        ResolvedQuantity resolvedQuantity = resolveQuantity(material, quantity, presentationQuantity);

        UUID affectedBatchId = null;
        BigDecimal unitCostAtMovement = entryUnitCost != null ? entryUnitCost : material.getUnitCost();
        if (type.increasesStock()) {
            if (type == MovementType.PURCHASE_ENTRY && entryUnitCost != null) {
                material.receiveStock(resolvedQuantity.quantity(), entryUnitCost);
            } else {
                material.increaseStock(resolvedQuantity.quantity());
            }
            if (material.isTracksBatches() && (type == MovementType.PURCHASE_ENTRY || type == MovementType.ADJUSTMENT_IN)) {
                affectedBatchId = createBatch(
                        clinicId,
                        materialId,
                        resolvedQuantity.quantity(),
                        lotNumber,
                        expirationDate,
                        unitCostAtMovement
                ).getId();
            }
        } else if (type.decreasesStock()) {
            if (type != MovementType.USAGE_EXIT) {
                validateAvailableStockForManualExit(material, resolvedQuantity.quantity());
            }
            material.decreaseStock(resolvedQuantity.quantity());
            if (material.isTracksBatches()) {
                BatchConsumption consumption = consumeBatches(clinicId, materialId, resolvedQuantity.quantity(), batchId, material.getUnitCost());
                affectedBatchId = consumption.singleBatchId();
                unitCostAtMovement = consumption.weightedUnitCost();
            }
        } else {
            throw new IllegalArgumentException("Tipo de movimiento no soportado.");
        }

        InventoryMovement movement = InventoryMovement.builder()
                .id(UUID.randomUUID())
                .clinicId(clinicId)
                .materialId(materialId)
                .type(type)
                .quantity(resolvedQuantity.quantity())
                .presentationQuantity(resolvedQuantity.presentationQuantity())
                .presentationNameAtMovement(resolvedQuantity.presentationNameAtMovement())
                .quantityPerPresentationAtMovement(resolvedQuantity.quantityPerPresentationAtMovement())
                .unitCostAtMovement(unitCostAtMovement)
                .batchId(affectedBatchId)
                .movementDate(movementDate != null ? movementDate : LocalDateTime.now())
                .referenceType(referenceType)
                .referenceId(referenceId)
                .notes(notes)
                .createdAt(LocalDateTime.now())
                .build();

        materialRepository.save(material);
        return movementRepository.save(movement);
    }

    private InventoryBatch createBatch(
            UUID clinicId,
            UUID materialId,
            BigDecimal quantity,
            String lotNumber,
            LocalDate expirationDate,
            BigDecimal unitCost) {
        InventoryBatch batch = InventoryBatch.builder()
                .id(UUID.randomUUID())
                .clinicId(clinicId)
                .materialId(materialId)
                .lotNumber(normalizeOptional(lotNumber))
                .expirationDate(expirationDate)
                .initialQuantity(quantity)
                .remainingQuantity(quantity)
                .unitCostAtEntry(unitCost)
                .createdAt(LocalDateTime.now())
                .build();
        return batchRepository.save(batch);
    }

    private BatchConsumption consumeBatches(
            UUID clinicId,
            UUID materialId,
            BigDecimal quantity,
            UUID batchId,
            BigDecimal fallbackUnitCost) {
        if (batchId != null) {
            InventoryBatch batch = batchRepository.findByIdAndClinicIdForUpdate(batchId, clinicId)
                    .orElseThrow(() -> new IllegalArgumentException("El lote indicado no existe."));
            if (!batch.getMaterialId().equals(materialId)) {
                throw new IllegalArgumentException("El lote indicado no pertenece a este material.");
            }
            batch.decrease(quantity);
            batchRepository.save(batch);
            return new BatchConsumption(batch.getId(), safeUnitCost(batch.getUnitCostAtEntry(), fallbackUnitCost));
        }

        List<InventoryBatch> availableBatches = batchRepository.findAvailableForConsumptionForUpdate(materialId, clinicId);
        BigDecimal remaining = quantity;
        BigDecimal weightedCost = BigDecimal.ZERO;
        UUID singleBatchId = null;
        int consumedBatchCount = 0;

        for (InventoryBatch batch : availableBatches) {
            if (remaining.signum() <= 0) {
                break;
            }
            if (batch.getRemainingQuantity() == null || batch.getRemainingQuantity().signum() <= 0) {
                continue;
            }

            BigDecimal consumed = batch.getRemainingQuantity().min(remaining);
            batch.decrease(consumed);
            batchRepository.save(batch);

            BigDecimal unitCost = safeUnitCost(batch.getUnitCostAtEntry(), fallbackUnitCost);
            weightedCost = weightedCost.add(consumed.multiply(unitCost));
            remaining = remaining.subtract(consumed);
            consumedBatchCount++;
            singleBatchId = consumedBatchCount == 1 ? batch.getId() : null;
        }

        if (remaining.signum() > 0) {
            throw new IllegalStateException("No hay existencia suficiente por lotes para esta salida.");
        }

        BigDecimal weightedUnitCost = quantity.signum() > 0
                ? weightedCost.divide(quantity, 4, RoundingMode.HALF_UP)
                : safeUnitCost(null, fallbackUnitCost);
        return new BatchConsumption(singleBatchId, weightedUnitCost);
    }

    private void validateAvailableStockForManualExit(Material material, BigDecimal quantity) {
        if (material.availableQuantity().compareTo(quantity) < 0) {
            throw new IllegalStateException("Stock disponible insuficiente: hay unidades reservadas para citas. Libera la reserva o ajusta la cantidad.");
        }
    }

    private BigDecimal safeUnitCost(BigDecimal preferred, BigDecimal fallback) {
        if (preferred != null) {
            return preferred;
        }
        return fallback != null ? fallback : BigDecimal.ZERO;
    }

    private void ensureMaterialExists(UUID materialId, UUID clinicId) {
        materialRepository.findByIdAndClinicId(materialId, clinicId)
                .orElseThrow(() -> new IllegalArgumentException("El material no existe en esta clínica."));
    }

    private void validateQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero.");
        }
    }

    private ResolvedQuantity resolveQuantity(Material material, BigDecimal quantity, BigDecimal presentationQuantity) {
        boolean hasBaseQuantity = quantity != null;
        boolean hasPresentationQuantity = presentationQuantity != null;

        if (hasBaseQuantity == hasPresentationQuantity) {
            throw new IllegalArgumentException("Captura cantidad base o cantidad por presentación, no ambas.");
        }

        if (hasPresentationQuantity) {
            validateQuantity(presentationQuantity);
            if (material.getQuantityPerPresentation() == null || material.getQuantityPerPresentation().signum() <= 0) {
                throw new IllegalStateException("El material no tiene contenido por presentación configurado.");
            }
            BigDecimal baseQuantity = presentationQuantity.multiply(material.getQuantityPerPresentation());
            validateQuantity(baseQuantity);
            return new ResolvedQuantity(
                    baseQuantity,
                    presentationQuantity,
                    material.getPresentationName(),
                    material.getQuantityPerPresentation()
            );
        }

        validateQuantity(quantity);
        return new ResolvedQuantity(quantity, null, null, null);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ResolvedQuantity(
            BigDecimal quantity,
            BigDecimal presentationQuantity,
            String presentationNameAtMovement,
            BigDecimal quantityPerPresentationAtMovement
    ) {}

    private record BatchConsumption(UUID singleBatchId, BigDecimal weightedUnitCost) {}
}
