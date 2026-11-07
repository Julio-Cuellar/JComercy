package com.jcode.jcomercy.sales.infra.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Dice si la base de datos es PostgreSQL o H2 (que solo se usa en pruebas y desarrollo). Las dos hablan casi el mismo
 * SQL, pero crear-o-reemplazar una fila se escribe distinto: {@code ON CONFLICT} en PostgreSQL y {@code MERGE} en H2.
 */
@Component
public class SqlDialect {

    private final JdbcTemplate jdbc;
    private volatile Boolean postgres;

    public SqlDialect(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isPostgres() {
        Boolean known = postgres;
        if (known == null) {
            known = Boolean.TRUE.equals(jdbc.execute((java.sql.Connection connection) ->
                "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())));
            postgres = known;
        }
        return known;
    }
}
