package com.jcode.jcomercy.companymanagement.domain;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;
import com.jcode.jcomercy.companymanagement.domain.model.account.OwnerPerson;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnerPersonTest {
    @Test
    void shouldCreateOwnerPersonAndNormalizeRfcAndEmail() {
        OwnerPerson person = OwnerPerson.create(" Julio ", "Pérez López", LocalDate.of(1985, 4, 12),
            "pelj850412abc", "5512345678", "OWNER@EXAMPLE.COM", "Ciudad de México", "Av. Reforma 100", "test-password-hash");

        assertThat(person.getFirstName()).isEqualTo("Julio");
        assertThat(person.getRfc()).isEqualTo("PELJ850412ABC");
        assertThat(person.getEmail()).isEqualTo("owner@example.com");
    }

    @Test
    void shouldRejectFutureBirthDate() {
        assertThatThrownBy(() -> OwnerPerson.create("Julio", "Pérez López", LocalDate.now().plusDays(1),
            "PELJ850412ABC", "5512345678", "owner@example.com", "Ciudad de México", "Av. Reforma 100", "test-password-hash"))
            .isInstanceOf(DomainRuleViolationException.class)
            .hasMessageContaining("fecha de nacimiento");
    }
}
