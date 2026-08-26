package com.jcode.jcomercy.companymanagement.infra.persistence.adapter;

import com.jcode.jcomercy.companymanagement.domain.model.company.Company;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.companymanagement.infra.persistence.entity.CompanyJpaEntity;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.CompanySpringDataRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CompanyRepositoryAdapter implements CompanyRepositoryPort {
    private final CompanySpringDataRepository repository;

    public CompanyRepositoryAdapter(CompanySpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Company save(Company company) {
        CompanyJpaEntity entity = CompanyJpaEntity.fromDomain(company);
        CompanyJpaEntity saved = repository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Company> findById(UUID id) {
        return repository.findById(id).map(CompanyJpaEntity::toDomain);
    }

    @Override
    public Optional<Company> findByPublicCompanyId(String publicCompanyId) {
        return repository.findByPublicCompanyId(publicCompanyId).map(CompanyJpaEntity::toDomain);
    }

    @Override
    public List<Company> findByPublicAccountId(String publicAccountId) {
        return repository.findByPublicAccountId(publicAccountId).stream().map(CompanyJpaEntity::toDomain).toList();
    }

    @Override
    public boolean existsByPublicCompanyId(String publicCompanyId) {
        return repository.existsByPublicCompanyId(publicCompanyId);
    }
}
