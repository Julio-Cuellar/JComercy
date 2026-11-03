package com.jcode.jcomercy.companymanagement.infra.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtContextTest {
    private final JwtAuthorizationService authorization = new JwtAuthorizationService();

    @Test
    void anAccountTokenIsNotTiedToAnyCompany() {
        var authentication = authentication(null);

        assertThat(authorization.isAccountSession(authentication)).isTrue();
        assertThat(authorization.isSessionOfCompany(authentication, "company-1")).isFalse();
    }

    @Test
    void aCompanyTokenOnlyWorksForItsOwnCompany() {
        var authentication = authentication("company-1");

        assertThat(authorization.isSessionOfCompany(authentication, "company-1")).isTrue();
        assertThat(authorization.isSessionOfCompany(authentication, "company-2")).isFalse();
        assertThat(authorization.isSessionOfCompany(authentication, null)).isFalse();
    }

    @Test
    void aCompanyTokenIsNotAnAccountToken() {
        assertThat(authorization.isAccountSession(authentication("company-1"))).isFalse();
    }

    @Test
    void withoutASessionNothingIsAnAccountOrACompanyToken() {
        assertThat(authorization.isAccountSession(null)).isFalse();
        assertThat(authorization.isSessionOfCompany(null, "company-1")).isFalse();
        assertThat(authorization.isAccountSession(new UsernamePasswordAuthenticationToken("account-1", null, List.of()))).isFalse();
    }

    @Test
    void onlyAllowsAuthenticatedAccountOwnerContext() {
        var authentication = authentication(null);
        assertThat(authorization.canAccessAccount(authentication, "account-1")).isTrue();
        assertThat(authorization.canAccessAccount(authentication, "account-2")).isFalse();
    }

    private static UsernamePasswordAuthenticationToken authentication(String companyContext) {
        var authentication = new UsernamePasswordAuthenticationToken("account-1", null, List.of());
        authentication.setDetails(new JwtContext("account-1", companyContext));
        return authentication;
    }
}
