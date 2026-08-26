package com.jcode.jcomercy.companymanagement.infra.config;

import com.jcode.jcomercy.companymanagement.application.service.AccountApplicationService;
import com.jcode.jcomercy.companymanagement.application.service.BranchApplicationService;
import com.jcode.jcomercy.companymanagement.application.service.CompanyApplicationService;
import com.jcode.jcomercy.companymanagement.domain.port.AccountRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.BranchRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class CompanyManagementSpringConfig {

    @Bean
    public AccountApplicationService accountApplicationService(
        AccountRepositoryPort accountRepository,
        EventOutboxPort outboxPort
    ) {
        return new AccountApplicationService(accountRepository, outboxPort);
    }

    @Bean
    public CompanyApplicationService companyApplicationService(
        CompanyRepositoryPort companyRepository,
        EventOutboxPort outboxPort
    ) {
        return new CompanyApplicationService(companyRepository, outboxPort);
    }

    @Bean
    public BranchApplicationService branchApplicationService(
        BranchRepositoryPort branchRepository,
        CompanyRepositoryPort companyRepository,
        EventOutboxPort outboxPort
    ) {
        return new BranchApplicationService(branchRepository, companyRepository, outboxPort);
    }
}
