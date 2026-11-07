package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.application.dto.EmployeeDto;
import com.jcode.jcomercy.companymanagement.application.dto.EmployeeInput;
import com.jcode.jcomercy.companymanagement.application.dto.PageDto;
import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;
import com.jcode.jcomercy.companymanagement.domain.exception.DuplicateResourceException;
import com.jcode.jcomercy.companymanagement.domain.exception.EntityNotFoundException;
import com.jcode.jcomercy.companymanagement.domain.exception.InvalidRequestException;
import com.jcode.jcomercy.companymanagement.domain.model.account.Account;
import com.jcode.jcomercy.companymanagement.domain.model.company.Company;
import com.jcode.jcomercy.companymanagement.domain.model.employee.EmployeeProfile;
import com.jcode.jcomercy.companymanagement.domain.model.employee.EmployeeStatus;
import com.jcode.jcomercy.companymanagement.domain.model.employee.ProfileType;
import com.jcode.jcomercy.companymanagement.domain.model.role.Role;
import com.jcode.jcomercy.companymanagement.domain.model.role.RoleStatus;
import com.jcode.jcomercy.companymanagement.domain.port.AccountRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.BranchRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EmployeeProfileRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.RoleRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * El alta de las personas de la empresa por RH. RH registra los datos, el rol y las sucursales; la persona solo activa su
 * acceso (M5). RFC, CURP y correo son datos personales: no se escriben en registros ni viajan en eventos.
 */
