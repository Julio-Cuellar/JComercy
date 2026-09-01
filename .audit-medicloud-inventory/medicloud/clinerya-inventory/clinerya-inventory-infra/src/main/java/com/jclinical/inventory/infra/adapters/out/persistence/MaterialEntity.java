package com.jclinical.inventory.infra.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "materials", schema = "inventory")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialEntity {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(nullable = false)
    private String name;

    @Column
    private String category;

    @Column(name = "internal_code")
    private String internalCode;

    @Column
    private String brand;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "unit_of_measure", nullable = false)
    private String unitOfMeasure;

    @Column(name = "presentation_name")
    private String presentationName;

    @Column(name = "quantity_per_presentation", precision = 19, scale = 4)
    private BigDecimal quantityPerPresentation;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "current_stock", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentStock;

    @Column(name = "reserved_quantity", precision = 19, scale = 4)
    private BigDecimal reservedQuantity;

    @Column(name = "minimum_stock", precision = 19, scale = 4)
    private BigDecimal minimumStock;

    @Column(name = "sale_enabled", nullable = false)
    private boolean saleEnabled;

    @Column(name = "sale_price", precision = 19, scale = 4)
    private BigDecimal salePrice;

    @Column(name = "tracks_batches", nullable = false)
    private boolean tracksBatches;

    @Column(nullable = false)
    private boolean active;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
