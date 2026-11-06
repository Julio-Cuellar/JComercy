package com.jcode.jcomercy.companymanagement.application.dto;

import com.jcode.jcomercy.companymanagement.domain.model.role.Role;

import java.util.List;

/** Un rol como lo ve el contrato: del sistema o propio de la empresa, con sus permisos. */
public record RoleViewDto(
    String roleId,
    String name,
    String type,
    String status,
    List<Permission> permissions,
    long version
) {

    public record Permission(String permissionCode, List<String> branchIds) { }

    public static RoleViewDto fromDomain(Role role) {
        List<Permission> permissions = role.getPermissions().stream().sorted()
            .map(code -> new Permission(code, List.of())).toList();
        return new RoleViewDto(role.getPublicRoleId(), role.getName(), role.isSystemRole() ? "SYSTEM" : "CUSTOM",
            role.getStatus().name(), permissions, role.getVersion());
    }
}
