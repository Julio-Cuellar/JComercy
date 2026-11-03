package com.jcode.jcomercy.inventory.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "server.port=0")
class InventoryMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesInventoryCoreTables() {
        var tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'INVENTORY'",
                Integer.class);

        assertThat(tableCount).isEqualTo(13);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'INVENTORY' and table_name = 'STOCK_RESERVATION'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'INVENTORY' and table_name = 'PRODUCT'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'INVENTORY' and table_name = 'INVENTORY_MOVEMENT'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'INVENTORY' and table_name = 'COMPANY_UNIT_OF_MEASURE'",
                Integer.class)).isEqualTo(1);
    }
}
