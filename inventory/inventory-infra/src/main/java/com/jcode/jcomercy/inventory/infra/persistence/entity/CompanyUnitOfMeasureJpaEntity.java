package com.jcode.jcomercy.inventory.infra.persistence.entity;

import com.jcode.jcomercy.inventory.domain.model.unit.CompanyUnitOfMeasure.UnitStatus;
import com.jcode.jcomercy.inventory.domain.model.unit.UnitDimension;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "company_unit_of_measure", schema = "inventory")
public class CompanyUnitOfMeasureJpaEntity {
    @Id private UUID id;
    @Column(name = "public_unit_id", nullable = false, unique = true) private UUID publicUnitId;
    @Column(name = "company_id", nullable = false, length = 100) private String companyId;
    @Column(nullable = false, length = 20) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 20) private String symbol;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private UnitDimension dimension;
    @Column(name = "base_unit_code", nullable = false, length = 20) private String baseUnitCode;
    @Column(name = "conversion_factor", nullable = false, precision = 24, scale = 9) private BigDecimal conversionFactor;
    @Column(nullable = false) private int decimals;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private UnitStatus status;
    @Version @Column(nullable = false) private long version;
    protected CompanyUnitOfMeasureJpaEntity() { }
    public CompanyUnitOfMeasureJpaEntity(UUID id, UUID publicUnitId, String companyId, String code, String name,
                                         String symbol, UnitDimension dimension, String baseUnitCode,
                                         BigDecimal conversionFactor, int decimals, UnitStatus status) {
        this.id=id; this.publicUnitId=publicUnitId; this.companyId=companyId; this.code=code; this.name=name;
        this.symbol=symbol; this.dimension=dimension; this.baseUnitCode=baseUnitCode; this.conversionFactor=conversionFactor;
        this.decimals=decimals; this.status=status;
    }
    public UUID getId() { return id; } public UUID getPublicUnitId() { return publicUnitId; }
    public String getCompanyId() { return companyId; } public String getCode() { return code; } public String getName() { return name; }
    public String getSymbol() { return symbol; } public UnitDimension getDimension() { return dimension; }
    public String getBaseUnitCode() { return baseUnitCode; } public BigDecimal getConversionFactor() { return conversionFactor; }
    public int getDecimals() { return decimals; } public UnitStatus getStatus() { return status; }
}
