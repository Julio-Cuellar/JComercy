package com.jcode.jcomercy.companymanagement.domain.model.employee;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * El enlace con el que una persona activa su acceso. Se guarda solo la huella del código, nunca el código: viaja
 * únicamente en el correo. Sirve una vez y vence. Una persona tiene a lo más una invitación vigente.
 */
public class EmployeeInvitation {
    private final UUID id;
    private final String publicInvitationId;
    private final String publicEmployeeId;
    private final String publicCompanyId;
    private final String tokenHash;
    private InvitationStatus status;
    private EmailStatus emailStatus;
    private final Instant expiresAt;
    private Instant acceptedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;

    public EmployeeInvitation(UUID id, String publicInvitationId, String publicEmployeeId, String publicCompanyId,
                              String tokenHash, InvitationStatus status, EmailStatus emailStatus, Instant expiresAt,
                              Instant acceptedAt, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.publicInvitationId = Objects.requireNonNull(publicInvitationId);
        this.publicEmployeeId = Objects.requireNonNull(publicEmployeeId);
        this.publicCompanyId = Objects.requireNonNull(publicCompanyId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.status = Objects.requireNonNull(status);
        this.emailStatus = Objects.requireNonNull(emailStatus);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.acceptedAt = acceptedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
    }

    public static EmployeeInvitation issue(String publicEmployeeId, String publicCompanyId, String tokenHash, Instant expiresAt) {
        Instant now = Instant.now();
        return new EmployeeInvitation(UUID.randomUUID(), "inv_" + UUID.randomUUID().toString().replace("-", ""),
            publicEmployeeId, publicCompanyId, tokenHash, InvitationStatus.PENDING, EmailStatus.PENDING, expiresAt, null, now, now, 0L);
    }

    /** Si el enlace todavía sirve: vigente, sin usar y sin cancelar. */
    public boolean isUsable(Instant now) {
        return status == InvitationStatus.PENDING && now.isBefore(expiresAt);
    }

    public void accept(Instant now) {
        if (!isUsable(now)) {
            throw new DomainRuleViolationException("Esta invitación ya no sirve.");
        }
        this.status = InvitationStatus.ACCEPTED;
        this.acceptedAt = now;
        this.updatedAt = now;
    }

    public void cancel() {
        if (status != InvitationStatus.PENDING) {
            throw new DomainRuleViolationException("Solo se puede cancelar una invitación pendiente.");
        }
        this.status = InvitationStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public void markEmail(EmailStatus result) {
        this.emailStatus = Objects.requireNonNull(result);
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getPublicInvitationId() { return publicInvitationId; }
    public String getPublicEmployeeId() { return publicEmployeeId; }
    public String getPublicCompanyId() { return publicCompanyId; }
    public String getTokenHash() { return tokenHash; }
    public InvitationStatus getStatus() { return status; }
    public EmailStatus getEmailStatus() { return emailStatus; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
