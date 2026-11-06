package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.application.dto.MembershipDetailDto;
import com.jcode.jcomercy.companymanagement.application.dto.MembershipSummaryDto;
import com.jcode.jcomercy.companymanagement.application.dto.PageDto;
import com.jcode.jcomercy.companymanagement.domain.exception.EntityNotFoundException;
import com.jcode.jcomercy.companymanagement.domain.exception.InvalidRequestException;
import com.jcode.jcomercy.companymanagement.domain.model.assignment.AssignmentStatus;
import com.jcode.jcomercy.companymanagement.domain.model.assignment.BranchAssignment;
import com.jcode.jcomercy.companymanagement.domain.model.membership.CompanyMembership;
import com.jcode.jcomercy.companymanagement.domain.model.membership.MembershipStatus;
import com.jcode.jcomercy.companymanagement.domain.model.role.Role;
import com.jcode.jcomercy.companymanagement.domain.model.role.RoleStatus;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.MembershipRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.RoleRepositoryPort;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Las membresías de una empresa: quién pertenece a ella, con qué roles y en qué sucursales. Esta parte solo lee; las
 * membresías las crea la activación de una invitación. Lo que se devuelve nunca incluye datos de acceso.
 */
public class MembershipApplicationService {
    static final int DEFAULT_LIMIT = 25;
    static final int MAX_LIMIT = 100;
    private static final Comparator<CompanyMembership> ORDER = Comparator
        .comparing(CompanyMembership::getCreatedAt).thenComparing(CompanyMembership::getPublicMembershipId);

    private final MembershipRepositoryPort memberships;
    private final RoleRepositoryPort roles;
    private final CompanyRepositoryPort companies;

    public MembershipApplicationService(MembershipRepositoryPort memberships, RoleRepositoryPort roles,
                                        CompanyRepositoryPort companies) {
        this.memberships = Objects.requireNonNull(memberships);
        this.roles = Objects.requireNonNull(roles);
        this.companies = Objects.requireNonNull(companies);
    }

    public PageDto<MembershipSummaryDto> list(String companyId, String status, String branchId, String roleId,
                                              Integer limit, String after) {
        requireCompany(companyId);
        MembershipStatus wantedStatus = parseStatus(status);
        int pageSize = pageSize(limit);
        PageCursor cursor = PageCursor.decode(after);
        List<Role> visibleRoles = roles.findByPublicCompanyIdOrSystem(companyId);
        String wantedRoleName = roleId == null ? null : visibleRoles.stream()
            .filter(role -> role.getPublicRoleId().equals(roleId)).map(Role::getName).findFirst().orElse(null);
        Set<String> inBranch = branchId == null ? null : membershipsAssignedTo(companyId, branchId);

        List<CompanyMembership> matching = memberships.findMembershipsByPublicCompanyId(companyId).stream()
            .sorted(ORDER)
            .filter(membership -> cursor == null || cursor.isBefore(membership.getCreatedAt(), membership.getPublicMembershipId()))
            .filter(membership -> wantedStatus == null || membership.getStatus() == wantedStatus)
            .filter(membership -> roleId == null || (wantedRoleName != null && membership.getRoles().contains(wantedRoleName)))
            .filter(membership -> inBranch == null || inBranch.contains(membership.getPublicMembershipId()))
            .toList();

        boolean hasMore = matching.size() > pageSize;
        List<CompanyMembership> pageItems = hasMore ? matching.subList(0, pageSize) : matching;
        String next = null;
        if (hasMore) {
            CompanyMembership last = pageItems.get(pageItems.size() - 1);
            next = new PageCursor(last.getCreatedAt(), last.getPublicMembershipId()).encode();
        }
        Map<String, String> roleIdByName = roleIdsByName(visibleRoles);
        return new PageDto<>(pageItems.stream().map(membership -> summary(membership, roleIdByName)).toList(),
            new PageDto.Page(pageSize, next));
    }

    public MembershipDetailDto get(String companyId, String membershipId) {
        requireCompany(companyId);
        CompanyMembership membership = memberships.findMembershipByPublicId(membershipId)
            .filter(found -> found.getPublicCompanyId().equals(companyId))
            .orElseThrow(() -> new EntityNotFoundException("Membresía", membershipId));
        Map<String, String> roleIdByName = roleIdsByName(roles.findByPublicCompanyIdOrSystem(companyId));
        List<MembershipDetailDto.AssignmentSummary> assignments = memberships
            .findAssignmentsByPublicMembershipId(membershipId).stream()
            .map(assignment -> new MembershipDetailDto.AssignmentSummary(assignment.getPublicAssignmentId(),
                assignment.getPublicBranchId(), assignment.getStatus().name()))
            .toList();
        return new MembershipDetailDto(membership.getPublicMembershipId(), membership.getUserId(),
            membership.getPublicCompanyId(), membership.getStatus().name(), roleIds(membership, roleIdByName),
            assignments, membership.getCreatedAt(), membership.getUpdatedAt(), membership.getVersion());
    }

    /** Las sucursales que la persona tiene asignadas y activas en esa empresa; vacío si no tiene membresía activa. */
    public List<String> activeBranchIds(String companyId, String userId) {
        return memberships.findMembershipByCompanyAndUser(companyId, userId)
            .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
            .map(membership -> memberships.findAssignmentsByPublicMembershipId(membership.getPublicMembershipId()).stream()
                .filter(assignment -> assignment.getStatus() == AssignmentStatus.ACTIVE)
                .map(BranchAssignment::getPublicBranchId).sorted().toList())
            .orElse(List.of());
    }

    private Set<String> membershipsAssignedTo(String companyId, String branchId) {
        Set<String> ids = new HashSet<>();
        for (BranchAssignment assignment : memberships.findAssignmentsByPublicCompanyId(companyId)) {
            if (assignment.getPublicBranchId().equals(branchId)) {
                ids.add(assignment.getPublicMembershipId());
            }
        }
        return ids;
    }

    private void requireCompany(String companyId) {
        companies.findByPublicCompanyId(companyId).orElseThrow(() -> new EntityNotFoundException("Empresa", companyId));
    }

    private static MembershipSummaryDto summary(CompanyMembership membership, Map<String, String> roleIdByName) {
        return new MembershipSummaryDto(membership.getPublicMembershipId(), membership.getUserId(),
            membership.getPublicCompanyId(), membership.getStatus().name(), roleIds(membership, roleIdByName),
            membership.getVersion());
    }

    /** Los roles de la membresía guardan su nombre; el contrato los publica por identificador. Un nombre sin rol se omite. */
    private static List<String> roleIds(CompanyMembership membership, Map<String, String> roleIdByName) {
        return membership.getRoles().stream().map(roleIdByName::get).filter(Objects::nonNull).sorted().toList();
    }

    private static Map<String, String> roleIdsByName(List<Role> visibleRoles) {
        Map<String, String> byName = new HashMap<>();
        for (Role role : visibleRoles) {
            if (role.getStatus() != RoleStatus.DELETED) {
                byName.putIfAbsent(role.getName(), role.getPublicRoleId());
            }
        }
        return byName;
    }

    private static MembershipStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return MembershipStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("El estado '" + status + "' no existe. Usa PENDING, ACTIVE, SUSPENDED o REVOKED.");
        }
    }

    static int pageSize(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("El límite debe estar entre 1 y " + MAX_LIMIT + ".");
        }
        return limit;
    }
}
