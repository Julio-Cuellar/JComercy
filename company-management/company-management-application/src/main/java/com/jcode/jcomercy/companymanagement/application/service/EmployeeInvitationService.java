package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.application.dto.InvitationPreviewDto;
import com.jcode.jcomercy.companymanagement.application.dto.InvitationSummaryDto;
import com.jcode.jcomercy.companymanagement.domain.exception.AuthenticationRequiredException;
import com.jcode.jcomercy.companymanagement.domain.exception.ConflictException;
import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;
import com.jcode.jcomercy.companymanagement.domain.exception.DuplicateResourceException;
import com.jcode.jcomercy.companymanagement.domain.exception.EntityNotFoundException;
import com.jcode.jcomercy.companymanagement.domain.exception.UnauthorizedAccessException;
import com.jcode.jcomercy.companymanagement.domain.model.account.Account;
import com.jcode.jcomercy.companymanagement.domain.model.assignment.BranchAssignment;
import com.jcode.jcomercy.companymanagement.domain.model.company.Company;
import com.jcode.jcomercy.companymanagement.domain.model.company.CompanyStatus;
import com.jcode.jcomercy.companymanagement.domain.model.employee.EmployeeInvitation;
import com.jcode.jcomercy.companymanagement.domain.model.employee.EmployeeProfile;
import com.jcode.jcomercy.companymanagement.domain.model.employee.EmployeeStatus;
import com.jcode.jcomercy.companymanagement.domain.model.employee.InvitationStatus;
import com.jcode.jcomercy.companymanagement.domain.model.membership.CompanyMembership;
import com.jcode.jcomercy.companymanagement.domain.model.role.Role;
import com.jcode.jcomercy.companymanagement.domain.model.role.RoleStatus;
import com.jcode.jcomercy.companymanagement.domain.port.AccountRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.BranchRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EmployeeInvitationRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EmployeeProfileRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;
import com.jcode.jcomercy.companymanagement.domain.port.InvitationDeliveryPort;
import com.jcode.jcomercy.companymanagement.domain.port.MembershipRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.RoleRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * La invitación con la que una persona activa su acceso. RH registra a la persona (M3); aquí se le manda el enlace, se
 * reenvía, se cancela, y la persona lo abre y elige su contraseña. Activar crea su cuenta (o usa la que ya tiene), su
 * membresía y sus sucursales, todo en una sola transacción: o queda todo, o no queda nada.
 *
 * <p>El enlace lleva un código que solo viaja en el correo. Uno vencido, usado, cancelado o inexistente responde lo
 * mismo, para no revelar qué códigos existen. Los eventos de invitación no llevan datos personales.
 */
public class EmployeeInvitationService {
    static final Duration VALIDITY = Duration.ofDays(7);

    private final EmployeeInvitationRepositoryPort invitations;
    private final EmployeeProfileRepositoryPort employees;
    private final CompanyRepositoryPort companies;
    private final RoleRepositoryPort roles;
    private final BranchRepositoryPort branches;
    private final AccountRepositoryPort accounts;
    private final AccountApplicationService accountService;
    private final MembershipRepositoryPort memberships;
    private final EventOutboxPort outbox;
    private final InvitationDeliveryPort delivery;
    private final IdempotencyRegistry idempotency;
    private final Duration resendInterval;

    public EmployeeInvitationService(EmployeeInvitationRepositoryPort invitations, EmployeeProfileRepositoryPort employees,
                                     CompanyRepositoryPort companies, RoleRepositoryPort roles, BranchRepositoryPort branches,
                                     AccountRepositoryPort accounts, AccountApplicationService accountService,
                                     MembershipRepositoryPort memberships, EventOutboxPort outbox,
                                     InvitationDeliveryPort delivery, IdempotencyRegistry idempotency, Duration resendInterval) {
        this.invitations = Objects.requireNonNull(invitations);
        this.employees = Objects.requireNonNull(employees);
        this.companies = Objects.requireNonNull(companies);
        this.roles = Objects.requireNonNull(roles);
        this.branches = Objects.requireNonNull(branches);
        this.accounts = Objects.requireNonNull(accounts);
        this.accountService = Objects.requireNonNull(accountService);
        this.memberships = Objects.requireNonNull(memberships);
        this.outbox = Objects.requireNonNull(outbox);
        this.delivery = Objects.requireNonNull(delivery);
        this.idempotency = Objects.requireNonNull(idempotency);
        this.resendInterval = Objects.requireNonNull(resendInterval);
    }

