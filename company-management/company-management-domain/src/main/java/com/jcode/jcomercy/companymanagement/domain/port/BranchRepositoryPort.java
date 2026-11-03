package com.jcode.jcomercy.companymanagement.domain.port;

import com.jcode.jcomercy.companymanagement.domain.model.branch.Branch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepositoryPort {
    Branch save(Branch branch);
    Optional<Branch> findById(UUID id);
    Optional<Branch> findByPublicBranchId(String publicBranchId);
    Optional<Branch> findByPublicCompanyIdAndPublicBranchId(String publicCompanyId, String publicBranchId);
    List<Branch> findByPublicCompanyId(String publicCompanyId);
    Optional<Branch> findHeadOfficeByPublicCompanyId(String publicCompanyId);
    boolean existsByPublicCompanyIdAndCode(String publicCompanyId, String code);
}