public class EmployeeApplicationService {
    private static final Pattern RFC = Pattern.compile("^[A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3}$");
    private static final Pattern CURP = Pattern.compile("^[A-Z]{4}\\d{6}[HM][A-Z]{2}[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9]\\d$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String OWNER_ROLE = "rol_sys_account_owner";
    private static final Comparator<EmployeeProfile> ORDER = Comparator
        .comparing(EmployeeProfile::getCreatedAt).thenComparing(EmployeeProfile::getPublicEmployeeId);

    private final EmployeeProfileRepositoryPort employees;
    private final CompanyRepositoryPort companies;
    private final RoleRepositoryPort roles;
    private final BranchRepositoryPort branches;
    private final AccountRepositoryPort accounts;
    private final IdempotencyRegistry idempotency;
    private final EmployeeInvitationService invitations;

    public EmployeeApplicationService(EmployeeProfileRepositoryPort employees, CompanyRepositoryPort companies,
                                      RoleRepositoryPort roles, BranchRepositoryPort branches,
                                      AccountRepositoryPort accounts, IdempotencyRegistry idempotency,
                                      EmployeeInvitationService invitations) {
        this.invitations = Objects.requireNonNull(invitations);
        this.employees = Objects.requireNonNull(employees);
        this.companies = Objects.requireNonNull(companies);
        this.roles = Objects.requireNonNull(roles);
        this.branches = Objects.requireNonNull(branches);
        this.accounts = Objects.requireNonNull(accounts);
        this.idempotency = Objects.requireNonNull(idempotency);
    }

    /**
     * Registra a la persona y le manda su invitación. {@code actorCanGive} dice si quien registra tiene un permiso: nadie
     * puede dar un rol con permisos que no tiene.
     */
    @Transactional
    public EmployeeDto register(String companyId, EmployeeInput input, Predicate<String> actorCanGive, String actorUserId,
                                String correlationId, String idempotencyKey) {
        return idempotency.execute("employee.create:" + companyId, idempotencyKey, () -> {
            Company company = requireCompany(companyId);
            EmployeeProfile.PersonalData data = validated(input);
            checkRolesAndBranches(companyId, input, actorCanGive);
            checkAgainstOtherAccounts(company, data);
            checkInsideTheCompany(companyId, data, null);
            EmployeeProfile saved = employees.save(EmployeeProfile.register(companyId, ProfileType.EMPLEADO, data,
                List.copyOf(new LinkedHashSet<>(input.roleIds())), List.copyOf(new LinkedHashSet<>(input.branchIds()))));
            invitations.issue(saved, company, actorUserId, correlationId);
            return view(saved);
        });
    }

    /** Corrige un perfil que aún no activa su acceso. Se vuelven a comprobar los datos como en el alta. */
    @Transactional
    public EmployeeDto correct(String companyId, String employeeId, EmployeeInput input, Predicate<String> actorCanGive) {
        Company company = requireCompany(companyId);
        EmployeeProfile profile = find(companyId, employeeId);
        if (profile.getStatus() != EmployeeStatus.PENDING_ACTIVATION) {
            throw new DomainRuleViolationException("Solo se pueden corregir los datos de una persona que aún no activa su acceso.");
        }
        EmployeeProfile.PersonalData data = validated(input);
        checkRolesAndBranches(companyId, input, actorCanGive);
        checkAgainstOtherAccounts(company, data);
        checkInsideTheCompany(companyId, data, profile.getPublicEmployeeId());
        profile.correct(data, List.copyOf(new LinkedHashSet<>(input.roleIds())), List.copyOf(new LinkedHashSet<>(input.branchIds())));
        return view(employees.save(profile));
    }

    public EmployeeDto get(String companyId, String employeeId) {
        requireCompany(companyId);
        return view(find(companyId, employeeId));
    }

    /** El perfil con el estado de su invitación más reciente. */
    private EmployeeDto view(EmployeeProfile profile) {
        return EmployeeDto.fromDomain(profile, invitations.summaryOf(profile));
    }

    public PageDto<EmployeeDto> list(String companyId, String status, Integer limit, String after) {
        requireCompany(companyId);
        EmployeeStatus wantedStatus = parseStatus(status);
        int pageSize = MembershipApplicationService.pageSize(limit);
        PageCursor cursor = PageCursor.decode(after);

        List<EmployeeProfile> matching = employees.findByPublicCompanyId(companyId).stream()
            .sorted(ORDER)
            .filter(profile -> cursor == null || cursor.isBefore(profile.getCreatedAt(), profile.getPublicEmployeeId()))
            .filter(profile -> wantedStatus == null || profile.getStatus() == wantedStatus)
            .toList();
        boolean hasMore = matching.size() > pageSize;
        List<EmployeeProfile> pageItems = hasMore ? matching.subList(0, pageSize) : matching;
        String next = null;
        if (hasMore) {
            EmployeeProfile last = pageItems.get(pageItems.size() - 1);
            next = new PageCursor(last.getCreatedAt(), last.getPublicEmployeeId()).encode();
        }
        return new PageDto<>(pageItems.stream().map(this::view).toList(), new PageDto.Page(pageSize, next));
    }

    private Company requireCompany(String companyId) {
        return companies.findByPublicCompanyId(companyId).orElseThrow(() -> new EntityNotFoundException("Empresa", companyId));
    }

    private EmployeeProfile find(String companyId, String employeeId) {
        return employees.findByPublicEmployeeId(employeeId)
            .filter(profile -> profile.getPublicCompanyId().equals(companyId))
            .orElseThrow(() -> new EntityNotFoundException("Persona", employeeId));
    }

    /** Revisa la forma de cada dato (400) y devuelve los datos ya normalizados. */
    private static EmployeeProfile.PersonalData validated(EmployeeInput input) {
        if (input == null) {
            throw new InvalidRequestException("Faltan los datos de la persona.");
        }
        String firstName = text(input.firstName(), "el nombre", 100);
        String lastName = text(input.lastName(), "los apellidos", 160);
        String phone = text(input.phoneNumber(), "el teléfono", 30);
        String state = text(input.state(), "el estado", 100);
        String address = text(input.address(), "el domicilio", 500);
        String email = text(input.email(), "el correo", 255).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw new InvalidRequestException("Revisa el correo: debe tener la forma nombre@empresa.com.");
        }
        String rfc = text(input.rfc(), "el RFC", 13).toUpperCase(Locale.ROOT);
        if (!RFC.matcher(rfc).matches()) {
            throw new InvalidRequestException("Revisa el RFC: son 12 o 13 caracteres, por ejemplo PEPJ850412AB1.");
        }
        String curp = text(input.curp(), "la CURP", 18).toUpperCase(Locale.ROOT);
        if (!CURP.matcher(curp).matches()) {
            throw new InvalidRequestException("Revisa la CURP: son 18 caracteres, por ejemplo PEPJ850412HDFRRN09.");
        }
        LocalDate birthDate = input.birthDate();
        if (birthDate == null || !birthDate.isBefore(LocalDate.now()) || birthDate.isBefore(LocalDate.of(1900, 1, 1))) {
            throw new InvalidRequestException("Revisa la fecha de nacimiento: debe ser anterior a hoy.");
        }
        if (input.roleIds() == null || input.roleIds().isEmpty()) {
            throw new InvalidRequestException("Elige al menos un rol para la persona.");
        }
        if (input.branchIds() == null || input.branchIds().isEmpty()) {
            throw new InvalidRequestException("Elige al menos una sucursal para la persona.");
        }
        return new EmployeeProfile.PersonalData(firstName, lastName, birthDate, rfc, curp, phone, email, state, address);
    }

