package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.application.dto.BranchDto;
import com.jcode.jcomercy.companymanagement.domain.exception.DuplicateResourceException;
import com.jcode.jcomercy.companymanagement.domain.exception.EntityNotFoundException;
import com.jcode.jcomercy.companymanagement.domain.model.branch.Branch;
import com.jcode.jcomercy.companymanagement.domain.port.BranchRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class BranchApplicationService {
    private final BranchRepositoryPort branchRepository;
    private final CompanyRepositoryPort companyRepository;
    private final EventOutboxPort outboxPort;

    public BranchApplicationService(BranchRepositoryPort branchRepository, CompanyRepositoryPort companyRepository, EventOutboxPort outboxPort) {
        this.branchRepository = Objects.requireNonNull(branchRepository);
        this.companyRepository = Objects.requireNonNull(companyRepository);
        this.outboxPort = Objects.requireNonNull(outboxPort);
    }

    public BranchDto createBranch(String publicCompanyId, String code, String name, boolean isHeadOffice, String actorUserId, String correlationId) {
        if (!companyRepository.existsByPublicCompanyId(publicCompanyId)) {
            throw new EntityNotFoundException("Company", publicCompanyId);
        }
        if (branchRepository.existsByPublicCompanyIdAndCode(publicCompanyId, code)) {
            throw new DuplicateResourceException("Branch", "code", code);
        }

        Branch branch = Branch.create(publicCompanyId, code, name, isHeadOffice);
        Branch saved = branchRepository.save(branch);

        outboxPort.recordEvent(
            "branch.created",
            "1.0",
            saved.getVersion(),
            1L,
            "company_management",
            correlationId != null ? correlationId : "cor_" + UUID.randomUUID().toString().replace("-", ""),
            null,
            "",
            publicCompanyId,
            saved.getPublicBranchId(),
            actorUserId,
            Map.of(
                "branchId", saved.getPublicBranchId(),
                "companyId", publicCompanyId,
                "code", saved.getCode(),
                "name", saved.getName(),
                "isHeadOffice", saved.isHeadOffice(),
                "status", saved.getStatus().name()
            )
        );

        return BranchDto.fromDomain(saved);
    }

    public BranchDto getBranchByPublicId(String publicBranchId) {
        Branch branch = branchRepository.findByPublicBranchId(publicBranchId)
            .orElseThrow(() -> new EntityNotFoundException("Branch", publicBranchId));
        return BranchDto.fromDomain(branch);
    }

    public List<BranchDto> listBranchesByCompany(String publicCompanyId) {
        return branchRepository.findByPublicCompanyId(publicCompanyId)
            .stream()
            .map(BranchDto::fromDomain)
            .toList();
    }

    public BranchDto activateBranch(String publicBranchId, String actorUserId, String correlationId) {
        Branch branch = branchRepository.findByPublicBranchId(publicBranchId)
            .orElseThrow(() -> new EntityNotFoundException("Branch", publicBranchId));

        branch.activate();
        Branch saved = branchRepository.save(branch);

        outboxPort.recordEvent(
            "branch.activated",
            "1.0",
            saved.getVersion(),
            2L,
            "company_management",
            correlationId != null ? correlationId : "cor_" + UUID.randomUUID().toString().replace("-", ""),
            null,
            "",
            saved.getPublicCompanyId(),
            saved.getPublicBranchId(),
            actorUserId,
            Map.of(
                "branchId", saved.getPublicBranchId(),
                "companyId", saved.getPublicCompanyId(),
                "status", saved.getStatus().name()
            )
        );

        return BranchDto.fromDomain(saved);
    }
}
