package com.jcode.jcomercy.companymanagement.infra.persistence.adapter;

import com.jcode.jcomercy.companymanagement.domain.model.branch.Branch;
import com.jcode.jcomercy.companymanagement.domain.port.BranchRepositoryPort;
import com.jcode.jcomercy.companymanagement.infra.persistence.entity.BranchJpaEntity;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.BranchSpringDataRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class BranchRepositoryAdapter implements BranchRepositoryPort {
    private final BranchSpringDataRepository repository;

    public BranchRepositoryAdapter(BranchSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Branch save(Branch branch) {
        BranchJpaEntity entity = BranchJpaEntity.fromDomain(branch);
        BranchJpaEntity saved = repository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Branch> findById(UUID id) {
        return repository.findById(id).map(BranchJpaEntity::toDomain);
    }

    @Override
    public Optional<Branch> findByPublicBranchId(String publicBranchId) {
        return repository.findByPublicBranchId(publicBranchId).map(BranchJpaEntity::toDomain);
    }

    @Override
    public Optional<Branch> findByPublicCompanyIdAndPublicBranchId(String publicCompanyId, String publicBranchId) {
        return repository.findByPublicCompanyIdAndPublicBranchId(publicCompanyId, publicBranchId)
            .map(BranchJpaEntity::toDomain);
    }

    @Override
    public List<Branch> findByPublicCompanyId(String publicCompanyId) {
        return repository.findByPublicCompanyId(publicCompanyId).stream().map(BranchJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<Branch> findHeadOfficeByPublicCompanyId(String publicCompanyId) {
        return repository.findByPublicCompanyIdAndIsHeadOfficeTrue(publicCompanyId).map(BranchJpaEntity::toDomain);
    }

    @Override
    public boolean existsByPublicCompanyIdAndCode(String publicCompanyId, String code) {
        return repository.existsByPublicCompanyIdAndCode(publicCompanyId, code);
    }
}
