package com.jcode.jcomercy.companymanagement.domain.model.account;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class OwnerPerson {
    private final UUID id;
    private final String firstName;
    private final String lastName;
    private final LocalDate birthDate;
    private final String rfc;
    private final String phoneNumber;
    private final String email;
    private final String passwordHash;
    private final String state;
    private final String address;

    public OwnerPerson(UUID id, String firstName, String lastName, LocalDate birthDate, String rfc,
                       String phoneNumber, String email, String state, String address, String passwordHash) {
        this.id = Objects.requireNonNull(id, "id de persona no puede ser nulo");
        this.firstName = required(firstName, "El nombre");
        this.lastName = required(lastName, "Los apellidos");
        this.birthDate = Objects.requireNonNull(birthDate, "La fecha de nacimiento no puede ser nula");
        if (!birthDate.isBefore(LocalDate.now())) {
            throw new DomainRuleViolationException("La fecha de nacimiento debe ser anterior a hoy.");
        }
        this.rfc = required(rfc, "El RFC").toUpperCase();
        this.phoneNumber = required(phoneNumber, "El número de contacto");
        this.email = required(email, "El correo electrónico").toLowerCase();
        this.passwordHash = required(passwordHash, "La contraseña cifrada");
        this.state = required(state, "El estado");
        this.address = required(address, "La dirección");
    }

    public static OwnerPerson create(String firstName, String lastName, LocalDate birthDate, String rfc,
                                     String phoneNumber, String email, String state, String address, String passwordHash) {
        return new OwnerPerson(UUID.randomUUID(), firstName, lastName, birthDate, rfc, phoneNumber, email, state, address, passwordHash);
    }

    private static String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new DomainRuleViolationException(label + " no puede estar vacío.");
        }
        return value.trim();
    }

    public UUID getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public LocalDate getBirthDate() { return birthDate; }
    public String getRfc() { return rfc; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getState() { return state; }
    public String getAddress() { return address; }

    public OwnerPerson withPasswordHash(String newPasswordHash) {
        return new OwnerPerson(id, firstName, lastName, birthDate, rfc, phoneNumber, email,
            state, address, newPasswordHash);
    }
}
