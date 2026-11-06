package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.application.dto.PageDto;
import com.jcode.jcomercy.companymanagement.application.dto.RoleViewDto;
import com.jcode.jcomercy.companymanagement.domain.exception.EntityNotFoundException;
import com.jcode.jcomercy.companymanagement.domain.exception.InvalidRequestException;
import com.jcode.jcomercy.companymanagement.domain.model.role.Role;
import com.jcode.jcomercy.companymanagement.domain.model.role.RoleStatus;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.RoleRepositoryPort;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Los roles que una empresa puede dar: los del sistema, iguales para todas, y los propios de esa empresa. Una empresa
 * nunca ve los roles propios de otra.
 */
public class RoleApplicationService {
    private static final Comparator<Role> ORDER = Comparator
        .comparing((Role role) -> !role.isSystemRole()).thenComparing(Role::getName).thenComparing(Role::getPublicRoleId);

    private final RoleRepositoryPort roles;
    private final CompanyRepositoryPort companies;

    public RoleApplicationService(RoleRepositoryPort roles, CompanyRepositoryPort companies) {
        this.roles = Objects.requireNonNull(roles);
        this.companies = Objects.requireNonNull(companies);
    }

    public PageDto<RoleViewDto> list(String companyId, String type, String status, Integer limit, String after) {
        requireCompany(companyId);
        Boolean wantSystem = parseType(type);
        RoleStatus wantedStatus = parseStatus(status);
        int pageSize = MembershipApplicationService.pageSize(limit);

        List<Role> matching = roles.findByPublicCompanyIdOrSystem(companyId).stream()
            .sorted(ORDER)
            .filter(role -> wantSystem == null || role.isSystemRole() == wantSystem)
            .filter(role -> wantedStatus == null || role.getStatus() == wantedStatus)
            .toList();
        int start = 0;
        if (after != null && !after.isBlank()) {
            start = indexAfter(matching, after);
        }
        int end = Math.min(matching.size(), start + pageSize);
        String next = end < matching.size() ? matching.get(end - 1).getPublicRoleId() : null;
        return new PageDto<>(matching.subList(start, end).stream().map(RoleViewDto::fromDomain).toList(),
            new PageDto.Page(pageSize, next));
    }

    public RoleViewDto get(String companyId, String roleId) {
        requireCompany(companyId);
        Role role = roles.findByPublicRoleId(roleId)
            .filter(found -> found.isSystemRole() || companyId.equals(found.getPublicCompanyId()))
            .orElseThrow(() -> new EntityNotFoundException("Rol", roleId));
        return RoleViewDto.fromDomain(role);
    }

    private void requireCompany(String companyId) {
        companies.findByPublicCompanyId(companyId).orElseThrow(() -> new EntityNotFoundException("Empresa", companyId));
    }

    private static int indexAfter(List<Role> sorted, String cursor) {
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getPublicRoleId().equals(cursor)) {
                return i + 1;
            }
        }
        throw new InvalidRequestException("El cursor 'after' no es válido. Usa el nextCursor de la página anterior.");
    }

    private static Boolean parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return switch (type.trim().toUpperCase()) {
            case "SYSTEM" -> Boolean.TRUE;
            case "CUSTOM" -> Boolean.FALSE;
            default -> throw new InvalidRequestException("El tipo '" + type + "' no existe. Usa SYSTEM o CUSTOM.");
        };
    }

    private static RoleStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return RoleStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("El estado '" + status + "' no existe. Usa ACTIVE o INACTIVE.");
        }
    }
}
