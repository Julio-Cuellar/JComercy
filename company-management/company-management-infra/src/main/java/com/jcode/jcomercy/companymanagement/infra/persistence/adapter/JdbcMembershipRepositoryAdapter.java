package com.jcode.jcomercy.companymanagement.infra.persistence.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.companymanagement.domain.model.assignment.AssignmentStatus;
import com.jcode.jcomercy.companymanagement.domain.model.assignment.BranchAssignment;
import com.jcode.jcomercy.companymanagement.domain.model.membership.CompanyMembership;
import com.jcode.jcomercy.companymanagement.domain.model.membership.MembershipStatus;
import com.jcode.jcomercy.companymanagement.domain.port.MembershipRepositoryPort;
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
 * Las membresías de empresa y sus asignaciones de sucursal ({@code company_membership} y {@code branch_assignment}),
 * con SQL directo como el resto del acceso a empresas. Los roles de la membresía se guardan como lista JSON de nombres.
 */
@Component
public class JdbcMembershipRepositoryAdapter implements MembershipRepositoryPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcMembershipRepositoryAdapter.class);
    private static final TypeReference<List<String>> TEXT_LIST = new TypeReference<>() { };
    private static final String MEMBERSHIP_COLUMNS = """
        id, public_membership_id, public_company_id, user_id, status, roles_json, created_at, updated_at, version
        """;
    private static final String ASSIGNMENT_COLUMNS = """
        a.id, a.public_assignment_id, a.public_membership_id, a.public_branch_id, a.status, a.created_at, a.updated_at, a.version
        """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<CompanyMembership> membershipMapper = this::toMembership;
    private final RowMapper<BranchAssignment> assignmentMapper = this::toAssignment;

    public JdbcMembershipRepositoryAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public CompanyMembership saveMembership(CompanyMembership membership) {
        int updated = jdbc.update("""
            UPDATE company_management.company_membership SET status=?, roles_json=?, updated_at=?, version=version+1 WHERE id=?
            """, membership.getStatus().name(), rolesJson(membership), at(membership.getUpdatedAt()), membership.getId());
        if (updated == 0) {
            jdbc.update("""
                INSERT INTO company_management.company_membership
                  (id, public_membership_id, public_company_id, user_id, status, roles_json, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, membership.getId(), membership.getPublicMembershipId(), membership.getPublicCompanyId(),
                membership.getUserId(), membership.getStatus().name(), rolesJson(membership),
                at(membership.getCreatedAt()), at(membership.getUpdatedAt()));
        }
        return findMembershipById(membership.getId()).orElseThrow();
    }

    @Override
    public Optional<CompanyMembership> findMembershipById(UUID id) {
        return jdbc.query("SELECT " + MEMBERSHIP_COLUMNS + " FROM company_management.company_membership WHERE id=?",
            membershipMapper, id).stream().findFirst();
    }

    @Override
    public Optional<CompanyMembership> findMembershipByPublicId(String publicMembershipId) {
        return jdbc.query("SELECT " + MEMBERSHIP_COLUMNS + " FROM company_management.company_membership WHERE public_membership_id=?",
            membershipMapper, publicMembershipId).stream().findFirst();
    }

    @Override
    public Optional<CompanyMembership> findMembershipByCompanyAndUser(String publicCompanyId, String userId) {
        return jdbc.query("SELECT " + MEMBERSHIP_COLUMNS + " FROM company_management.company_membership WHERE public_company_id=? AND user_id=?",
            membershipMapper, publicCompanyId, userId).stream().findFirst();
    }

    @Override
    public List<CompanyMembership> findMembershipsByPublicCompanyId(String publicCompanyId) {
        return jdbc.query("SELECT " + MEMBERSHIP_COLUMNS + " FROM company_management.company_membership WHERE public_company_id=?",
            membershipMapper, publicCompanyId);
    }

    @Override
    public BranchAssignment saveAssignment(BranchAssignment assignment) {
        int updated = jdbc.update("""
            UPDATE company_management.branch_assignment SET status=?, updated_at=?, version=version+1 WHERE id=?
            """, assignment.getStatus().name(), at(assignment.getUpdatedAt()), assignment.getId());
        if (updated == 0) {
            jdbc.update("""
                INSERT INTO company_management.branch_assignment
                  (id, public_assignment_id, public_membership_id, public_branch_id, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """, assignment.getId(), assignment.getPublicAssignmentId(), assignment.getPublicMembershipId(),
                assignment.getPublicBranchId(), assignment.getStatus().name(),
                at(assignment.getCreatedAt()), at(assignment.getUpdatedAt()));
        }
        return jdbc.query("SELECT " + ASSIGNMENT_COLUMNS + " FROM company_management.branch_assignment a WHERE a.id=?",
            assignmentMapper, assignment.getId()).stream().findFirst().orElseThrow();
    }

    @Override
    public Optional<BranchAssignment> findAssignmentByPublicId(String publicAssignmentId) {
        return jdbc.query("SELECT " + ASSIGNMENT_COLUMNS + " FROM company_management.branch_assignment a WHERE a.public_assignment_id=?",
            assignmentMapper, publicAssignmentId).stream().findFirst();
    }

    @Override
    public List<BranchAssignment> findAssignmentsByPublicMembershipId(String publicMembershipId) {
        return jdbc.query("SELECT " + ASSIGNMENT_COLUMNS + " FROM company_management.branch_assignment a WHERE a.public_membership_id=? ORDER BY a.created_at, a.public_assignment_id",
            assignmentMapper, publicMembershipId);
    }

    @Override
    public List<BranchAssignment> findAssignmentsByPublicCompanyId(String publicCompanyId) {
        return jdbc.query("""
            SELECT %s FROM company_management.branch_assignment a
            JOIN company_management.company_membership m ON m.public_membership_id = a.public_membership_id
            WHERE m.public_company_id=?
            """.formatted(ASSIGNMENT_COLUMNS), assignmentMapper, publicCompanyId);
    }

    @Override
    public boolean existsAssignment(String publicMembershipId, String publicBranchId) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM company_management.branch_assignment WHERE public_membership_id=? AND public_branch_id=?
            """, Integer.class, publicMembershipId, publicBranchId);
        return count != null && count > 0;
    }

    private CompanyMembership toMembership(ResultSet rs, int row) throws SQLException {
        return new CompanyMembership(rs.getObject("id", UUID.class), rs.getString("public_membership_id"),
            rs.getString("public_company_id"), rs.getString("user_id"), MembershipStatus.valueOf(rs.getString("status")),
            new HashSet<>(readRoles(rs.getString("roles_json"), rs.getString("public_membership_id"))),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getObject("updated_at", OffsetDateTime.class).toInstant(), rs.getLong("version"));
    }

    private BranchAssignment toAssignment(ResultSet rs, int row) throws SQLException {
        return new BranchAssignment(rs.getObject("id", UUID.class), rs.getString("public_assignment_id"),
            rs.getString("public_membership_id"), rs.getString("public_branch_id"),
            AssignmentStatus.valueOf(rs.getString("status")),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getObject("updated_at", OffsetDateTime.class).toInstant(), rs.getLong("version"));
    }

    /** Un dato dañado no concede nada: se avisa en el registro y la membresía queda sin roles. */
    private List<String> readRoles(String json, String membershipId) {
        try {
            return mapper.readValue(json, TEXT_LIST);
        } catch (Exception e) {
            log.warn("No se pudieron leer los roles de la membresía {} y se tratan como vacíos", membershipId);
            return List.of();
        }
    }

    private String rolesJson(CompanyMembership membership) {
        try {
            return mapper.writeValueAsString(membership.getRoles().stream().sorted().toList());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron guardar los roles de la membresía " + membership.getPublicMembershipId(), e);
        }
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