    /** La invitación más reciente de la persona, como la ve RH; {@code null} si nunca tuvo una. */
    public InvitationSummaryDto summaryOf(EmployeeProfile profile) {
        List<EmployeeInvitation> all = invitations.findByPublicEmployeeId(profile.getPublicEmployeeId());
        if (all.isEmpty()) {
            return null;
        }
        EmployeeInvitation latest = all.stream().max(Comparator.comparing(EmployeeInvitation::getCreatedAt)).orElseThrow();
        boolean expired = latest.getStatus() == InvitationStatus.PENDING && !latest.isUsable(Instant.now());
        return new InvitationSummaryDto(expired ? "EXPIRED" : latest.getStatus().name(), latest.getEmailStatus().name(), latest.getExpiresAt());
    }

    /**
     * Crea la invitación de una persona y le manda el enlace cuando la transacción en curso se confirme. Si tenía otra
     * vigente, esa deja de servir.
     */
    @Transactional
    public void issue(EmployeeProfile profile, Company company, String actorUserId, String correlationId) {
        List<String> roleNames = roleNamesOf(profile.getRoleIds());
        supersedePending(profile, company, roleNames, actorUserId, correlationId);
        String code = OneTimeCode.generate();
        EmployeeInvitation invitation = invitations.save(EmployeeInvitation.issue(profile.getPublicEmployeeId(),
            company.getPublicCompanyId(), OneTimeCode.hash(code), Instant.now().plus(VALIDITY)));
        recordInvitationEvent("membership.invitation.created", invitation, company, roleNames, "PENDING", actorUserId, correlationId);
        EmployeeProfile.PersonalData data = profile.getData();
        delivery.deliverAfterCommit(new InvitationDeliveryPort.Delivery(invitation.getPublicInvitationId(), data.email(),
            data.firstName(), company.getTradeName(), roleNames, code, invitation.getExpiresAt()));
    }

    /** Manda un enlace nuevo; el anterior deja de servir. Hay un tiempo mínimo entre envíos. */
    @Transactional
    public void resend(String companyId, String employeeId, String actorUserId, String correlationId, String idempotencyKey) {
        idempotency.execute("employee.invitation.resend:" + employeeId, idempotencyKey, () -> {
            Company company = requireCompany(companyId);
            EmployeeProfile profile = pendingProfile(companyId, employeeId);
            Instant now = Instant.now();
            invitations.findByPublicEmployeeId(employeeId).stream().map(EmployeeInvitation::getCreatedAt).max(Instant::compareTo)
                .filter(latest -> now.isBefore(latest.plus(resendInterval)))
                .ifPresent(latest -> {
                    long minutes = Math.max(1, Duration.between(now, latest.plus(resendInterval)).toMinutes() + 1);
                    throw new ConflictException("Ya se le mandó la invitación hace poco. Espera " + minutes + " minuto(s) antes de reenviarla.");
                });
            issue(profile, company, actorUserId, correlationId);
            return Boolean.TRUE;
        });
    }

    /** Cancela la invitación vigente. La persona sigue pendiente y se le puede volver a invitar. */
    @Transactional
    public void cancel(String companyId, String employeeId, String actorUserId, String correlationId, String idempotencyKey) {
        idempotency.execute("employee.invitation.cancel:" + employeeId, idempotencyKey, () -> {
            Company company = requireCompany(companyId);
            EmployeeProfile profile = pendingProfile(companyId, employeeId);
            Instant now = Instant.now();
            EmployeeInvitation current = invitations.findByPublicEmployeeId(employeeId).stream()
                .filter(invitation -> invitation.isUsable(now)).findFirst()
                .orElseThrow(() -> new ConflictException("Esta persona no tiene una invitación vigente."));
            current.cancel();
            invitations.save(current);
            recordInvitationEvent("membership.invitation.revoked", current, company, roleNamesOf(profile.getRoleIds()), "REVOKED", actorUserId, correlationId);
            return Boolean.TRUE;
        });
    }

