package com.jcode.jcomercy.companymanagement.domain.port;

import com.jcode.jcomercy.companymanagement.domain.model.company.Company;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepositoryPort {
    Company save(Company company);
    Optional<Company> findById(UUID id);
    Optional<Company> findByPublicCompanyId(String publicCompanyId);
    default Optional<Company> findByPublicCompanyIdForUpdate(String publicCompanyId) { return findByPublicCompanyId(publicCompanyId); }
    List<Company> findByPublicAccountId(String publicAccountId);
    boolean existsByPublicCompanyId(String publicCompanyId);
}
