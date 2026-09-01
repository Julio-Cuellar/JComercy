package com.jclinical.inventory.infra.config;

import com.jclinical.inventory.domain.model.InventoryBatch;
import com.jclinical.inventory.domain.model.InventoryMovement;
import com.jclinical.inventory.domain.ports.in.ManageInventoryMovementUseCase;
import com.jclinical.inventory.domain.service.InventoryMovementService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDate;
import java.util.UUID;

@Service
@Primary
@RequiredArgsConstructor
public class TransactionalInventoryMovementUseCase implements ManageInventoryMovementUseCase {

    private final InventoryMovementService movementService;

    @Override
    @Transactional
    public InventoryMovement registerPurchaseEntry(UUID clinicId, UUID materialId, RegisterMovementCommand command) {
        return movementService.registerPurchaseEntry(clinicId, materialId, command);
    }

    @Override
    @Transactional
    public InventoryMovement registerAdjustment(UUID clinicId, UUID materialId, RegisterMovementCommand command) {
        return movementService.registerAdjustment(clinicId, materialId, command);
    }

    @Override
    @Transactional
    public InventoryMovement registerSaleExit(UUID clinicId, UUID materialId, RegisterMovementCommand command) {
        return movementService.registerSaleExit(clinicId, materialId, command);
    }

    @Override
    @Transactional
    public InventoryMovement registerUsageExit(UUID clinicId, UUID materialId, RegisterUsageExitCommand command) {
        return movementService.registerUsageExit(clinicId, materialId, command);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryMovement> listMovementsByMaterial(UUID clinicId, UUID materialId, int page, int size) {
        return movementService.listMovementsByMaterial(clinicId, materialId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryMovement> listMovementsByClinic(UUID clinicId, int page, int size) {
        return movementService.listMovementsByClinic(clinicId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryBatch> listBatchesByMaterial(UUID clinicId, UUID materialId) {
        return movementService.listBatchesByMaterial(clinicId, materialId);
    }

    @Override
    @Transactional
    public List<InventoryMovement> registerExpiredBatchWastes(UUID clinicId, LocalDate asOfDate) {
        return movementService.registerExpiredBatchWastes(clinicId, asOfDate);
    }
}
