package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.application.dto.BranchDto;
import com.jcode.jcomercy.companymanagement.domain.model.branch.Branch;
import com.jcode.jcomercy.companymanagement.domain.model.company.DomicilioFiscal;
import com.jcode.jcomercy.companymanagement.domain.port.BranchRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BranchApplicationServiceTest {

    @Test
    void doesNotExposeBranchThroughAnotherCompany() {
        Branch branch = Branch.create("com_owner", "HQ", "Principal", false,
            new DomicilioFiscal("Reforma", "100", "10", "Juárez", "06600", "Cuauhtémoc", "Ciudad de México", "México"), "5512345678");
        InMemoryBranchRepository branches = new InMemoryBranchRepository(branch);
        BranchApplicationService service = new BranchApplicationService(
            branches, new ExistingCompanyRepository(), eventPort(), new IdempotencyRegistry());

        assertThatThrownBy(() -> service.getBranchByPublicId("com_other", branch.getPublicBranchId()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Branch");
    }

    @Test
    void createsBranchOnlyOnceForTheSameIdempotencyKey() {
        InMemoryBranchRepository branches = new InMemoryBranchRepository();
        BranchApplicationService service = new BranchApplicationService(
            branches, new ExistingCompanyRepository(), eventPort(), new IdempotencyRegistry());

        BranchDto first = service.createBranch("com_owner", "HQ", "Principal", true, "Reforma", "100", "10", "Juárez", "06600", "Cuauhtémoc", "Ciudad de México", "México", "5512345678",
            "usr_owner", "cor_1", "idem_1");
        BranchDto second = service.createBranch("com_owner", "HQ", "Principal", true, "Reforma", "100", "10", "Juárez", "06600", "Cuauhtémoc", "Ciudad de México", "México", "5512345678",
            "usr_owner", "cor_1", "idem_1");

        assertThat(second.publicBranchId()).isEqualTo(first.publicBranchId());
        assertThat(branches.saved).hasSize(1);
    }

    @Test
    void requiresMatrixAsFirstBranch() {
        BranchApplicationService service = new BranchApplicationService(
            new InMemoryBranchRepository(), new ExistingCompanyRepository(), eventPort(), new IdempotencyRegistry());

        assertThatThrownBy(() -> service.createBranch("com_owner", "SUC-1", "Sucursal", false,
            "Reforma", "100", "", "Juárez", "06600", "Cuauhtémoc", "Ciudad de México", "México", "5512345678",
            "usr_owner", "cor_2", "idem_2"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("MATRIZ");
    }

    private static EventOutboxPort eventPort() {
        return (eventType, schemaVersion, aggregateVersion, sequence, producerModule, correlationId,
                causationId, accountId, companyId, branchId, actorUserId, dataPayload) -> { };
    }

    private static final class ExistingCompanyRepository implements CompanyRepositoryPort {
        @Override public com.jcode.jcomercy.companymanagement.domain.model.company.Company save(com.jcode.jcomercy.companymanagement.domain.model.company.Company company) { return company; }
        @Override public Optional<com.jcode.jcomercy.companymanagement.domain.model.company.Company> findById(UUID id) { return Optional.empty(); }
        @Override public Optional<com.jcode.jcomercy.companymanagement.domain.model.company.Company> findByPublicCompanyId(String id) { return Optional.empty(); }
        @Override public List<com.jcode.jcomercy.companymanagement.domain.model.company.Company> findByPublicAccountId(String id) { return List.of(); }
        @Override public boolean existsByPublicCompanyId(String id) { return "com_owner".equals(id); }
    }

    private static final class InMemoryBranchRepository implements BranchRepositoryPort {
        private final List<Branch> saved = new ArrayList<>();

        private InMemoryBranchRepository(Branch... initial) {
            saved.addAll(List.of(initial));
        }

        @Override public Branch save(Branch branch) { saved.add(branch); return branch; }
        @Override public Optional<Branch> findById(UUID id) { return saved.stream().filter(b -> b.getId().equals(id)).findFirst(); }
        @Override public Optional<Branch> findByPublicBranchId(String id) { return saved.stream().filter(b -> b.getPublicBranchId().equals(id)).findFirst(); }
        @Override public Optional<Branch> findByPublicCompanyIdAndPublicBranchId(String companyId, String branchId) {
            return saved.stream().filter(b -> b.getPublicCompanyId().equals(companyId) && b.getPublicBranchId().equals(branchId)).findFirst();
        }
        @Override public List<Branch> findByPublicCompanyId(String companyId) { return saved.stream().filter(b -> b.getPublicCompanyId().equals(companyId)).toList(); }
        @Override public Optional<Branch> findHeadOfficeByPublicCompanyId(String companyId) { return Optional.empty(); }
        @Override public boolean existsByPublicCompanyIdAndCode(String companyId, String code) { return saved.stream().anyMatch(b -> b.getPublicCompanyId().equals(companyId) && b.getCode().equals(code)); }
    }
}
