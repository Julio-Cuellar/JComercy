package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.application.dto.PageDto;
import com.jcode.jcomercy.companymanagement.application.dto.RoleViewDto;
import com.jcode.jcomercy.companymanagement.domain.exception.ConflictException;
import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;
import com.jcode.jcomercy.companymanagement.domain.exception.DuplicateResourceException;
import com.jcode.jcomercy.companymanagement.domain.exception.EntityNotFoundException;
import com.jcode.jcomercy.companymanagement.domain.exception.InvalidRequestException;
import com.jcode.jcomercy.companymanagement.domain.model.company.Company;
import com.jcode.jcomercy.companymanagement.domain.model.role.Role;
import com.jcode.jcomercy.companymanagement.domain.model.role.RoleStatus;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;
import com.jcode.jcomercy.companymanagement.domain.port.MembershipRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.RoleRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Los roles que una empresa puede dar: los del sistema, iguales para todas, y los propios de esa empresa, que ella crea,
 * edita, desactiva y borra. Una empresa nunca ve los roles propios de otra. Quien da un permiso tiene que tenerlo.
 */
public class RoleApplicationService {
    static final int MAX_NAME_LENGTH = 160;
    /** Permisos como los del sistema: minúsculas con puntos ("inventory.stock.read"), una familia ("inventory.*") o "*". */
    private static final Pattern PERMISSION_CODE = Pattern.compile("^(\\*|[a-z0-9_]+(\\.[a-z0-9_]+)*(\\.\\*)?)$");
    private static final Comparator<Role> ORDER = Comparator
        .comparing((Role role) -> !role.isSystemRole()).thenComparing(Role::getName).thenComparing(Role::getPublicRoleId);

    private final RoleRepositoryPort roles;
    private final CompanyRepositoryPort companies;
    private final MembershipRepositoryPort memberships;
    private final EventOutboxPort outbox;
    private final IdempotencyRegistry idempotency;

    public RoleApplicationService(RoleRepositoryPort roles, CompanyRepositoryPort companies,
                                  MembershipRepositoryPort memberships, EventOutboxPort outbox,
                                  IdempotencyRegistry idempotency) {
        this.roles = Objects.requireNonNull(roles);
        this.companies = Objects.requireNonNull(companies);
        this.memberships = Objects.requireNonNull(memberships);
        this.outbox = Objects.requireNonNull(outbox);
        this.idempotency = Objects.requireNonNull(idempotency);
    }

    public PageDto<RoleViewDto> list(String companyId, String type, String status, Integer limit, String after) {
        requireCompany(companyId);
        Boolean wantSystem = parseType(type);
        RoleStatus wantedStatus = parseStatus(status);
        int pageSize = MembershipApplicationService.pageSize(limit);

        List<Role> matching = visibleRoles(companyId).stream()
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
        return RoleViewDto.fromDomain(find(companyId, roleId));
    }

    /**
     * Crea un rol propio. {@code actorCanGive} dice si quien actúa tiene un permiso: nadie puede dar uno que no tiene.
     */
    @Transactional
    public RoleViewDto create(String companyId, String name, List<RoleViewDto.Permission> permissions,
                              Predicate<String> actorCanGive, String actorUserId, String correlationId, String idempotencyKey) {
        return idempotency.execute("role.create:" + companyId, idempotencyKey,
            () -> createInternal(companyId, name, permissions, actorCanGive, actorUserId, correlationId));
    }

    private RoleViewDto createInternal(String companyId, String name, List<RoleViewDto.Permission> permissions,
                                       Predicate<String> actorCanGive, String actorUserId, String correlationId) {
        Company company = requireCompany(companyId);
        String cleanName = cleanName(name);
        Set<String> codes = permissionCodes(permissions);
        requireDelegable(codes, actorCanGive);
        boolean taken = visibleRoles(companyId).stream().anyMatch(role -> role.getName().equalsIgnoreCase(cleanName));
        if (taken) {
            throw new DuplicateResourceException("Rol", "nombre", cleanName);
        }
        Role saved = roles.save(Role.createCustomRole(companyId, cleanName, codes));
        record("role.created", saved, company, actorUserId, correlationId);
        return RoleViewDto.fromDomain(saved);
    }

    /**
     * Cambia los permisos de un rol propio. El nombre no cambia. Si llega la versión que la persona consultó y el rol ya
     * cambió, hay conflicto. Quien edita solo puede agregar permisos que tiene; quitar siempre puede.
     */
    @Transactional
    public RoleViewDto update(String companyId, String roleId, String name, List<RoleViewDto.Permission> permissions,
                              Long expectedVersion, Predicate<String> actorCanGive, String actorUserId, String correlationId) {
        Company company = requireCompany(companyId);
        Role role = find(companyId, roleId);
        if (role.isSystemRole()) {
            throw new DomainRuleViolationException("Los roles del sistema no se pueden modificar.");
        }
        if (name != null && !name.isBlank() && !name.trim().equals(role.getName())) {
            throw new DomainRuleViolationException("El nombre de un rol no se puede cambiar. Crea un rol nuevo con el nombre que quieres.");
        }
        if (expectedVersion != null && expectedVersion != role.getVersion()) {
            throw new ConflictException("El rol cambió desde que lo consultaste. Vuelve a consultarlo y repite el cambio.");
        }
        Set<String> codes = permissionCodes(permissions);
        Set<String> added = new LinkedHashSet<>(codes);
        added.removeAll(role.getPermissions());
        requireDelegable(added, actorCanGive);
        role.updatePermissions(codes);
        Role saved = roles.save(role);
        record("role.updated", saved, company, actorUserId, correlationId);
        return RoleViewDto.fromDomain(saved);
    }

