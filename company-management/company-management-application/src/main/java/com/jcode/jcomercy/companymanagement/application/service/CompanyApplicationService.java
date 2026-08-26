package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.application.dto.CompanyDto;
import com.jcode.jcomercy.companymanagement.domain.exception.EntityNotFoundException;
import com.jcode.jcomercy.companymanagement.domain.model.company.Company;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class CompanyApplicationService {
    private final CompanyRepositoryPort companyRepository;
    private final EventOutboxPort outboxPort;

    public CompanyApplicationService(CompanyRepositoryPort companyRepository, EventOutboxPort outboxPort) {
        this.companyRepository = Objects.requireNonNull(companyRepository);
        this.outboxPort = Objects.requireNonNull(outboxPort);
    }

    public CompanyDto createCompany(String publicAccountId, String legalName, String tradeName, String taxId, String actorUserId, String correlationId) {
        Company company = Company.create(publicAccountId, legalName, tradeName, taxId);
        Company saved = companyRepository.save(company);

        outboxPort.recordEvent(
            "company.created",
            "1.0",
            saved.getVersion(),
            1L,
            "company_management",
            correlationId != null ? correlationId : "cor_" + UUID.randomUUID().toString().replace("-", ""),
            null,
            publicAccountId,
            saved.getPublicCompanyId(),
            null,
            actorUserId,
            Map.of(
                "companyId", saved.getPublicCompanyId(),
                "legalName", saved.getLegalName(),
                "tradeName", saved.getTradeName(),
                "taxId", saved.getTaxId(),
                "status", saved.getStatus().name()
            )
        );

        return CompanyDto.fromDomain(saved);
    }

    public CompanyDto getCompanyByPublicId(String publicCompanyId) {
        Company company = companyRepository.findByPublicCompanyId(publicCompanyId)
            .orElseThrow(() -> new EntityNotFoundException("Company", publicCompanyId));
        return CompanyDto.fromDomain(company);
    }

    public List<CompanyDto> listCompaniesByAccount(String publicAccountId) {
        return companyRepository.findByPublicAccountId(publicAccountId)
            .stream()
            .map(CompanyDto::fromDomain)
            .toList();
    }

    public CompanyDto activateCompany(String publicCompanyId, String actorUserId, String correlationId) {
        Company company = companyRepository.findByPublicCompanyId(publicCompanyId)
            .orElseThrow(() -> new EntityNotFoundException("Company", publicCompanyId));

        company.activate();
        Company saved = companyRepository.save(company);

        outboxPort.recordEvent(
            "company.activated",
            "1.0",
            saved.getVersion(),
            2L,
            "company_management",
            correlationId != null ? correlationId : "cor_" + UUID.randomUUID().toString().replace("-", ""),
            null,
            saved.getPublicAccountId(),
            saved.getPublicCompanyId(),
            null,
            actorUserId,
            Map.of(
                "companyId", saved.getPublicCompanyId(),
                "status", saved.getStatus().name()
            )
        );

        return CompanyDto.fromDomain(saved);
    }
}