    /** Lo que ve quien abre el enlace. Un código que ya no sirve, sea por lo que sea, responde igual. */
    public InvitationPreviewDto consult(String code) {
        EmployeeInvitation invitation = findUsable(code);
        EmployeeProfile profile = pendingProfileOf(invitation);
        Company company = companies.findByPublicCompanyId(invitation.getPublicCompanyId()).orElseThrow(EmployeeInvitationService::noLongerValid);
        EmployeeProfile.PersonalData data = profile.getData();
        return new InvitationPreviewDto(data.firstName(), company.getTradeName(), roleNamesOf(profile.getRoleIds()), data.email(),
            accounts.findByOwnerEmail(data.email()).isPresent());
    }

    /**
     * Activa el acceso. Si el correo no tiene cuenta, se crea con la contraseña que la persona eligió; si ya la tiene,
     * debe haber iniciado sesión con ella ({@code callerAccountId}) y no se toca su contraseña.
     */
    @Transactional
    public void accept(String code, String password, String callerAccountId, String correlationId) {
        EmployeeInvitation invitation = findUsable(code);
        EmployeeProfile profile = pendingProfileOf(invitation);
        Company company = companies.findByPublicCompanyId(invitation.getPublicCompanyId()).orElseThrow(EmployeeInvitationService::noLongerValid);
        String companyId = company.getPublicCompanyId();
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw new DomainRuleViolationException("La empresa todavía no está activa. Cuando lo esté podrás activar tu acceso con este mismo enlace.");
        }
        Set<String> roleNames = new LinkedHashSet<>(activeRoleNames(profile.getRoleIds(), companyId));
        for (String branchId : profile.getBranchIds()) {
            if (branches.findByPublicCompanyIdAndPublicBranchId(companyId, branchId).isEmpty()) {
                throw new DomainRuleViolationException("Una de tus sucursales ya no existe. Pide a quien te invitó que revise tu alta.");
            }
        }

        EmployeeProfile.PersonalData data = profile.getData();
        String accountId = accountFor(data, password, callerAccountId, invitation, correlationId);
        if (memberships.findMembershipByCompanyAndUser(companyId, accountId).isPresent()) {
            throw new ConflictException("Esta persona ya pertenece a la empresa.");
        }
        CompanyMembership membership = memberships.saveMembership(CompanyMembership.create(companyId, accountId, roleNames));
        for (String branchId : profile.getBranchIds()) {
            memberships.saveAssignment(BranchAssignment.create(membership.getPublicMembershipId(), branchId));
        }
        profile.activate(accountId, membership.getPublicMembershipId());
        employees.save(profile);
        invitation.accept(Instant.now());
        invitations.save(invitation);

