package com.jcode.jcomercy.companymanagement.application.dto;

import com.jcode.jcomercy.companymanagement.domain.model.role.Role;

import java.time.Instant;
import java.util.Set;

public record RoleDto(
    String publicRoleId,
    String publicCompanyId,
    String name,
    boolean isSystemRole,
    String status,
    Set<String> permissions,
    Instant createdAt,
    Instant updatedAt,
    long version
) {
    public static RoleDto fromDomain(Role role) {
        return new RoleDto(
            role.getPublicRoleId(),
            role.getPublicCompanyId(),
            role.getName(),
            role.isSystemRole(),
            role.getStatus().name(),
            role.getPermissions(),
            role.getCreatedAt(),
            role.getUpdatedAt(),
            role.getVersion()
        );
    }
}
