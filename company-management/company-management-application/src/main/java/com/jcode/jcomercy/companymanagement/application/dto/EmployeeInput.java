package com.jcode.jcomercy.companymanagement.application.dto;

import java.time.LocalDate;
import java.util.List;

/** Lo que RH escribe para registrar o corregir a una persona: sus datos, el rol y las sucursales que tendrá. */
public record EmployeeInput(
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
    List<String> branchIds
) { }