        List<String> sortedRoles = roleNames.stream().sorted().toList();
        Map<String, Object> membershipData = Map.of("membershipId", membership.getPublicMembershipId(), "companyId", companyId,
            "userId", accountId, "roles", sortedRoles, "status", "ACTIVE");
        outbox.recordEvent("membership.created", "1.0", membership.getVersion(), "company_management", correlation(correlationId),
            null, company.getPublicAccountId(), companyId, null, accountId, membershipData);
        outbox.recordEvent("membership.invitation.accepted", "1.0", membership.getVersion(), "company_management", correlation(correlationId),
            null, company.getPublicAccountId(), companyId, null, accountId, membershipData);
    }

    /** La cuenta de la persona: la que ya tiene (con su sesión) o una nueva con la contraseña que eligió. */
    private String accountFor(EmployeeProfile.PersonalData data, String password, String callerAccountId,
                              EmployeeInvitation invitation, String correlationId) {
        Account existing = accounts.findByOwnerEmail(data.email()).orElse(null);
        if (existing != null) {
            if (!existing.getOwner().getRfc().equals(data.rfc())) {
                throw new DuplicateResourceException("No pudimos activar tu acceso con estos datos. Pide ayuda a quien te invitó.");
            }
            if (callerAccountId == null) {
                throw new AuthenticationRequiredException("Inicia sesión con tu cuenta para activar tu acceso a la empresa.");
            }
            if (!callerAccountId.equals(existing.getPublicAccountId())) {
                throw new UnauthorizedAccessException("Esta invitación es para otra cuenta. Inicia sesión con la cuenta de tu correo.");
            }
            return existing.getPublicAccountId();
        }
        // La clave de idempotencia es nueva en cada intento: si algo falla después y se deshace la transacción, un
        // reintento no debe recibir la cuenta que ya no existe.
        return accountService.registerAccount(data.firstName() + " " + data.lastName(), data.firstName(), data.lastName(),
            data.birthDate(), data.rfc(), data.phoneNumber(), data.email(), data.state(), data.address(), password,
            correlationId, "invitation:" + invitation.getPublicInvitationId() + ":" + UUID.randomUUID()).publicAccountId();
    }

    private void supersedePending(EmployeeProfile profile, Company company, List<String> roleNames, String actorUserId, String correlationId) {
        Instant now = Instant.now();
        for (EmployeeInvitation previous : invitations.findByPublicEmployeeId(profile.getPublicEmployeeId())) {
            if (previous.getStatus() != InvitationStatus.PENDING) {
                continue;
            }
            boolean wasUsable = previous.isUsable(now);
            previous.cancel();
            invitations.save(previous);
            if (wasUsable) {
                recordInvitationEvent("membership.invitation.revoked", previous, company, roleNames, "REVOKED", actorUserId, correlationId);
            }
        }
    }

    private void recordInvitationEvent(String type, EmployeeInvitation invitation, Company company, List<String> roleNames,
                                       String status, String actorUserId, String correlationId) {
        // El contrato describe estos eventos con los datos de la membresía, que todavía no existe: solo se publican la
        // empresa, los roles y el estado. Ni el correo ni los datos personales salen de aquí.
        outbox.recordEvent(type, "1.0", invitation.getVersion(), "company_management", correlation(correlationId), null,
            company.getPublicAccountId(), company.getPublicCompanyId(), null, actorUserId,
            Map.of("companyId", company.getPublicCompanyId(), "roles", roleNames, "status", status));
    }

    private EmployeeInvitation findUsable(String code) {
        return invitations.findByTokenHash(OneTimeCode.hash(code))
            .filter(invitation -> invitation.isUsable(Instant.now()))
            .orElseThrow(EmployeeInvitationService::noLongerValid);
    }

    private EmployeeProfile pendingProfileOf(EmployeeInvitation invitation) {
        return employees.findByPublicEmployeeId(invitation.getPublicEmployeeId())
            .filter(profile -> profile.getStatus() == EmployeeStatus.PENDING_ACTIVATION)
            .orElseThrow(EmployeeInvitationService::noLongerValid);
    }

    private EmployeeProfile pendingProfile(String companyId, String employeeId) {
        EmployeeProfile profile = employees.findByPublicEmployeeId(employeeId)
            .filter(found -> found.getPublicCompanyId().equals(companyId))
            .orElseThrow(() -> new EntityNotFoundException("Persona", employeeId));
        if (profile.getStatus() != EmployeeStatus.PENDING_ACTIVATION) {
            throw new DomainRuleViolationException("Esta persona ya activó su acceso.");
        }
        return profile;
    }

    private Company requireCompany(String companyId) {
        return companies.findByPublicCompanyId(companyId).orElseThrow(() -> new EntityNotFoundException("Empresa", companyId));
    }

    private List<String> roleNamesOf(List<String> roleIds) {
        List<String> names = new ArrayList<>();
        for (String roleId : roleIds) {
            roles.findByPublicRoleId(roleId).map(Role::getName).ifPresent(names::add);
        }
        return names.stream().sorted().toList();
    }

    /** Los nombres de los roles, que deben seguir existiendo y activos: si no, RH tiene que revisar el alta. */
    private List<String> activeRoleNames(List<String> roleIds, String companyId) {
        List<String> names = new ArrayList<>();
        for (String roleId : roleIds) {
            Role role = roles.findByPublicRoleId(roleId)
                .filter(found -> found.isSystemRole() || companyId.equals(found.getPublicCompanyId()))
                .filter(found -> found.getStatus() == RoleStatus.ACTIVE)
                .orElseThrow(() -> new DomainRuleViolationException("Uno de tus roles ya no está disponible. Pide a quien te invitó que revise tu alta."));
            names.add(role.getName());
        }
        return names;
    }

    private static String correlation(String correlationId) {
        return correlationId != null ? correlationId : "cor_" + UUID.randomUUID().toString().replace("-", "");
    }

    /** Siempre el mismo error, sin citar el código ni decir si está vencido, usado, cancelado o no existe. */
    private static EntityNotFoundException noLongerValid() {
        return new EntityNotFoundException("Invitación", "solicitada");
    }
}
