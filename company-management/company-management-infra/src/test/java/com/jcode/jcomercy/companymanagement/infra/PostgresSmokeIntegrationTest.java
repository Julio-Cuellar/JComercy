package com.jcode.jcomercy.companymanagement.infra;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de humo del perfil {@code postgres} (BIT-033): la aplicación arranca sobre un PostgreSQL real, Flyway aplica
 * todas las migraciones y Hibernate valida el esquema contra esas tablas. Solo corre con {@code -Ppostgres}; la suite
 * normal sigue usando H2.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "jcomercy.test.db", matches = "postgres")
class PostgresSmokeIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("Con el perfil postgres la aplicación corre sobre PostgreSQL real y todas las migraciones quedaron aplicadas")
    void runsOnRealPostgresWithEveryMigrationApplied() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }

        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied()).isNotEmpty().hasSameSizeAs(flyway.info().all());
    }
}
