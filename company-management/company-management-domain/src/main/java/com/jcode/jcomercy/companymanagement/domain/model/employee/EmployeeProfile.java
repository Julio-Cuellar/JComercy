package com.jcode.jcomercy.companymanagement.domain.model.employee;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que RH registra de una persona de la empresa: sus datos, el rol y las sucursales que tendrá. El perfil existe antes
 * que su acceso: queda pendiente hasta que la persona active el suyo. Pertenece a una empresa, así que el RFC, el correo
 * y la CURP solo se comparan dentro de ella. Son datos personales: no salen en eventos ni en registros.
 */
public class EmployeeProfile {

    /** Los datos personales del perfil, ya normalizados (RFC y CURP en mayúsculas, correo en minúsculas). */
    public record PersonalData(String firstName, String lastName, LocalDate birthDate, String rfc, String curp,
                               String phoneNumber, String email, String state, String address) {
        public PersonalData {
            firstName = Objects.requireNonNull(firstName, "El nombre no puede ser nulo").trim();
            lastName = Objects.requireNonNull(lastName, "Los apellidos no pueden ser nulos").trim();
            birthDate = Objects.requireNonNull(birthDate, "La fecha de nacimiento no puede ser nula");
            rfc = Objects.requireNonNull(rfc, "El RFC no puede ser nulo").trim().toUpperCase(Locale.ROOT);
            curp = Objects.requireNonNull(curp, "La CURP no puede ser nula").trim().toUpperCase(Locale.ROOT);
            phoneNumber = Objects.requireNonNull(phoneNumber, "El teléfono no puede ser nulo").trim();
            email = Objects.requireNonNull(email, "El correo no puede ser nulo").trim().toLowerCase(Locale.ROOT);
            state = Objects.requireNonNull(state, "El estado no puede ser nulo").trim();
            address = Objects.requireNonNull(address, "El domicilio no puede ser nulo").trim();
        }
    }

    private final UUID id;
    private final String publicEmployeeId;
    private final String publicCompanyId;
    private final ProfileType profileType;
    private EmployeeStatus status;
    private PersonalData data;
    private List<String> roleIds;
    private List<String> branchIds;
    private String accountPublicId;
    private String membershipPublicId;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;

    public EmployeeProfile(UUID id, String publicEmployeeId, String publicCompanyId, ProfileType profileType,
                           EmployeeStatus status, PersonalData data, List<String> roleIds, List<String> branchIds,
                           String accountPublicId, String membershipPublicId, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id no puede ser nulo");
        this.publicEmployeeId = Objects.requireNonNull(publicEmployeeId, "publicEmployeeId no puede ser nulo");
        this.publicCompanyId = Objects.requireNonNull(publicCompanyId, "publicCompanyId no puede ser nulo");
        this.profileType = Objects.requireNonNull(profileType, "profileType no puede ser nulo");
        this.status = Objects.requireNonNull(status, "status no puede ser nulo");
        this.data = Objects.requireNonNull(data, "data no puede ser nulo");
        this.roleIds = List.copyOf(Objects.requireNonNull(roleIds, "roleIds no puede ser nulo"));
        this.branchIds = List.copyOf(Objects.requireNonNull(branchIds, "branchIds no puede ser nulo"));
        this.accountPublicId = accountPublicId;
        this.membershipPublicId = membershipPublicId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt no puede ser nulo");
        this.version = version;
    }

    /** Un perfil nuevo, pendiente de activar. */
    public static EmployeeProfile register(String publicCompanyId, ProfileType profileType, PersonalData data,
                                           List<String> roleIds, List<String> branchIds) {
        Instant now = Instant.now();
        String publicId = "emp_" + UUID.randomUUID().toString().replace("-", "");
        return new EmployeeProfile(UUID.randomUUID(), publicId, publicCompanyId, profileType,
            EmployeeStatus.PENDING_ACTIVATION, data, roleIds, branchIds, null, null, now, now, 0L);
    }

    /** Corrige los datos, el rol y las sucursales mientras la persona no ha activado su acceso. */
    public void correct(PersonalData newData, List<String> newRoleIds, List<String> newBranchIds) {
        if (status != EmployeeStatus.PENDING_ACTIVATION) {
            throw new DomainRuleViolationException("Solo se pueden corregir los datos de una persona que aún no activa su acceso.");
        }
        this.data = Objects.requireNonNull(newData);
        this.roleIds = List.copyOf(newRoleIds);
        this.branchIds = List.copyOf(newBranchIds);
        this.updatedAt = Instant.now();
    }

    /** La persona activó su acceso: queda ligada a su cuenta y a su membresía en la empresa. */
    public void activate(String accountPublicId, String membershipPublicId) {
        if (status != EmployeeStatus.PENDING_ACTIVATION) {
            throw new DomainRuleViolationException("Esta persona ya activó su acceso.");
        }
        this.accountPublicId = Objects.requireNonNull(accountPublicId);
        this.membershipPublicId = Objects.requireNonNull(membershipPublicId);
        this.status = EmployeeStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getPublicEmployeeId() { return publicEmployeeId; }
    public String getPublicCompanyId() { return publicCompanyId; }
    public ProfileType getProfileType() { return profileType; }
    public EmployeeStatus getStatus() { return status; }
    public PersonalData getData() { return data; }
    public List<String> getRoleIds() { return roleIds; }
    public List<String> getBranchIds() { return branchIds; }
    public String getAccountPublicId() { return accountPublicId; }
    public String getMembershipPublicId() { return membershipPublicId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
