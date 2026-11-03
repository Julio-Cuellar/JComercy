package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.domain.model.account.Account;
import com.jcode.jcomercy.companymanagement.domain.model.company.Company;
import com.jcode.jcomercy.companymanagement.domain.port.AccountRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyApplicationServiceTest {

    @Test
    void rejectsCompanyForAnUnknownAccount() {
        CompanyApplicationService service = new CompanyApplicationService(
            new EmptyCompanyRepository(), new EmptyAccountRepository(), noOpOutbox(), new IdempotencyRegistry());

        assertThatThrownBy(() -> service.createCompany("acc_missing", "Legal", "Trade", "RFC123456789", "601",
            LocalDate.of(2020, 1, 15), "5512345678", "empresa@example.com", "https://example.com", "https://example.com/logo.png",
            "Reforma", "100", "10", "Juárez", "06600", "Cuauhtémoc", "Ciudad de México", "México",
            "usr_1", "cor_1", "idem_1"))
            .hasMessageContaining("Account");
    }

    private static EventOutboxPort noOpOutbox() {
        return (eventType, schemaVersion, aggregateVersion, sequence, producerModule, correlationId,
                causationId, accountId, companyId, branchId, actorUserId, dataPayload) -> { };
    }

    private static final class EmptyAccountRepository implements AccountRepositoryPort {
        @Override public Account save(Account account) { return account; }
        @Override public Optional<Account> findById(UUID id) { return Optional.empty(); }
        @Override public Optional<Account> findByPublicAccountId(String id) { return Optional.empty(); }
        @Override public boolean existsByPublicAccountId(String id) { return false; }
        @Override public boolean existsByOwnerRfc(String rfc) { return false; }
        @Override public boolean existsByOwnerEmail(String email) { return false; }
        @Override public Optional<Account> findByOwnerEmail(String email) { return Optional.empty(); }
    }

    private static final class EmptyCompanyRepository implements CompanyRepositoryPort {
        @Override public Company save(Company company) { return company; }
        @Override public Optional<Company> findById(UUID id) { return Optional.empty(); }
        @Override public Optional<Company> findByPublicCompanyId(String id) { return Optional.empty(); }
        @Override public List<Company> findByPublicAccountId(String id) { return List.of(); }
        @Override public boolean existsByPublicCompanyId(String id) { return false; }
    }
}
