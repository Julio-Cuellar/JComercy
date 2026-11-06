package com.jcode.jcomercy.companymanagement.application.dto;

import com.jcode.jcomercy.companymanagement.domain.model.employee.EmployeeProfile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * El perfil de una persona como lo ve RH. Lleva RFC y CURP, que son datos personales: solo se entrega a quien tiene
 * permiso de RH y no se copia a eventos ni a registros.
 */
public record EmployeeDto(
    String employeeId,
    String profileType,
    String status,
    String firstName,
    String lastName,
    LocalDate birthDate,
    String rfc,
    String curp,
    String phoneNumber,
    String email,
    String state,
    String address,
    List<String> roleIds,
    List<String> branchIds,
    Instant createdAt,
    Instant updatedAt,
    long version
) {
    public static EmployeeDto fromDomain(EmployeeProfile profile) {
        EmployeeProfile.PersonalData data = profile.getData();
        return new EmployeeDto(profile.getPublicEmployeeId(), profile.getProfileType().name(), profile.getStatus().name(),
            data.firstName(), data.lastName(), data.birthDate(), data.rfc(), data.curp(), data.phoneNumber(), data.email(),
            data.state(), data.address(), profile.getRoleIds(), profile.getBranchIds(), profile.getCreatedAt(),
            profile.getUpdatedAt(), profile.getVersion());
    }
}
