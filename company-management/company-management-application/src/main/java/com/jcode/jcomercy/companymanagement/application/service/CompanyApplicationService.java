package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.application.dto.CompanyDto;
import com.jcode.jcomercy.companymanagement.domain.exception.EntityNotFoundException;
import com.jcode.jcomercy.companymanagement.domain.model.company.Company;
import com.jcode.jcomercy.companymanagement.domain.model.company.DomicilioFiscal;
import com.jcode.jcomercy.companymanagement.domain.port.AccountRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.transaction.annotation.Transactional;

public class CompanyApplicationService {
    private final CompanyRepositoryPort companyRepository;
    private final AccountRepositoryPort accountRepository;
    private final EventOutboxPort outboxPort;
    private final IdempotencyRegistry idempotencyRegistry;

    public CompanyApplicationService(CompanyRepositoryPort companyRepository, AccountRepositoryPort accountRepository, EventOutboxPort outboxPort, IdempotencyRegistry idempotencyRegistry) {
        this.companyRepository = Objects.requireNonNull(companyRepository);
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.outboxPort = Objects.requireNonNull(outboxPort);
        this.idempotencyRegistry = Objects.requireNonNull(idempotencyRegistry);
    }

    @Transactional
    public CompanyDto createCompany(String publicAccountId, String legalName, String tradeName, String rfc, String taxRegime,
                                    LocalDate incorporationDate, String phone, String email, String website, String logoUrl,
                                    String calle, String numeroExterior, String numeroInterior, String colonia, String codigoPostal,
                                    String municipioAlcaldia, String estado, String pais,
                                    String actorUserId, String correlationId, String idempotencyKey) {
        return idempotencyRegistry.execute("company.create:" + publicAccountId, idempotencyKey,
            () -> createCompanyInternal(publicAccountId, legalName, tradeName, rfc, taxRegime, incorporationDate, phone, email, website, logoUrl,
                calle, numeroExterior, numeroInterior, colonia, codigoPostal, municipioAlcaldia, estado, pais, actorUserId, correlationId));
    }

    private CompanyDto createCompanyInternal(String publicAccountId, String legalName, String tradeName, String rfc, String taxRegime,
                                              LocalDate incorporationDate, String phone, String email, String website, String logoUrl,
                                              String calle, String numeroExterior, String numeroInterior, String colonia, String codigoPostal,
                                              String municipioAlcaldia, String estado, String pais, String actorUserId, String correlationId) {
        if (!accountRepository.existsByPublicAccountId(publicAccountId)) {
            throw new EntityNotFoundException("Account", publicAccountId);
        }
        Company company = Company.create(publicAccountId, legalName, tradeName, rfc, taxRegime, incorporationDate, phone, email, website, logoUrl,
            new DomicilioFiscal(calle, numeroExterior, numeroInterior, colonia, codigoPostal, municipioAlcaldia, estado, pais));
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
                "rfc", saved.getRfc(),
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

    @Transactional
    public CompanyDto activateCompany(String publicCompanyId, String actorUserId, String correlationId, String idempotencyKey) {
        return idempotencyRegistry.execute("company.activate:" + publicCompanyId, idempotencyKey,
            () -> activateCompanyInternal(publicCompanyId, actorUserId, correlationId));
    }

    private CompanyDto activateCompanyInternal(String publicCompanyId, String actorUserId, String correlationId) {
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
