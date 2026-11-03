package com.jcode.jcomercy.inventory.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

import com.jcode.jcomercy.inventory.domain.model.unit.UnitDimension;

@Entity
@Table(name = "unit_of_measure", schema = "inventory")
public class UnitOfMeasureJpaEntity {

    @Id
    @Column(length = 20, nullable = false)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UnitDimension dimension;

    @Column(name = "base_unit_code", nullable = false, length = 20)
    private String baseUnitCode;

    @Column(name = "conversion_factor", nullable = false, precision = 24, scale = 9)
    private BigDecimal conversionFactor;

    @Column(nullable = false)
    private int decimals;

    @Column(nullable = false, length = 20)
    private String status;

    protected UnitOfMeasureJpaEntity() {
    }

    public UnitOfMeasureJpaEntity(String code, String name, String symbol, UnitDimension dimension,
                                   String baseUnitCode, BigDecimal conversionFactor, int decimals, String status) {
        this.code = code;
        this.name = name;
        this.symbol = symbol;
        this.dimension = dimension;
        this.baseUnitCode = baseUnitCode;
        this.conversionFactor = conversionFactor;
        this.decimals = decimals;
        this.status = status;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSymbol() { return symbol; }
    public UnitDimension getDimension() { return dimension; }
    public String getBaseUnitCode() { return baseUnitCode; }
    public BigDecimal getConversionFactor() { return conversionFactor; }
    public int getDecimals() { return decimals; }
    public String getStatus() { return status; }
}
