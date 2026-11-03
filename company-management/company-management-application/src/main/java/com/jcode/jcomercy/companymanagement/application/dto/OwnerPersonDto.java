package com.jcode.jcomercy.companymanagement.application.dto;

import com.jcode.jcomercy.companymanagement.domain.model.account.OwnerPerson;

import java.time.LocalDate;
import java.util.UUID;

public record OwnerPersonDto(
    UUID id,
    String firstName,
    String lastName,
    LocalDate birthDate,
    String rfc,
    String phoneNumber,
    String email,
    String state,
    String address
) {
    public static OwnerPersonDto fromDomain(OwnerPerson person) {
        return new OwnerPersonDto(person.getId(), person.getFirstName(), person.getLastName(), person.getBirthDate(),
            person.getRfc(), person.getPhoneNumber(), person.getEmail(), person.getState(), person.getAddress());
    }
}
