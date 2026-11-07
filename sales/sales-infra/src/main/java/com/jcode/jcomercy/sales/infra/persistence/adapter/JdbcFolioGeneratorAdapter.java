package com.jcode.jcomercy.sales.infra.persistence.adapter;

import com.jcode.jcomercy.sales.domain.port.FolioGeneratorPort;
import com.jcode.jcomercy.sales.infra.persistence.SqlDialect;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * El folio de cada ticket, consecutivo por sucursal en {@code sales.ticket_sequence}. La fila de la sucursal se crea si
 * falta y se bloquea al tomar el número, así que dos cobros a la vez no repiten folio. Crear la fila con un INSERT normal
 * y atrapar el choque no sirve en PostgreSQL: el choque aborta la transacción y la que perdió ya no puede seguir.
 */
@Component
public class JdbcFolioGeneratorAdapter implements FolioGeneratorPort {
    private static final String CREATE_IF_ABSENT_POSTGRES = """
        INSERT INTO sales.ticket_sequence (company_id, branch_id, next_value) VALUES (?, ?, 1)
        ON CONFLICT (company_id, branch_id) DO NOTHING
        """;
    // H2 no entiende ON CONFLICT: se usa la forma estándar de MERGE.
    private static final String CREATE_IF_ABSENT_H2 = """
        MERGE INTO sales.ticket_sequence AS target
        USING (VALUES (CAST(? AS VARCHAR(100)), CAST(? AS VARCHAR(100)))) AS source(company_id, branch_id)
        ON target.company_id = source.company_id AND target.branch_id = source.branch_id
        WHEN NOT MATCHED THEN INSERT (company_id, branch_id, next_value) VALUES (source.company_id, source.branch_id, 1)
        """;

    private final JdbcTemplate jdbc;
    private final SqlDialect dialect;

    public JdbcFolioGeneratorAdapter(JdbcTemplate jdbc, SqlDialect dialect) {
        this.jdbc = jdbc;
        this.dialect = dialect;
    }

    @Override
    @Transactional
    public String next(String companyId, String branchId) {
        try {
            jdbc.update(dialect.isPostgres() ? CREATE_IF_ABSENT_POSTGRES : CREATE_IF_ABSENT_H2, companyId, branchId);
        } catch (DuplicateKeyException alreadyCreatedByAnotherCaller) {
            // Solo en H2, donde el choque no aborta la transacción: la fila ya existe y se bloquea enseguida.
        }
        Long value = jdbc.queryForObject(
            "SELECT next_value FROM sales.ticket_sequence WHERE company_id = ? AND branch_id = ? FOR UPDATE", Long.class,
            companyId, branchId);
        jdbc.update("UPDATE sales.ticket_sequence SET next_value = next_value + 1 WHERE company_id = ? AND branch_id = ?",
            companyId, branchId);
        return "TCK-%06d".formatted(value);
    }
}
