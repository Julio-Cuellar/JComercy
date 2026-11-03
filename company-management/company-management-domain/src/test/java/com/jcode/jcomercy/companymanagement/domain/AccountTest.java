package com.jcode.jcomercy.companymanagement.domain;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;
import com.jcode.jcomercy.companymanagement.domain.model.account.Account;
import com.jcode.jcomercy.companymanagement.domain.model.account.AccountStatus;
import com.jcode.jcomercy.companymanagement.domain.model.account.OwnerPerson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

class AccountTest {

    @Test
    @DisplayName("Debe crear una cuenta activa con identificador público estandarizado")
    void shouldCreateActiveAccount() {
        OwnerPerson owner = owner();
        Account account = Account.create("Cuenta Principal", owner);

        assertThat(account.getPublicAccountId()).startsWith("acc_");
        assertThat(account.getName()).isEqualTo("Cuenta Principal");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getOwner().getEmail()).isEqualTo("owner@example.com");
    }

    @Test
    @DisplayName("Debe fallar si el nombre de la cuenta es nulo o vacío")
    void shouldFailWhenAccountNameIsEmpty() {
        assertThatThrownBy(() -> Account.create("   ", owner()))
            .isInstanceOf(DomainRuleViolationException.class)
            .hasMessageContaining("no puede estar vacío");
    }

    @Test
    @DisplayName("Debe permitir suspender y reactivar una cuenta")
    void shouldSuspendAndReactivateAccount() {
        Account account = Account.create("Cuenta Test", owner());

        account.suspend("Mora de pago");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);

        account.reactivate();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    private static OwnerPerson owner() {
        return OwnerPerson.create("Julio", "Pérez López", LocalDate.of(1985, 4, 12),
            "PELJ850412ABC", "5512345678", "owner@example.com", "Ciudad de México", "Av. Reforma 100", "test-password-hash");
    }
}
