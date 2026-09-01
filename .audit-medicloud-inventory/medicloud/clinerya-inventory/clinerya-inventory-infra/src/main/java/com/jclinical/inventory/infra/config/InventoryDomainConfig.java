package com.jclinical.inventory.infra.config;

import com.jclinical.inventory.domain.model.InventoryBatch;
import com.jclinical.inventory.domain.model.InventoryMovement;
import com.jclinical.inventory.domain.model.Material;
import com.jclinical.inventory.domain.model.MaterialReservation;
import com.jclinical.inventory.domain.ports.out.InventoryBatchRepositoryPort;
import com.jclinical.inventory.domain.ports.out.InventoryMovementRepositoryPort;
import com.jclinical.inventory.domain.ports.out.MaterialRepositoryPort;
import com.jclinical.inventory.domain.ports.out.MaterialReservationRepositoryPort;
import com.jclinical.inventory.domain.ports.out.MaterialReservationQueryPort;
import com.jclinical.inventory.domain.ports.out.PurchaseOrderRepositoryPort;
import com.jclinical.inventory.domain.ports.out.PurchaseReceiptRepositoryPort;
import com.jclinical.inventory.domain.ports.out.SupplierRepositoryPort;
import com.jclinical.inventory.domain.ports.out.SupplierMaterialRepositoryPort;
import com.jclinical.inventory.domain.service.InventoryMovementService;
import com.jclinical.inventory.domain.service.MaterialReservationService;
import com.jclinical.inventory.domain.service.MaterialService;
import com.jclinical.inventory.domain.service.PurchasingService;
import com.jclinical.inventory.infra.adapters.out.persistence.InventoryBatchEntity;
import com.jclinical.inventory.infra.adapters.out.persistence.InventoryBatchMapper;
import com.jclinical.inventory.infra.adapters.out.persistence.InventoryMovementEntity;
import com.jclinical.inventory.infra.adapters.out.persistence.InventoryMovementMapper;
import com.jclinical.inventory.infra.adapters.out.persistence.MaterialEntity;
import com.jclinical.inventory.infra.adapters.out.persistence.MaterialMapper;
import com.jclinical.inventory.infra.adapters.out.persistence.MaterialReservationEntity;
import com.jclinical.inventory.infra.adapters.out.persistence.MaterialReservationMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InventoryDomainConfig {

    @Bean
    public MaterialService materialService(MaterialRepositoryPort materialRepository) {
        return new MaterialService(materialRepository);
    }

    @Bean
    public InventoryMovementService inventoryMovementService(
            MaterialRepositoryPort materialRepository,
            InventoryMovementRepositoryPort movementRepository,
            InventoryBatchRepositoryPort batchRepository,
            com.jclinical.core.events.DomainEventPublisherPort eventPublisher) {
        return new InventoryMovementService(materialRepository, movementRepository, batchRepository, eventPublisher);
    }

    @Bean
    public MaterialReservationService materialReservationService(
            MaterialRepositoryPort materialRepository,
            MaterialReservationRepositoryPort reservationRepository,
            MaterialReservationQueryPort reservationQuery) {
        return new MaterialReservationService(materialRepository, reservationRepository, reservationQuery);
    }

    @Bean
    public PurchasingService purchasingService(
            SupplierRepositoryPort supplierRepository,
            SupplierMaterialRepositoryPort supplierMaterialRepository,
            PurchaseOrderRepositoryPort purchaseOrderRepository,
            PurchaseReceiptRepositoryPort purchaseReceiptRepository,
            MaterialRepositoryPort materialRepository,
            InventoryMovementService inventoryMovementService,
            com.jclinical.core.events.DomainEventPublisherPort eventPublisher) {
        return new PurchasingService(
                supplierRepository,
                supplierMaterialRepository,
                purchaseOrderRepository,
                purchaseReceiptRepository,
                materialRepository,
                inventoryMovementService,
                eventPublisher
        );
    }

    @Bean
    @ConditionalOnMissingBean(MaterialReservationMapper.class)
    public MaterialReservationMapper materialReservationMapper() {
        return new MaterialReservationMapper() {
            @Override
            public MaterialReservationEntity toEntity(MaterialReservation domain) {
                if (domain == null) {
                    return null;
                }
                return MaterialReservationEntity.builder()
                        .id(domain.getId())
                        .clinicId(domain.getClinicId())
                        .appointmentId(domain.getAppointmentId())
                        .materialId(domain.getMaterialId())
                        .materialName(domain.getMaterialName())
                        .quantity(domain.getQuantity())
                        .status(domain.getStatus())
                        .createdAt(domain.getCreatedAt())
                        .releasedAt(domain.getReleasedAt())
                        .build();
            }

            @Override
            public MaterialReservation toDomain(MaterialReservationEntity entity) {
                if (entity == null) {
                    return null;
                }
                return MaterialReservation.builder()
                        .id(entity.getId())
                        .clinicId(entity.getClinicId())
                        .appointmentId(entity.getAppointmentId())
                        .materialId(entity.getMaterialId())
                        .materialName(entity.getMaterialName())
                        .quantity(entity.getQuantity())
                        .status(entity.getStatus())
                        .createdAt(entity.getCreatedAt())
                        .releasedAt(entity.getReleasedAt())
                        .build();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(MaterialMapper.class)
    public MaterialMapper materialMapper() {
        return new MaterialMapper() {
            @Override
            public MaterialEntity toEntity(Material domain) {
                if (domain == null) {
                    return null;
                }
                return MaterialEntity.builder()
                        .id(domain.getId())
                        .clinicId(domain.getClinicId())
                        .name(domain.getName())
                        .category(domain.getCategory())
                        .internalCode(domain.getInternalCode())
                        .brand(domain.getBrand())
                        .description(domain.getDescription())
                        .unitOfMeasure(domain.getUnitOfMeasure())
                        .presentationName(domain.getPresentationName())
                        .quantityPerPresentation(domain.getQuantityPerPresentation())
                        .unitCost(domain.getUnitCost())
                        .currentStock(domain.getCurrentStock())
                        .reservedQuantity(domain.getReservedQuantity())
                        .minimumStock(domain.getMinimumStock())
                        .saleEnabled(domain.isSaleEnabled())
                        .salePrice(domain.getSalePrice())
                        .tracksBatches(domain.isTracksBatches())
                        .active(domain.isActive())
                        .version(domain.getVersion())
                        .createdAt(domain.getCreatedAt())
                        .updatedAt(domain.getUpdatedAt())
                        .build();
            }

            @Override
            public Material toDomain(MaterialEntity entity) {
                if (entity == null) {
                    return null;
                }
                return Material.builder()
                        .id(entity.getId())
                        .clinicId(entity.getClinicId())
                        .name(entity.getName())
                        .category(entity.getCategory())
                        .internalCode(entity.getInternalCode())
                        .brand(entity.getBrand())
                        .description(entity.getDescription())
                        .unitOfMeasure(entity.getUnitOfMeasure())
                        .presentationName(entity.getPresentationName())
                        .quantityPerPresentation(entity.getQuantityPerPresentation())
                        .unitCost(entity.getUnitCost())
                        .currentStock(entity.getCurrentStock())
                        .reservedQuantity(entity.getReservedQuantity())
                        .minimumStock(entity.getMinimumStock())
                        .saleEnabled(entity.isSaleEnabled())
                        .salePrice(entity.getSalePrice())
                        .tracksBatches(entity.isTracksBatches())
                        .active(entity.isActive())
                        .version(entity.getVersion())
                        .createdAt(entity.getCreatedAt())
                        .updatedAt(entity.getUpdatedAt())
                        .build();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(InventoryMovementMapper.class)
    public InventoryMovementMapper inventoryMovementMapper() {
        return new InventoryMovementMapper() {
            @Override
            public InventoryMovementEntity toEntity(InventoryMovement domain) {
                if (domain == null) {
                    return null;
                }
                return InventoryMovementEntity.builder()
                        .id(domain.getId())
                        .clinicId(domain.getClinicId())
                        .materialId(domain.getMaterialId())
                        .type(domain.getType())
                        .quantity(domain.getQuantity())
                        .presentationQuantity(domain.getPresentationQuantity())
                        .presentationNameAtMovement(domain.getPresentationNameAtMovement())
                        .quantityPerPresentationAtMovement(domain.getQuantityPerPresentationAtMovement())
                        .unitCostAtMovement(domain.getUnitCostAtMovement())
                        .batchId(domain.getBatchId())
                        .movementDate(domain.getMovementDate())
                        .referenceType(domain.getReferenceType())
                        .referenceId(domain.getReferenceId())
                        .notes(domain.getNotes())
                        .createdAt(domain.getCreatedAt())
                        .build();
            }

            @Override
            public InventoryMovement toDomain(InventoryMovementEntity entity) {
                if (entity == null) {
                    return null;
                }
                return InventoryMovement.builder()
                        .id(entity.getId())
                        .clinicId(entity.getClinicId())
                        .materialId(entity.getMaterialId())
                        .type(entity.getType())
                        .quantity(entity.getQuantity())
                        .presentationQuantity(entity.getPresentationQuantity())
                        .presentationNameAtMovement(entity.getPresentationNameAtMovement())
                        .quantityPerPresentationAtMovement(entity.getQuantityPerPresentationAtMovement())
                        .unitCostAtMovement(entity.getUnitCostAtMovement())
                        .batchId(entity.getBatchId())
                        .movementDate(entity.getMovementDate())
                        .referenceType(entity.getReferenceType())
                        .referenceId(entity.getReferenceId())
                        .notes(entity.getNotes())
                        .createdAt(entity.getCreatedAt())
                        .build();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(InventoryBatchMapper.class)
    public InventoryBatchMapper inventoryBatchMapper() {
        return new InventoryBatchMapper() {
            @Override
            public InventoryBatchEntity toEntity(InventoryBatch domain) {
                if (domain == null) {
                    return null;
                }
                return InventoryBatchEntity.builder()
                        .id(domain.getId())
                        .clinicId(domain.getClinicId())
                        .materialId(domain.getMaterialId())
                        .lotNumber(domain.getLotNumber())
                        .expirationDate(domain.getExpirationDate())
                        .initialQuantity(domain.getInitialQuantity())
                        .remainingQuantity(domain.getRemainingQuantity())
                        .unitCostAtEntry(domain.getUnitCostAtEntry())
                        .createdAt(domain.getCreatedAt())
                        .build();
            }

            @Override
            public InventoryBatch toDomain(InventoryBatchEntity entity) {
                if (entity == null) {
                    return null;
                }
                return InventoryBatch.builder()
                        .id(entity.getId())
                        .clinicId(entity.getClinicId())
                        .materialId(entity.getMaterialId())
                        .lotNumber(entity.getLotNumber())
                        .expirationDate(entity.getExpirationDate())
                        .initialQuantity(entity.getInitialQuantity())
                        .remainingQuantity(entity.getRemainingQuantity())
                        .unitCostAtEntry(entity.getUnitCostAtEntry())
                        .createdAt(entity.getCreatedAt())
                        .build();
            }
        };
    }
}
