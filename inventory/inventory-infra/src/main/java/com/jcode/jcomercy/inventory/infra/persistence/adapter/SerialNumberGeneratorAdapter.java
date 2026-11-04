package com.jcode.jcomercy.inventory.infra.persistence.adapter;

import com.jcode.jcomercy.inventory.domain.port.SerialNumberGeneratorPort;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventorySerialSequenceJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SerialNumberGeneratorAdapter implements SerialNumberGeneratorPort {
    private final InventorySerialSequenceJpaRepository repository;
    private final JdbcTemplate jdbc;
    public SerialNumberGeneratorAdapter(InventorySerialSequenceJpaRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }
    // Crear la secuencia con un INSERT normal y atrapar el choque de clave no sirve: con peticiones a la vez, el choque
    // aborta la transacción en PostgreSQL y la que perdió ya no puede leer la fila que creó la otra. ON CONFLICT DO
    // NOTHING espera a que la otra confirme y sigue sin error.
    private static final String CREATE_IF_ABSENT_POSTGRES = """
        INSERT INTO inventory.inventory_serial_sequence (company_id, next_value, version)
        VALUES (?, 1, 0) ON CONFLICT (company_id) DO NOTHING
        """;
    // H2, que solo se usa en pruebas y desarrollo, no entiende ON CONFLICT: se usa la forma estándar de MERGE.
    private static final String CREATE_IF_ABSENT_H2 = """
        MERGE INTO inventory.inventory_serial_sequence AS target
        USING (VALUES (CAST(? AS VARCHAR(100)))) AS source(company_id) ON target.company_id = source.company_id
        WHEN NOT MATCHED THEN INSERT (company_id, next_value, version) VALUES (source.company_id, 1, 0)
        """;

    private volatile String createIfAbsent;

    @Override
    public String next(String companyId) {
        jdbc.update(createIfAbsent(), companyId);
        var sequence = repository.findForUpdate(companyId)
                .orElseThrow(() -> new IllegalStateException("No se pudo crear la secuencia de seriales"));
        long value = sequence.getNextValue();
        sequence.advance();
        repository.save(sequence);
        return "SER-%06d".formatted(value);
    }

    private String createIfAbsent() {
        String sql = createIfAbsent;
        if (sql == null) {
            boolean postgres = Boolean.TRUE.equals(jdbc.execute((java.sql.Connection connection) ->
                "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())));
            sql = postgres ? CREATE_IF_ABSENT_POSTGRES : CREATE_IF_ABSENT_H2;
            createIfAbsent = sql;
        }
        return sql;
    }
}
