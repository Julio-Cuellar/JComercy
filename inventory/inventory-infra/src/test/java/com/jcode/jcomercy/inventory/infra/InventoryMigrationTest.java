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

    // H2 guarda los nombres en mayúsculas y PostgreSQL en minúsculas: se compara en minúsculas para que valga en ambas.
    // Las 13 son las tablas de la aplicación: el historial de Flyway no cuenta (en H2 vive en otro esquema, en PostgreSQL no).
    @Test
    void flywayCreatesInventoryCoreTables() {
        var tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where lower(table_schema) = 'inventory' and lower(table_name) <> 'flyway_schema_history'",
                Integer.class);

        assertThat(tableCount).isEqualTo(13);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where lower(table_schema) = 'inventory' and lower(table_name) = 'stock_reservation'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where lower(table_schema) = 'inventory' and lower(table_name) = 'product'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where lower(table_schema) = 'inventory' and lower(table_name) = 'inventory_movement'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where lower(table_schema) = 'inventory' and lower(table_name) = 'company_unit_of_measure'",
                Integer.class)).isEqualTo(1);
    }
}
