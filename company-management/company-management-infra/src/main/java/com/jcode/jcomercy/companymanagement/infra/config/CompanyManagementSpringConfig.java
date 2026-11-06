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

    @Bean
    public com.jcode.jcomercy.companymanagement.application.service.MembershipApplicationService membershipApplicationService(
        com.jcode.jcomercy.companymanagement.domain.port.MembershipRepositoryPort membershipRepository,
        com.jcode.jcomercy.companymanagement.domain.port.RoleRepositoryPort roleRepository,
        CompanyRepositoryPort companyRepository
    ) {
        return new com.jcode.jcomercy.companymanagement.application.service.MembershipApplicationService(
            membershipRepository, roleRepository, companyRepository);
    }

    @Bean
    public com.jcode.jcomercy.companymanagement.application.service.RoleApplicationService roleApplicationService(
        com.jcode.jcomercy.companymanagement.domain.port.RoleRepositoryPort roleRepository,
        CompanyRepositoryPort companyRepository,
        com.jcode.jcomercy.companymanagement.domain.port.MembershipRepositoryPort membershipRepository,
        EventOutboxPort outboxPort,
        IdempotencyRegistry idempotencyRegistry
    ) {
        return new com.jcode.jcomercy.companymanagement.application.service.RoleApplicationService(
            roleRepository, companyRepository, membershipRepository, outboxPort, idempotencyRegistry);
    }

    @Bean
    public com.jcode.jcomercy.companymanagement.application.service.EmployeeApplicationService employeeApplicationService(
        com.jcode.jcomercy.companymanagement.domain.port.EmployeeProfileRepositoryPort employeeRepository,
        CompanyRepositoryPort companyRepository,
        com.jcode.jcomercy.companymanagement.domain.port.RoleRepositoryPort roleRepository,
        BranchRepositoryPort branchRepository,
        AccountRepositoryPort accountRepository,
        IdempotencyRegistry idempotencyRegistry
    ) {
        return new com.jcode.jcomercy.companymanagement.application.service.EmployeeApplicationService(
            employeeRepository, companyRepository, roleRepository, branchRepository, accountRepository, idempotencyRegistry);
    }
}
