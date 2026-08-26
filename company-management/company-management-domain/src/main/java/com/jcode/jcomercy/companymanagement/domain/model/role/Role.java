package com.jcode.jcomercy.companymanagement.domain.model.role;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;

import java.time.Instant;
import java.util.*;

public class Role {
    private final UUID id;
    private final String publicRoleId;
    private final String publicCompanyId; // null if system role
    private final String name;
    private final boolean isSystemRole;
    private RoleStatus status;
    private final Set<String> permissions;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Role(UUID id, String publicRoleId, String publicCompanyId, String name, boolean isSystemRole, RoleStatus status, Set<String> permissions, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id no puede ser nulo");
        this.publicRoleId = Objects.requireNonNull(publicRoleId, "publicRoleId no puede ser nulo");
        this.publicCompanyId = publicCompanyId;
        this.name = validateName(name);
        this.isSystemRole = isSystemRole;
        this.status = Objects.requireNonNull(status, "status no puede ser nulo");
        this.permissions = permissions != null ? new HashSet<>(permissions) : new HashSet<>();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt no puede ser nulo");
        this.version = version;
    }

    public static Role createSystemRole(String name, Set<String> permissions) {
        Instant now = Instant.now();
        String publicId = "rol_sys_" + name.toLowerCase().replace(" ", "_");
        return new Role(UUID.randomUUID(), publicId, null, name, true, RoleStatus.ACTIVE, permissions, now, now, 0L);
    }

    public static Role createCustomRole(String publicCompanyId, String name, Set<String> permissions) {
        Instant now = Instant.now();
        String publicId = "rol_" + UUID.randomUUID().toString().replace("-", "");
        return new Role(UUID.randomUUID(), publicId, Objects.requireNonNull(publicCompanyId), name, false, RoleStatus.ACTIVE, permissions, now, now, 0L);
    }

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new DomainRuleViolationException("El nombre del rol no puede estar vacío.");
        }
        return name.trim();
    }

    public void updatePermissions(Set<String> newPermissions) {
        if (isSystemRole) {
            throw new DomainRuleViolationException("No se pueden modificar los permisos de un rol del sistema.");
        }
        this.permissions.clear();
        if (newPermissions != null) {
            this.permissions.addAll(newPermissions);
        }
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        if (isSystemRole) {
            throw new DomainRuleViolationException("No se puede desactivar un rol del sistema.");
        }
        this.status = RoleStatus.INACTIVE;
        this.updatedAt = Instant.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getPublicRoleId() { return publicRoleId; }
    public String getPublicCompanyId() { return publicCompanyId; }
    public String getName() { return name; }
    public boolean isSystemRole() { return isSystemRole; }
    public RoleStatus getStatus() { return status; }
    public Set<String> getPermissions() { return Collections.unmodifiableSet(permissions); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
