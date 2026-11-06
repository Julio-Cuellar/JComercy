package com.jcode.jcomercy.companymanagement.infra.persistence.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.companymanagement.domain.exception.DuplicateResourceException;
import com.jcode.jcomercy.companymanagement.domain.model.employee.EmployeeProfile;
import com.jcode.jcomercy.companymanagement.domain.model.employee.EmployeeStatus;
import com.jcode.jcomercy.companymanagement.domain.model.employee.ProfileType;
import com.jcode.jcomercy.companymanagement.domain.port.EmployeeProfileRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Los perfiles de empleado en {@code company_management.employee_profile}, con SQL directo. Los índices únicos de la
 * tabla son la última defensa contra dos altas simultáneas con el mismo RFC, correo o CURP en una empresa.
 */
@Component
public class JdbcEmployeeProfileRepositoryAdapter implements EmployeeProfileRepositoryPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcEmployeeProfileRepositoryAdapter.class);
    private static final TypeReference<List<String>> TEXT_LIST = new TypeReference<>() { };
    private static final String COLUMNS = """
        id, public_employee_id, public_company_id, profile_type, status, first_name, last_name, birth_date, rfc, curp,
        phone_number, email, state, address, role_ids_json, branch_ids_json, account_public_id, membership_public_id,
        created_at, updated_at, version
        """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<EmployeeProfile> rowMapper = this::toProfile;

    public JdbcEmployeeProfileRepositoryAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public EmployeeProfile save(EmployeeProfile profile) {
        EmployeeProfile.PersonalData data = profile.getData();
        try {
            int updated = jdbc.update("""
                UPDATE company_management.employee_profile
                SET status=?, first_name=?, last_name=?, birth_date=?, rfc=?, curp=?, phone_number=?, email=?, state=?, address=?,
                    role_ids_json=?, branch_ids_json=?, account_public_id=?, membership_public_id=?, updated_at=?, version=version+1
                WHERE id=?
                """, profile.getStatus().name(), data.firstName(), data.lastName(), Date.valueOf(data.birthDate()), data.rfc(),
                data.curp(), data.phoneNumber(), data.email(), data.state(), data.address(), json(profile.getRoleIds()),
                json(profile.getBranchIds()), profile.getAccountPublicId(), profile.getMembershipPublicId(),
                at(profile.getUpdatedAt()), profile.getId());
            if (updated == 0) {
                jdbc.update("""
                    INSERT INTO company_management.employee_profile
                      (id, public_employee_id, public_company_id, profile_type, status, first_name, last_name, birth_date, rfc, curp,
                       phone_number, email, state, address, role_ids_json, branch_ids_json, account_public_id, membership_public_id,
                       created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, profile.getId(), profile.getPublicEmployeeId(), profile.getPublicCompanyId(), profile.getProfileType().name(),
                    profile.getStatus().name(), data.firstName(), data.lastName(), Date.valueOf(data.birthDate()), data.rfc(), data.curp(),
                    data.phoneNumber(), data.email(), data.state(), data.address(), json(profile.getRoleIds()),
                    json(profile.getBranchIds()), profile.getAccountPublicId(), profile.getMembershipPublicId(),
                    at(profile.getCreatedAt()), at(profile.getUpdatedAt()));
            }
        } catch (DuplicateKeyException e) {
            // No se registra el mensaje: el de la base de datos cita el valor repetido, que es un dato personal.
            log.warn("Alta de empleado rechazada por un dato repetido en la empresa companyId={}", profile.getPublicCompanyId());
            throw new DuplicateResourceException("Ya hay una persona registrada con esos datos en tu empresa.");
        }
        return jdbc.query("SELECT " + COLUMNS + " FROM company_management.employee_profile WHERE id=?", rowMapper, profile.getId())
            .stream().findFirst().orElseThrow();
    }

    @Override
    public Optional<EmployeeProfile> findByPublicEmployeeId(String publicEmployeeId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM company_management.employee_profile WHERE public_employee_id=?",
            rowMapper, publicEmployeeId).stream().findFirst();
    }

    @Override
    public List<EmployeeProfile> findByPublicCompanyId(String publicCompanyId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM company_management.employee_profile WHERE public_company_id=?",
            rowMapper, publicCompanyId);
    }

    private EmployeeProfile toProfile(ResultSet rs, int row) throws SQLException {
        EmployeeProfile.PersonalData data = new EmployeeProfile.PersonalData(rs.getString("first_name"), rs.getString("last_name"),
            rs.getDate("birth_date").toLocalDate(), rs.getString("rfc"), rs.getString("curp"), rs.getString("phone_number"),
            rs.getString("email"), rs.getString("state"), rs.getString("address"));
        return new EmployeeProfile(rs.getObject("id", UUID.class), rs.getString("public_employee_id"), rs.getString("public_company_id"),
            ProfileType.valueOf(rs.getString("profile_type")), EmployeeStatus.valueOf(rs.getString("status")), data,
            readList(rs.getString("role_ids_json")), readList(rs.getString("branch_ids_json")),
            rs.getString("account_public_id"), rs.getString("membership_public_id"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getObject("updated_at", OffsetDateTime.class).toInstant(), rs.getLong("version"));
    }

    private List<String> readList(String json) {
        try {
            return mapper.readValue(json, TEXT_LIST);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer una lista guardada de un perfil de empleado", e);
        }
    }

    private String json(List<String> values) {
        try {
            return mapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo guardar una lista de un perfil de empleado", e);
        }
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
