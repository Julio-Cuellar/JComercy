package com.jcode.jcomercy.companymanagement.infra.config;

import com.jcode.jcomercy.companymanagement.application.service.AccountApplicationService;
import com.jcode.jcomercy.companymanagement.application.service.BranchApplicationService;
import com.jcode.jcomercy.companymanagement.application.service.CompanyApplicationService;
import com.jcode.jcomercy.companymanagement.domain.port.AccountRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.BranchRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;
import com.jcode.jcomercy.companymanagement.domain.port.PasswordHasher;
import com.jcode.jcomercy.companymanagement.application.service.IdempotencyRegistry;
import com.jcode.jcomercy.companymanagement.application.service.IdempotencyStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableTransactionManagement
@EnableScheduling
public class CompanyManagementSpringConfig {

    @Bean
    public IdempotencyRegistry idempotencyRegistry(IdempotencyStore store) {
        return new IdempotencyRegistry(store);
    }

    @Bean
    public AccountApplicationService accountApplicationService(
        AccountRepositoryPort accountRepository,
        EventOutboxPort outboxPort,
        IdempotencyRegistry idempotencyRegistry,
        PasswordHasher passwordHasher
    ) {
        return new AccountApplicationService(accountRepository, outboxPort, idempotencyRegistry, passwordHasher);
    }

    @Bean
    public CompanyApplicationService companyApplicationService(
        CompanyRepositoryPort companyRepository,
        AccountRepositoryPort accountRepository,
        EventOutboxPort outboxPort,
        IdempotencyRegistry idempotencyRegistry
    ) {
        return new CompanyApplicationService(companyRepository, accountRepository, outboxPort, idempotencyRegistry);
    }

    @Bean
    public BranchApplicationService branchApplicationService(
        BranchRepositoryPort branchRepository,
        CompanyRepositoryPort companyRepository,
        EventOutboxPort outboxPort,
        IdempotencyRegistry idempotencyRegistry
    ) {
        return new BranchApplicationService(branchRepository, companyRepository, outboxPort, idempotencyRegistry);
    }
}
