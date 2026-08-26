package com.jcode.jcomercy.companymanagement.domain;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;
import com.jcode.jcomercy.companymanagement.domain.model.account.Account;
import com.jcode.jcomercy.companymanagement.domain.model.account.AccountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    @DisplayName("Debe crear una cuenta activa con identificador público estandarizado")
    void shouldCreateActiveAccount() {
        Account account = Account.create("Cuenta Principal", "usr_123");

        assertThat(account.getPublicAccountId()).startsWith("acc_");
        assertThat(account.getName()).isEqualTo("Cuenta Principal");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getOwnerUserId()).isEqualTo("usr_123");
    }

    @Test
    @DisplayName("Debe fallar si el nombre de la cuenta es nulo o vacío")
    void shouldFailWhenAccountNameIsEmpty() {
        assertThatThrownBy(() -> Account.create("   ", "usr_123"))
            .isInstanceOf(DomainRuleViolationException.class)
            .hasMessageContaining("no puede estar vacío");
    }

    @Test
    @DisplayName("Debe permitir suspender y reactivar una cuenta")
    void shouldSuspendAndReactivateAccount() {
        Account account = Account.create("Cuenta Test", "usr_123");

        account.suspend("Mora de pago");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);

        account.reactivate();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }
}
