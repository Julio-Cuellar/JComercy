package com.jcode.jcomercy.sales.infra.persistence.adapter;

import com.jcode.jcomercy.sales.domain.model.price.ProductPrice;
import com.jcode.jcomercy.sales.domain.port.ProductPriceRepositoryPort;
import com.jcode.jcomercy.sales.infra.persistence.SqlDialect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Los precios de venta en {@code sales.product_price}. Guardar crea o reemplaza el precio en una sola sentencia, así que
 * dos personas que fijan el precio de un producto nuevo a la vez no chocan.
 */
@Component
public class JdbcProductPriceRepositoryAdapter implements ProductPriceRepositoryPort {
    private static final String COLUMNS = "company_id, product_id, amount, currency, updated_at, updated_by";
    private static final String UPSERT_POSTGRES = """
        INSERT INTO sales.product_price (company_id, product_id, amount, currency, updated_at, updated_by)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (company_id, product_id) DO UPDATE SET
          amount = EXCLUDED.amount, currency = EXCLUDED.currency, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by
        """;
    private static final String UPSERT_H2 = """
        MERGE INTO sales.product_price (company_id, product_id, amount, currency, updated_at, updated_by)
        KEY (company_id, product_id) VALUES (?, ?, ?, ?, ?, ?)
        """;

    private final JdbcTemplate jdbc;
    private final SqlDialect dialect;
    private final RowMapper<ProductPrice> rowMapper = this::toPrice;

    public JdbcProductPriceRepositoryAdapter(JdbcTemplate jdbc, SqlDialect dialect) {
        this.jdbc = jdbc;
        this.dialect = dialect;
    }

    @Override
    @Transactional
    public ProductPrice save(ProductPrice price) {
        jdbc.update(dialect.isPostgres() ? UPSERT_POSTGRES : UPSERT_H2, price.companyId(), price.productId(), price.amount(),
            price.currency(), OffsetDateTime.ofInstant(price.updatedAt(), ZoneOffset.UTC), price.updatedBy());
        return price;
    }

    @Override
    public List<ProductPrice> findByCompanyId(String companyId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM sales.product_price WHERE company_id = ? ORDER BY updated_at DESC, product_id",
            rowMapper, companyId);
    }

    @Override
    public List<ProductPrice> findByCompanyIdAndProductIds(String companyId, Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        List<Object> params = new ArrayList<>();
        params.add(companyId);
        params.addAll(productIds);
        String placeholders = String.join(", ", java.util.Collections.nCopies(productIds.size(), "?"));
        return jdbc.query("SELECT " + COLUMNS + " FROM sales.product_price WHERE company_id = ? AND product_id IN (" + placeholders
            + ") ORDER BY product_id", rowMapper, params.toArray());
    }

    private ProductPrice toPrice(ResultSet rs, int row) throws SQLException {
        return new ProductPrice(rs.getString("company_id"), rs.getObject("product_id", UUID.class), rs.getBigDecimal("amount"),
            rs.getString("currency"), rs.getObject("updated_at", OffsetDateTime.class).toInstant(), rs.getString("updated_by"));
    }
}
