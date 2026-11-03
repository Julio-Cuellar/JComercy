package com.jcode.jcomercy.companymanagement.domain;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;
import com.jcode.jcomercy.companymanagement.domain.model.company.Company;
import com.jcode.jcomercy.companymanagement.domain.model.company.CompanyStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

class CompanyTest {

    @Test
    @DisplayName("Debe crear una empresa en estado CONFIGURING")
    void shouldCreateCompanyInConfiguringState() {
        Company company = company("JComercy S.A. de C.V.", "JComercy", "RFC123456789");

        assertThat(company.getPublicCompanyId()).startsWith("com_");
        assertThat(company.getPublicAccountId()).isEqualTo("acc_123");
        assertThat(company.getLegalName()).isEqualTo("JComercy S.A. de C.V.");
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.CONFIGURING);
    }

    @Test
    @DisplayName("Debe completar configuración y activar empresa exitosamente")
    void shouldTransitionCompanyLifecycle() {
        Company company = company("Mi Empresa S.A.", "MiEmpresa", "RFC999999999");

        company.completeConfiguration();
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.READY_FOR_ACTIVATION);

        company.activate();
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
    }

    @Test
    @DisplayName("Debe denegar la activación si la empresa fue eliminada")
    void shouldDenyActivationForDeletedCompany() {
        Company company = company("Empresa Eliminada", "Trade", "RFC000000000");
        company.delete();

        assertThatThrownBy(company::activate)
            .isInstanceOf(DomainRuleViolationException.class)
            .hasMessageContaining("eliminada");
    }

    private static Company company(String legalName, String tradeName, String rfc) {
        return Company.create("acc_123", legalName, tradeName, rfc, "601",
            LocalDate.of(2020, 1, 15), "5512345678", "empresa@example.com", "https://example.com", "https://example.com/logo.png",
            new com.jcode.jcomercy.companymanagement.domain.model.company.DomicilioFiscal(
                "Reforma", "100", "10", "Juárez", "06600", "Cuauhtémoc", "Ciudad de México", "México"));
    }
}
