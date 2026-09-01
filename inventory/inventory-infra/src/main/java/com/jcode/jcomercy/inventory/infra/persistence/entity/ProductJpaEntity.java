package com.jcode.jcomercy.inventory.infra.persistence.entity;

import com.jcode.jcomercy.inventory.domain.model.product.ProductStatus;
import com.jcode.jcomercy.inventory.domain.model.product.ProductTrackingMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product", schema = "inventory")
public class ProductJpaEntity {

    @Id
    private UUID id;

    @Column(name = "public_product_id", nullable = false, unique = true)
    private UUID publicProductId;

    @Column(name = "company_id", nullable = false, length = 100)
    private String companyId;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "category_id", length = 100)
    private String categoryId;

    @Column(name = "stock_unit_code", nullable = false, length = 20)
    private String stockUnitCode;

    @Column(name = "purchase_unit_code", length = 20)
    private String purchaseUnitCode;

    @Column(name = "sale_unit_code", length = 20)
    private String saleUnitCode;

    @Column(name = "net_quantity", precision = 24, scale = 9)
    private BigDecimal netQuantity;

    @Column(name = "net_quantity_unit_code", length = 20)
    private String netQuantityUnitCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_mode", nullable = false, length = 30)
    private ProductTrackingMode trackingMode;

    @Column(name = "allow_negative_stock", nullable = false)
    private boolean allowNegativeStock;

    @Column(name = "costing_method", nullable = false, length = 40)
    private String costingMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ProductJpaEntity() {
    }

    public ProductJpaEntity(UUID id, UUID publicProductId, String companyId, String sku, String name,
                            String categoryId, String stockUnitCode, String purchaseUnitCode, String saleUnitCode,
                            BigDecimal netQuantity, String netQuantityUnitCode, ProductTrackingMode trackingMode,
                            boolean allowNegativeStock, String costingMethod, ProductStatus status) {
        this.id = id;
        this.publicProductId = publicProductId;
        this.companyId = companyId;
        this.sku = sku;
        this.name = name;
        this.categoryId = categoryId;
        this.stockUnitCode = stockUnitCode;
        this.purchaseUnitCode = purchaseUnitCode;
        this.saleUnitCode = saleUnitCode;
        this.netQuantity = netQuantity;
        this.netQuantityUnitCode = netQuantityUnitCode;
        this.trackingMode = trackingMode;
        this.allowNegativeStock = allowNegativeStock;
        this.costingMethod = costingMethod;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getPublicProductId() { return publicProductId; }
    public String getCompanyId() { return companyId; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getCategoryId() { return categoryId; }
    public String getStockUnitCode() { return stockUnitCode; }
    public String getPurchaseUnitCode() { return purchaseUnitCode; }
    public String getSaleUnitCode() { return saleUnitCode; }
    public BigDecimal getNetQuantity() { return netQuantity; }
    public String getNetQuantityUnitCode() { return netQuantityUnitCode; }
    public ProductTrackingMode getTrackingMode() { return trackingMode; }
    public boolean isAllowNegativeStock() { return allowNegativeStock; }
    public ProductStatus getStatus() { return status; }
}
