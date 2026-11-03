package com.jcode.jcomercy.companymanagement.infra.persistence.entity;

import com.jcode.jcomercy.companymanagement.domain.model.account.OwnerPerson;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "owner_person", schema = "company_management")
public class OwnerPersonJpaEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 100) private String firstName;
    @Column(nullable = false, length = 160) private String lastName;
    @Column(nullable = false) private LocalDate birthDate;
    @Column(nullable = false, length = 13) private String rfc;
    @Column(nullable = false, length = 30) private String phoneNumber;
    @Column(nullable = false, length = 255) private String email;
    @Column(name = "password_hash", length = 255) private String passwordHash;
    @Column(nullable = false, length = 100) private String state;
    @Column(nullable = false, length = 500) private String address;

    public OwnerPersonJpaEntity() {}

    public static OwnerPersonJpaEntity fromDomain(OwnerPerson domain) {
        OwnerPersonJpaEntity entity = new OwnerPersonJpaEntity();
        entity.id = domain.getId(); entity.firstName = domain.getFirstName(); entity.lastName = domain.getLastName();
        entity.birthDate = domain.getBirthDate(); entity.rfc = domain.getRfc(); entity.phoneNumber = domain.getPhoneNumber();
        entity.email = domain.getEmail(); entity.passwordHash = domain.getPasswordHash(); entity.state = domain.getState(); entity.address = domain.getAddress();
        return entity;
    }

    public OwnerPerson toDomain() {
        return new OwnerPerson(id, firstName, lastName, birthDate, rfc, phoneNumber, email, state, address, passwordHash);
    }
}
