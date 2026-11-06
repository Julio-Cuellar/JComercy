package com.jcode.jcomercy.companymanagement.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * Lo que una persona puede hacer hoy en una empresa. {@code branchIds} vacío significa «en toda la empresa». El contrato
 * también define {@code permissionsVersion}, pero la versión de los permisos todavía no existe y no se publica.
 */
public record EffectivePermissionsDto(
    String userId,
    String companyId,
    List<Permission> permissions,
    Instant calculatedAt
) {

    public record Permission(String permissionCode, List<String> branchIds) { }
}
