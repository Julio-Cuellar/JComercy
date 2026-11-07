package com.jcode.jcomercy.companymanagement.infra.persistence.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.companymanagement.domain.model.role.Role;
import com.jcode.jcomercy.companymanagement.domain.model.role.RoleStatus;
import com.jcode.jcomercy.companymanagement.domain.port.RoleRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Los roles en {@code company_management.role}, leídos con SQL directo como lo hace el cálculo de permisos
 * ({@code EffectivePermissions}). Los permisos se guardan como una lista JSON de texto.
 */
@Component
public class JdbcRoleRepositoryAdapter implements RoleRepositoryPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcRoleRepositoryAdapter.class);
    private static final TypeReference<List<String>> TEXT_LIST = new TypeReference<>() { };
    private static final String COLUMNS = """
        id, public_role_id, public_company_id, name, is_system_role, status, permissions_json, created_at, updated_at, version
        """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<Role> rowMapper = this::toRole;

    public JdbcRoleRepositoryAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public Role save(Role role) {
        int updated = jdbc.update("""
            UPDATE company_management.role SET status=?, permissions_json=?, updated_at=?, version=version+1 WHERE id=?
            """, role.getStatus().name(), json(role), at(role.getUpdatedAt()), role.getId());
        if (updated == 0) {
            jdbc.update("""
                INSERT INTO company_management.role
                  (id, public_role_id, public_company_id, name, is_system_role, status, permissions_json, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, role.getId(), role.getPublicRoleId(), role.getPublicCompanyId(), role.getName(), role.isSystemRole(),
                role.getStatus().name(), json(role), at(role.getCreatedAt()), at(role.getUpdatedAt()));
        }
        return findById(role.getId()).orElseThrow();
    }

    @Override
    public Optional<Role> findById(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM company_management.role WHERE id=?", rowMapper, id).stream().findFirst();
    }

    @Override
    public Optional<Role> findByPublicRoleId(String publicRoleId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM company_management.role WHERE public_role_id=?", rowMapper, publicRoleId)
            .stream().findFirst();
    }

    @Override
    public List<Role> findByPublicCompanyIdOrSystem(String publicCompanyId) {
        return jdbc.query("""
            SELECT %s FROM company_management.role WHERE public_company_id IS NULL OR public_company_id=?
            """.formatted(COLUMNS), rowMapper, publicCompanyId);
    }

    private Role toRole(ResultSet rs, int row) throws SQLException {
        return new Role(rs.getObject("id", UUID.class), rs.getString("public_role_id"), rs.getString("public_company_id"),
            rs.getString("name"), rs.getBoolean("is_system_role"), RoleStatus.valueOf(rs.getString("status")),
            new HashSet<>(readList(rs.getString("permissions_json"), rs.getString("public_role_id"))),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getObject("updated_at", OffsetDateTime.class).toInstant(), rs.getLong("version"));
    }

    /** Un dato dañado no concede nada: se avisa en el registro y el rol queda sin permisos. */
    private List<String> readList(String json, String roleId) {
        try {
            return mapper.readValue(json, TEXT_LIST);
        } catch (Exception e) {
            log.warn("No se pudieron leer los permisos del rol {} y se tratan como vacíos", roleId);
            return List.of();
        }
    }

    private String json(Role role) {
        try {
            return mapper.writeValueAsString(role.getPermissions().stream().sorted().toList());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron guardar los permisos del rol " + role.getPublicRoleId(), e);
        }
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
