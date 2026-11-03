package com.jcode.jcomercy.companymanagement.infra.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtContextTest {
    private final JwtAuthorizationService authorization = new JwtAuthorizationService();

    @Test
    void allowsCompanyInTokenContext() {
        assertThat(authorization.canAccessCompany(authentication(List.of("company-1")), "company-1")).isTrue();
    }

    @Test
    void rejectsCompanyOutsideTokenContext() {
        assertThat(authorization.canAccessCompany(authentication(List.of("company-1")), "company-2")).isFalse();
    }

    @Test
    void onlyAllowsAuthenticatedAccountOwnerContext() {
        var authentication = authentication(List.of("company-1"));
        assertThat(authorization.canAccessAccount(authentication, "account-1")).isTrue();
        assertThat(authorization.canAccessAccount(authentication, "account-2")).isFalse();
    }

    private static UsernamePasswordAuthenticationToken authentication(List<String> companyIds) {
        var authentication = new UsernamePasswordAuthenticationToken("account-1", null, List.of());
        authentication.setDetails(new JwtContext("account-1", companyIds, List.of()));
        return authentication;
    }
}
