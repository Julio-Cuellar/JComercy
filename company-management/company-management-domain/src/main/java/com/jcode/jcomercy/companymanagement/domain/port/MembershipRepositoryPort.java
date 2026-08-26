package com.jcode.jcomercy.companymanagement.domain.port;

import com.jcode.jcomercy.companymanagement.domain.model.assignment.BranchAssignment;
import com.jcode.jcomercy.companymanagement.domain.model.membership.CompanyMembership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepositoryPort {
    CompanyMembership saveMembership(CompanyMembership membership);
    Optional<CompanyMembership> findMembershipById(UUID id);
    Optional<CompanyMembership> findMembershipByPublicId(String publicMembershipId);
    Optional<CompanyMembership> findMembershipByCompanyAndUser(String publicCompanyId, String userId);
    List<CompanyMembership> findMembershipsByPublicCompanyId(String publicCompanyId);

    BranchAssignment saveAssignment(BranchAssignment assignment);
    Optional<BranchAssignment> findAssignmentByPublicId(String publicAssignmentId);
    List<BranchAssignment> findAssignmentsByPublicMembershipId(String publicMembershipId);
    boolean existsAssignment(String publicMembershipId, String publicBranchId);
}