    /** Desactivar le quita sus permisos a quien tiene el rol desde la siguiente petición. */
    @Transactional
    public RoleViewDto deactivate(String companyId, String roleId, String actorUserId, String correlationId, String idempotencyKey) {
        return idempotency.execute("role.deactivate:" + roleId, idempotencyKey, () -> {
            Company company = requireCompany(companyId);
            Role role = find(companyId, roleId);
            role.deactivate();
            Role saved = roles.save(role);
            record("role.deactivated", saved, company, actorUserId, correlationId);
            return RoleViewDto.fromDomain(saved);
        });
    }

    /** Borrado lógico. Un rol que alguien todavía tiene no se borra: antes hay que quitárselo. */
    @Transactional
    public void delete(String companyId, String roleId, Boolean confirmation, String actorUserId, String correlationId,
                       String idempotencyKey) {
        if (!Boolean.TRUE.equals(confirmation)) {
            throw new InvalidRequestException("Confirma el borrado agregando confirmation=true a la petición.");
        }
        idempotency.execute("role.delete:" + roleId, idempotencyKey, () -> {
            Company company = requireCompany(companyId);
            Role role = find(companyId, roleId);
            if (role.isSystemRole()) {
                throw new DomainRuleViolationException("No se puede eliminar un rol del sistema.");
            }
            boolean inUse = memberships.findMembershipsByPublicCompanyId(companyId).stream()
                .anyMatch(membership -> membership.getRoles().contains(role.getName()));
            if (inUse) {
                throw new ConflictException("No puedes eliminar el rol «" + role.getName()
                    + "» porque hay personas que lo tienen. Quítaselo primero o desactívalo.");
            }
            role.markDeleted();
            Role saved = roles.save(role);
            record("role.deleted", saved, company, actorUserId, correlationId);
            return Boolean.TRUE;
        });
    }

    private Company requireCompany(String companyId) {
        return companies.findByPublicCompanyId(companyId).orElseThrow(() -> new EntityNotFoundException("Empresa", companyId));
    }

    /** Los roles del sistema y los de esa empresa que no se han borrado. */
    private List<Role> visibleRoles(String companyId) {
        return roles.findByPublicCompanyIdOrSystem(companyId).stream()
            .filter(role -> role.getStatus() != RoleStatus.DELETED).toList();
    }

    private Role find(String companyId, String roleId) {
        return roles.findByPublicRoleId(roleId)
            .filter(found -> found.getStatus() != RoleStatus.DELETED)
            .filter(found -> found.isSystemRole() || companyId.equals(found.getPublicCompanyId()))
            .orElseThrow(() -> new EntityNotFoundException("Rol", roleId));
    }

    private static String cleanName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("Escribe el nombre del rol.");
        }
        String clean = name.trim();
        if (clean.length() > MAX_NAME_LENGTH) {
            throw new InvalidRequestException("El nombre del rol puede tener hasta " + MAX_NAME_LENGTH + " caracteres.");
        }
        return clean;
    }

    /**
     * Los códigos de permiso de la petición, ya validados. El contrato permite limitar un permiso a sucursales, pero el
     * modelo de roles todavía no: la sucursal se le asigna a la persona, no al permiso.
     */
    private static Set<String> permissionCodes(List<RoleViewDto.Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new InvalidRequestException("Elige al menos un permiso para el rol.");
        }
        Set<String> codes = new LinkedHashSet<>();
        for (RoleViewDto.Permission permission : permissions) {
            String code = permission == null || permission.permissionCode() == null ? "" : permission.permissionCode().trim();
            if (!PERMISSION_CODE.matcher(code).matches()) {
                throw new InvalidRequestException("El permiso '" + code + "' no es válido. Usa minúsculas separadas por puntos, por ejemplo inventory.stock.read.");
            }
            codes.add(code);
        }
        for (RoleViewDto.Permission permission : permissions) {
            if (permission.branchIds() != null && !permission.branchIds().isEmpty()) {
                throw new DomainRuleViolationException("Los permisos de un rol todavía no se pueden limitar por sucursal. La sucursal se asigna a cada persona.");
            }
        }
        if (codes.contains("*")) {
            throw new DomainRuleViolationException("Un rol propio no puede dar todos los permisos (*). Elige los que necesita.");
        }
        return codes;
    }

    private static void requireDelegable(Set<String> codes, Predicate<String> actorCanGive) {
        for (String code : codes) {
            if (!actorCanGive.test(code)) {
                throw new DomainRuleViolationException("No puedes dar el permiso " + code + " porque tu rol no lo tiene.");
            }
        }
    }

    private void record(String type, Role role, Company company, String actorUserId, String correlationId) {
        outbox.recordEvent(type, "1.0", role.getVersion(), "company_management",
            correlationId != null ? correlationId : "cor_" + UUID.randomUUID().toString().replace("-", ""),
            null, company.getPublicAccountId(), company.getPublicCompanyId(), null, actorUserId,
            Map.of("roleId", role.getPublicRoleId(),
                "companyId", company.getPublicCompanyId(),
                "name", role.getName(),
                "permissions", role.getPermissions().stream().sorted().toList(),
                "status", role.getStatus() == RoleStatus.DELETED ? RoleStatus.INACTIVE.name() : role.getStatus().name()));
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
        return switch (status.trim().toUpperCase()) {
            case "ACTIVE" -> RoleStatus.ACTIVE;
            case "INACTIVE" -> RoleStatus.INACTIVE;
            default -> throw new InvalidRequestException("El estado '" + status + "' no existe. Usa ACTIVE o INACTIVE.");
        };
    }
}
