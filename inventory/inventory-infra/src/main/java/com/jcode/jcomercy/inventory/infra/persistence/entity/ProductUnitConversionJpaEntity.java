package com.jcode.jcomercy.inventory.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import com.jcode.jcomercy.inventory.domain.model.product.ProductUnitConversion.ConversionStatus;

@Entity
@Table(name = "product_unit_conversion", schema = "inventory")
public class ProductUnitConversionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "from_unit_code", nullable = false, length = 20)
    private String fromUnitCode;

    @Column(name = "to_unit_code", nullable = false, length = 20)
    private String toUnitCode;

    @Column(nullable = false, precision = 24, scale = 9)
    private BigDecimal factor;

    @Column(name = "rounding_mode", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private RoundingMode roundingMode;

    @Column(name = "rounding_scale", nullable = false)
    private int roundingScale;

    @Column(name = "is_purchase_unit", nullable = false)
    private boolean purchaseUnit;

    @Column(name = "is_sales_unit", nullable = false)
    private boolean salesUnit;

    @Column(name = "is_default", nullable = false)
    private boolean defaultConversion;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ConversionStatus status;

    protected ProductUnitConversionJpaEntity() {
    }

    public ProductUnitConversionJpaEntity(UUID id, UUID productId, String fromUnitCode, String toUnitCode,
                                          BigDecimal factor, int roundingScale, RoundingMode roundingMode,
                                          boolean purchaseUnit, boolean salesUnit, boolean defaultConversion,
                                          ConversionStatus status) {
        this.id = id;
        this.productId = productId;
        this.fromUnitCode = fromUnitCode;
        this.toUnitCode = toUnitCode;
        this.factor = factor;
        this.roundingScale = roundingScale;
        this.roundingMode = roundingMode;
        this.purchaseUnit = purchaseUnit;
        this.salesUnit = salesUnit;
        this.defaultConversion = defaultConversion;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public String getFromUnitCode() { return fromUnitCode; }
    public String getToUnitCode() { return toUnitCode; }
    public BigDecimal getFactor() { return factor; }
    public int getRoundingScale() { return roundingScale; }
    public RoundingMode getRoundingMode() { return roundingMode; }
    public boolean isPurchaseUnit() { return purchaseUnit; }
    public boolean isSalesUnit() { return salesUnit; }
    public boolean isDefaultConversion() { return defaultConversion; }
    public ConversionStatus getStatus() { return status; }
}
