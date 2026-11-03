package com.jcode.jcomercy.inventory.domain.model.unit;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnitOfMeasureTest {

    @Test
    void convertsUnitsWithinTheSameDimension() {
        var kilogram = new UnitOfMeasure("KG", "Kilogramo", "kg", UnitDimension.MASS, "G", new BigDecimal("1000"), 3);
        var gram = new UnitOfMeasure("G", "Gramo", "g", UnitDimension.MASS, "G", BigDecimal.ONE, 0);

        assertThat(kilogram.convertTo(gram, BigDecimal.ONE)).isEqualByComparingTo("1000");
    }

    @Test
    void rejectsConversionAcrossDimensions() {
        var kilogram = new UnitOfMeasure("KG", "Kilogramo", "kg", UnitDimension.MASS, "G", new BigDecimal("1000"), 3);
        var liter = new UnitOfMeasure("L", "Litro", "L", UnitDimension.VOLUME, "ML", new BigDecimal("1000"), 3);

        assertThatThrownBy(() -> kilogram.convertTo(liter, BigDecimal.ONE))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessage("Units must belong to the same dimension");
    }
}
