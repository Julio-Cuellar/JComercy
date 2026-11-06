package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.application.dto.AssignmentDto;
import com.jcode.jcomercy.companymanagement.application.dto.PageDto;
import com.jcode.jcomercy.companymanagement.domain.exception.ConflictException;
import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;
import com.jcode.jcomercy.companymanagement.domain.exception.EntityNotFoundException;
import com.jcode.jcomercy.companymanagement.domain.exception.InvalidRequestException;
import com.jcode.jcomercy.companymanagement.domain.model.assignment.AssignmentStatus;
import com.jcode.jcomercy.companymanagement.domain.model.assignment.BranchAssignment;
import com.jcode.jcomercy.companymanagement.domain.model.company.Company;
import com.jcode.jcomercy.companymanagement.domain.model.membership.CompanyMembership;
import com.jcode.jcomercy.companymanagement.domain.model.membership.MembershipStatus;
import com.jcode.jcomercy.companymanagement.domain.model.role.Role;
import com.jcode.jcomercy.companymanagement.domain.port.BranchRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;
import com.jcode.jcomercy.companymanagement.domain.port.MembershipRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.RoleRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Las sucursales de una persona de la empresa: agregar, suspender y quitar. Los roles son de la persona, no de cada
 * sucursal. Una persona con la membresía activa no se queda sin su última sucursal activa: para cortarle el acceso se
 * suspende o se revoca su membresía. Lo que se quita no se borra: queda como retirado.
 */
public class AssignmentApplicationService {

    private final MembershipRepositoryPort memberships;
    private final BranchRepositoryPort branches;
    private final CompanyRepositoryPort companies;
    private final RoleRepositoryPort roles;
    private final EventOutboxPort outbox;
    private final IdempotencyRegistry idempotency;

    public AssignmentApplicationService(MembershipRepositoryPort memberships, BranchRepositoryPort branches,
                                        CompanyRepositoryPort companies, RoleRepositoryPort roles, EventOutboxPort outbox,
                                        IdempotencyRegistry idempotency) {
        this.memberships = Objects.requireNonNull(memberships);
        this.branches = Objects.requireNonNull(branches);
        this.companies = Objects.requireNonNull(companies);
        this.roles = Objects.requireNonNull(roles);
        this.outbox = Objects.requireNonNull(outbox);
        this.idempotency = Objects.requireNonNull(idempotency);
    }

    public PageDto<AssignmentDto> list(String companyId, String membershipId) {
        requireCompany(companyId);
        CompanyMembership membership = membership(companyId, membershipId);
        List<String> roleIds = roleIdsOf(membership, companyId);
        List<AssignmentDto> items = memberships.findAssignmentsByPublicMembershipId(membershipId).stream()
            .filter(assignment -> assignment.getStatus() != AssignmentStatus.REMOVED)
            .map(assignment -> dto(assignment, roleIds)).toList();
        return new PageDto<>(items, new PageDto.Page(MembershipApplicationService.MAX_LIMIT, null));
    }

    /**
     * Agrega una sucursal a la persona. Si la había tenido y se retiró, se vuelve a dar la misma. {@code roleIds} y
     * {@code permissions} solo se aceptan si no piden nada distinto de lo que la persona ya tiene.
     */
    @Transactional
    public AssignmentDto create(String companyId, String membershipId, String branchId, List<String> roleIds,
                                List<String> permissions, String status, String actorUserId, String correlationId,
                                String idempotencyKey) {
        return idempotency.execute("assignment.create:" + membershipId, idempotencyKey, () -> {
            Company company = requireCompany(companyId);
            CompanyMembership membership = membership(companyId, membershipId);
            if (branchId == null || branchId.isBlank()) {
                throw new InvalidRequestException("Elige la sucursal que quieres agregar.");
            }
            AssignmentStatus wanted = parseStatus(status, AssignmentStatus.ACTIVE);
            List<String> ownRoleIds = roleIdsOf(membership, companyId);
            requireNothingPerBranch(roleIds, permissions, ownRoleIds);
            if (membership.getStatus() == MembershipStatus.REVOKED) {
                throw new DomainRuleViolationException("El acceso de esta persona está revocado. No se le pueden agregar sucursales.");
            }
            if (branches.findByPublicCompanyIdAndPublicBranchId(companyId, branchId).isEmpty()) {
                throw new DomainRuleViolationException("La sucursal '" + branchId + "' no existe en tu empresa.");
            }
            Optional<BranchAssignment> existing = memberships.findAssignmentsByPublicMembershipId(membershipId).stream()
                .filter(assignment -> assignment.getPublicBranchId().equals(branchId)).findFirst();
            BranchAssignment assignment;
            if (existing.isPresent()) {
                if (existing.get().getStatus() != AssignmentStatus.REMOVED) {
                    throw new ConflictException("Esta persona ya tiene esa sucursal.");
                }
                assignment = existing.get();
                assignment.restore(wanted);
            } else {
                assignment = BranchAssignment.create(membershipId, branchId);
                if (wanted == AssignmentStatus.SUSPENDED) {
                    assignment.suspend();
                }
            }
            BranchAssignment saved = memberships.saveAssignment(assignment);
            AssignmentEvents.record(outbox, "branch-assignment.created", saved, company, actorUserId, correlationId, true);
            return dto(saved, ownRoleIds);
        });
    }

