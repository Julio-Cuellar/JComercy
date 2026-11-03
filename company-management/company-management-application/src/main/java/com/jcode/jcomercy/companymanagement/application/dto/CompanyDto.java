package com.jcode.jcomercy.companymanagement.application.dto;

import com.jcode.jcomercy.companymanagement.domain.model.company.Company;

import java.time.Instant;

public record CompanyDto(
    String publicCompanyId,
    String publicAccountId,
    String legalName,
    String tradeName,
    String rfc,
    String taxRegime,
    java.time.LocalDate incorporationDate,
    String phone,
    String email,
    String website,
    String logoUrl,
    DomicilioFiscalDto fiscalAddress,
    String status,
    String preferencesJson,
    Instant createdAt,
    Instant updatedAt,
    long version
) {
    public static CompanyDto fromDomain(Company company) {
        return new CompanyDto(
            company.getPublicCompanyId(),
            company.getPublicAccountId(),
            company.getLegalName(),
            company.getTradeName(),
            company.getRfc(),
            company.getTaxRegime(),
            company.getIncorporationDate(),
            company.getPhone(),
            company.getEmail(),
            company.getWebsite(),
            company.getLogoUrl(),
            DomicilioFiscalDto.fromDomain(company.getFiscalAddress()),
            company.getStatus().name(),
            company.getPreferencesJson(),
            company.getCreatedAt(),
            company.getUpdatedAt(),
            company.getVersion()
        );
    }
}
