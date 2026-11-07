package com.jcode.jcomercy.companymanagement.infra.persistence.adapter;

import com.jcode.jcomercy.companymanagement.domain.model.employee.EmailStatus;
import com.jcode.jcomercy.companymanagement.domain.model.employee.EmployeeInvitation;
import com.jcode.jcomercy.companymanagement.domain.model.employee.InvitationStatus;
import com.jcode.jcomercy.companymanagement.domain.port.EmployeeInvitationRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Las invitaciones en {@code company_management.employee_invitation}, con SQL directo. Solo guardan la huella del código. */
@Component
public class JdbcEmployeeInvitationRepositoryAdapter implements EmployeeInvitationRepositoryPort {
    private static final String COLUMNS = """
        id, public_invitation_id, public_employee_id, public_company_id, token_hash, status, email_status, expires_at,
        accepted_at, created_at, updated_at, version
        """;

    private final JdbcTemplate jdbc;
    private final RowMapper<EmployeeInvitation> rowMapper = this::toInvitation;

    public JdbcEmployeeInvitationRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EmployeeInvitation save(EmployeeInvitation invitation) {
        int updated = jdbc.update("""
            UPDATE company_management.employee_invitation
            SET status=?, email_status=?, accepted_at=?, updated_at=?, version=version+1 WHERE id=?
            """, invitation.getStatus().name(), invitation.getEmailStatus().name(), at(invitation.getAcceptedAt()),
            at(invitation.getUpdatedAt()), invitation.getId());
        if (updated == 0) {
            jdbc.update("""
                INSERT INTO company_management.employee_invitation
                  (id, public_invitation_id, public_employee_id, public_company_id, token_hash, status, email_status, expires_at,
                   accepted_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, invitation.getId(), invitation.getPublicInvitationId(), invitation.getPublicEmployeeId(),
                invitation.getPublicCompanyId(), invitation.getTokenHash(), invitation.getStatus().name(),
                invitation.getEmailStatus().name(), at(invitation.getExpiresAt()), at(invitation.getAcceptedAt()),
                at(invitation.getCreatedAt()), at(invitation.getUpdatedAt()));
        }
        return jdbc.query("SELECT " + COLUMNS + " FROM company_management.employee_invitation WHERE id=?", rowMapper, invitation.getId())
            .stream().findFirst().orElseThrow();
    }

    @Override
    public Optional<EmployeeInvitation> findByPublicInvitationId(String publicInvitationId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM company_management.employee_invitation WHERE public_invitation_id=?",
            rowMapper, publicInvitationId).stream().findFirst();
    }

    @Override
    public Optional<EmployeeInvitation> findByTokenHash(String tokenHash) {
        return jdbc.query("SELECT " + COLUMNS + " FROM company_management.employee_invitation WHERE token_hash=?",
            rowMapper, tokenHash).stream().findFirst();
    }

    @Override
    public List<EmployeeInvitation> findByPublicEmployeeId(String publicEmployeeId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM company_management.employee_invitation WHERE public_employee_id=? ORDER BY created_at DESC, public_invitation_id DESC",
            rowMapper, publicEmployeeId);
    }

    private EmployeeInvitation toInvitation(ResultSet rs, int row) throws SQLException {
        OffsetDateTime acceptedAt = rs.getObject("accepted_at", OffsetDateTime.class);
        return new EmployeeInvitation(rs.getObject("id", UUID.class), rs.getString("public_invitation_id"),
            rs.getString("public_employee_id"), rs.getString("public_company_id"), rs.getString("token_hash"),
            InvitationStatus.valueOf(rs.getString("status")), EmailStatus.valueOf(rs.getString("email_status")),
            rs.getObject("expires_at", OffsetDateTime.class).toInstant(), acceptedAt == null ? null : acceptedAt.toInstant(),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getObject("updated_at", OffsetDateTime.class).toInstant(), rs.getLong("version"));
    }

    private static OffsetDateTime at(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