    /** Suspende o reactiva una sucursal. La sucursal de una asignación no cambia: se quita y se agrega la otra. */
    @Transactional
    public AssignmentDto update(String companyId, String membershipId, String assignmentId, String branchId, String status,
                                Long expectedVersion, String actorUserId, String correlationId) {
        Company company = requireCompany(companyId);
        CompanyMembership membership = membership(companyId, membershipId);
        BranchAssignment assignment = assignment(membershipId, assignmentId);
        if (status == null || status.isBlank()) {
            throw new InvalidRequestException("Indica el estado de la sucursal: ACTIVE o SUSPENDED.");
        }
        AssignmentStatus wanted = parseStatus(status, null);
        if (branchId != null && !branchId.isBlank() && !branchId.equals(assignment.getPublicBranchId())) {
            throw new DomainRuleViolationException("La sucursal de una asignación no se puede cambiar. Quita esta y agrega la otra.");
        }
        if (expectedVersion != null && expectedVersion != assignment.getVersion()) {
            throw new ConflictException("La sucursal cambió desde que la consultaste. Vuelve a consultarla y repite el cambio.");
        }
        if (wanted == AssignmentStatus.SUSPENDED && assignment.getStatus() == AssignmentStatus.ACTIVE) {
            requireNotTheLastActive(membership, assignment);
            assignment.suspend();
        } else if (wanted == AssignmentStatus.ACTIVE) {
            assignment.reactivate();
        }
        BranchAssignment saved = memberships.saveAssignment(assignment);
        AssignmentEvents.record(outbox, "branch-assignment.updated", saved, company, actorUserId, correlationId, true);
        return dto(saved, roleIdsOf(membership, companyId));
    }

    /** Quita la sucursal de la persona (queda como retirada). */
    @Transactional
    public void remove(String companyId, String membershipId, String assignmentId, String actorUserId, String correlationId,
                       String idempotencyKey) {
        idempotency.execute("assignment.remove:" + assignmentId, idempotencyKey, () -> {
            Company company = requireCompany(companyId);
            CompanyMembership membership = membership(companyId, membershipId);
            BranchAssignment assignment = assignment(membershipId, assignmentId);
            if (assignment.getStatus() == AssignmentStatus.ACTIVE) {
                requireNotTheLastActive(membership, assignment);
            }
            assignment.remove();
            BranchAssignment saved = memberships.saveAssignment(assignment);
            AssignmentEvents.record(outbox, "branch-assignment.revoked", saved, company, actorUserId, correlationId, false);
            return Boolean.TRUE;
        });
    }

    /** A una persona con acceso no se le quita la última sucursal activa: se le suspende o revoca el acceso. */
    private void requireNotTheLastActive(CompanyMembership membership, BranchAssignment assignment) {
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            return;
        }
        boolean otherActive = memberships.findAssignmentsByPublicMembershipId(membership.getPublicMembershipId()).stream()
            .anyMatch(other -> !other.getPublicAssignmentId().equals(assignment.getPublicAssignmentId())
                && other.getStatus() == AssignmentStatus.ACTIVE);
        if (!otherActive) {
            throw new ConflictException("Es la última sucursal activa de esta persona. Si quieres cortarle el acceso, suspende su membresía en vez de quitarle la sucursal.");
        }
    }

    private static void requireNothingPerBranch(List<String> roleIds, List<String> permissions, List<String> ownRoleIds) {
        if (permissions != null && !permissions.isEmpty()) {
            throw new DomainRuleViolationException("Los permisos todavía no se pueden dar por sucursal. Se dan con el rol de la persona.");
        }
        if (roleIds != null && !roleIds.isEmpty() && !new HashSet<>(roleIds).equals(new HashSet<>(ownRoleIds))) {
            throw new DomainRuleViolationException("Los roles son de la persona, no de cada sucursal. Cambia sus roles en su membresía.");
        }
    }

    private CompanyMembership membership(String companyId, String membershipId) {
        return memberships.findMembershipByPublicId(membershipId)
            .filter(found -> found.getPublicCompanyId().equals(companyId))
            .orElseThrow(() -> new EntityNotFoundException("Membresía", membershipId));
    }

    private BranchAssignment assignment(String membershipId, String assignmentId) {
        return memberships.findAssignmentByPublicId(assignmentId)
            .filter(found -> found.getPublicMembershipId().equals(membershipId))
            .filter(found -> found.getStatus() != AssignmentStatus.REMOVED)
            .orElseThrow(() -> new EntityNotFoundException("Asignación", assignmentId));
    }

    private Company requireCompany(String companyId) {
        return companies.findByPublicCompanyId(companyId).orElseThrow(() -> new EntityNotFoundException("Empresa", companyId));
    }

    /** Los identificadores de los roles de la persona (la membresía guarda los nombres). */
    private List<String> roleIdsOf(CompanyMembership membership, String companyId) {
        Set<String> names = membership.getRoles();
        return roles.findByPublicCompanyIdOrSystem(companyId).stream()
            .filter(role -> names.contains(role.getName()))
            .filter(role -> role.getStatus() != com.jcode.jcomercy.companymanagement.domain.model.role.RoleStatus.DELETED)
            .map(Role::getPublicRoleId).sorted().toList();
    }

    private static AssignmentStatus parseStatus(String status, AssignmentStatus whenMissing) {
        if (status == null || status.isBlank()) {
            return whenMissing;
        }
        return switch (status.trim().toUpperCase()) {
            case "ACTIVE" -> AssignmentStatus.ACTIVE;
            case "SUSPENDED" -> AssignmentStatus.SUSPENDED;
            default -> throw new InvalidRequestException("El estado '" + status + "' no existe. Usa ACTIVE o SUSPENDED.");
        };
    }

    private static AssignmentDto dto(BranchAssignment assignment, List<String> roleIds) {
        return new AssignmentDto(assignment.getPublicAssignmentId(), assignment.getPublicMembershipId(),
            assignment.getPublicBranchId(), roleIds, assignment.getStatus().name(), assignment.getVersion());
    }
}