    private static String text(String value, String what, int max) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("Escribe " + what + ".");
        }
        String clean = value.trim();
        if (clean.length() > max) {
            throw new InvalidRequestException("Revisa " + what + ": puede tener hasta " + max + " caracteres.");
        }
        return clean;
    }

    /** Los roles deben existir en la empresa, estar activos y poder darse; las sucursales deben ser de la empresa. */
    private void checkRolesAndBranches(String companyId, EmployeeInput input, Predicate<String> actorCanGive) {
        Map<String, Role> visible = roles.findByPublicCompanyIdOrSystem(companyId).stream()
            .collect(Collectors.toMap(Role::getPublicRoleId, Function.identity(), (a, b) -> a));
        for (String roleId : input.roleIds()) {
            Role role = visible.get(roleId);
            if (role == null || role.getStatus() != RoleStatus.ACTIVE) {
                throw new DomainRuleViolationException("El rol '" + roleId + "' no existe en tu empresa o está desactivado.");
            }
            if (OWNER_ROLE.equals(role.getPublicRoleId())) {
                throw new DomainRuleViolationException("El rol de dueño de la cuenta no se puede dar a una persona del equipo.");
            }
            for (String permission : role.getPermissions()) {
                if (!actorCanGive.test(permission)) {
                    throw new DomainRuleViolationException("No puedes dar el rol «" + role.getName()
                        + "» porque incluye permisos que tu rol no tiene.");
                }
            }
        }
        for (String branchId : input.branchIds()) {
            if (branches.findByPublicCompanyIdAndPublicBranchId(companyId, branchId).isEmpty()) {
                throw new DomainRuleViolationException("La sucursal '" + branchId + "' no existe en tu empresa.");
            }
        }
    }

    /**
     * Si el correo o el RFC ya pertenecen a una cuenta, tienen que ser de la misma persona: si el correo es de una cuenta
     * con otro RFC, o el RFC de una cuenta con otro correo, se rechaza. El mensaje no dice cuál dato chocó: quien registra
     * no debe poder averiguar quién tiene cuenta en el sistema.
     */
    private void checkAgainstOtherAccounts(Company company, EmployeeProfile.PersonalData data) {
        Account byEmail = accounts.findByOwnerEmail(data.email()).orElse(null);
        if (byEmail != null) {
            if (!byEmail.getOwner().getRfc().equals(data.rfc())) {
                throw conflictWithAnotherAccount();
            }
            if (byEmail.getPublicAccountId().equals(company.getPublicAccountId())) {
                throw new DomainRuleViolationException("Esa persona es la dueña de esta cuenta y ya puede entrar a la empresa sin alta.");
            }
        } else if (accounts.existsByOwnerRfc(data.rfc())) {
            throw conflictWithAnotherAccount();
        }
    }

    private static DuplicateResourceException conflictWithAnotherAccount() {
        return new DuplicateResourceException("No pudimos registrar a esta persona con esos datos. Revisa que estén bien escritos o pide ayuda a quien administra la cuenta.");
    }

    /** Dentro de la empresa el RFC, el correo y la CURP no se repiten (se ignora el propio perfil al corregir). */
    private void checkInsideTheCompany(String companyId, EmployeeProfile.PersonalData data, String ownEmployeeId) {
        for (EmployeeProfile other : employees.findByPublicCompanyId(companyId)) {
            if (other.getPublicEmployeeId().equals(ownEmployeeId)) {
                continue;
            }
            EmployeeProfile.PersonalData existing = other.getData();
            if (existing.rfc().equals(data.rfc())) {
                throw new DuplicateResourceException("Ya registraste a una persona con ese RFC en tu empresa.");
            }
            if (existing.email().equals(data.email())) {
                throw new DuplicateResourceException("Ya registraste a una persona con ese correo en tu empresa.");
            }
            if (existing.curp().equals(data.curp())) {
                throw new DuplicateResourceException("Ya registraste a una persona con esa CURP en tu empresa.");
            }
        }
    }

    private static EmployeeStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return EmployeeStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("El estado '" + status + "' no existe. Usa PENDING_ACTIVATION o ACTIVE.");
        }
    }
}
