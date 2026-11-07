package com.jcode.jcomercy.sales.infra;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Con {@code -Ppostgres}, todas las pruebas de Spring usan un PostgreSQL real en un contenedor en lugar de H2, sin
 * tocar las pruebas: se registra en {@code META-INF/spring.factories} y reemplaza la base de datos del
 * {@code application.yml}. Sin el perfil no hace nada, así que la suite normal no necesita Docker.
 *
 * <p>Un solo contenedor sirve a toda la ejecución; Testcontainers lo detiene cuando termina la JVM. Se le sube
 * {@code max_connections}: cada contexto de Spring que el caché de pruebas conserva guarda su propio pool (ERR-030).
 */
public class PostgresTestDatabase implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String SWITCH = "jcomercy.test.db";
    // La documentación de migraciones recomienda PostgreSQL 16 o superior.
    private static final String IMAGE = "postgres:16-alpine";

    private static PostgreSQLContainer<?> container;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (!"postgres".equals(System.getProperty(SWITCH))) {
            return;
        }
        PostgreSQLContainer<?> database = database();
        TestPropertyValues.of(
            "spring.datasource.url=" + database.getJdbcUrl(),
            "spring.datasource.username=" + database.getUsername(),
            "spring.datasource.password=" + database.getPassword(),
            "spring.datasource.driver-class-name=org.postgresql.Driver"
        ).applyTo(context);
    }

    private static synchronized PostgreSQLContainer<?> database() {
        if (container == null) {
            container = new PostgreSQLContainer<>(IMAGE)
                .withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=500");
            container.start();
        }
        return container;
    }
}
