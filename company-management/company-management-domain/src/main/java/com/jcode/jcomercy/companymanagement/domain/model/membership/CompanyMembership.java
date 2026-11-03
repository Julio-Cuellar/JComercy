package com.jcode.jcomercy.companymanagement.domain.model.membership;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;

import java.time.Instant;
import java.util.*;

public class CompanyMembership {
    private final UUID id;
    private final String publicMembershipId;
    private final String publicCompanyId;
    private final String userId;
    private MembershipStatus status;
    private final Set<String> roles;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public CompanyMembership(UUID id, String publicMembershipId, String publicCompanyId, String userId, MembershipStatus status, Set<String> roles, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id no puede ser nulo");
        this.publicMembershipId = Objects.requireNonNull(publicMembershipId, "publicMembershipId no puede ser nulo");
        this.publicCompanyId = Objects.requireNonNull(publicCompanyId, "publicCompanyId no puede ser nulo");
        this.userId = Objects.requireNonNull(userId, "userId no puede ser nulo");
        this.status = Objects.requireNonNull(status, "status no puede ser nulo");
        this.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt no puede ser nulo");
        this.version = version;
    }

    public static CompanyMembership create(String publicCompanyId, String userId, Set<String> initialRoles) {
        Instant now = Instant.now();
        String publicId = "mem_" + UUID.randomUUID().toString().replace("-", "");
        return new CompanyMembership(UUID.randomUUID(), publicId, publicCompanyId, userId, MembershipStatus.ACTIVE, initialRoles, now, now, 0L);
    }

    public void assignRole(String roleName) {
        if (roleName != null && !roleName.trim().isEmpty()) {
            this.roles.add(roleName.trim());
            this.updatedAt = Instant.now();
        }
    }

    public void removeRole(String roleName) {
        if (this.roles.remove(roleName)) {
            this.updatedAt = Instant.now();
        }
    }

    public void suspend() {
        if (this.status == MembershipStatus.REVOKED) {
            throw new DomainRuleViolationException("No se puede suspender una membresía revocada.");
        }
        this.status = MembershipStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        if (this.status != MembershipStatus.SUSPENDED) {
            throw new DomainRuleViolationException("Solo se pueden reactivar membresías suspendidas.");
        }
        this.status = MembershipStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void revoke() {
        this.status = MembershipStatus.REVOKED;
        this.updatedAt = Instant.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getPublicMembershipId() { return publicMembershipId; }
    public String getPublicCompanyId() { return publicCompanyId; }
    public String getUserId() { return userId; }
    public MembershipStatus getStatus() { return status; }
    public Set<String> getRoles() { return Collections.unmodifiableSet(roles); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
