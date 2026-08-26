package com.jcode.jcomercy.companymanagement.application.dto;

import com.jcode.jcomercy.companymanagement.domain.model.membership.CompanyMembership;

import java.time.Instant;
import java.util.Set;

public record MembershipDto(
    String publicMembershipId,
    String publicCompanyId,
    String userId,
    String status,
    Set<String> roles,
    Instant createdAt,
    Instant updatedAt,
    long version
) {
    public static MembershipDto fromDomain(CompanyMembership membership) {
        return new MembershipDto(
            membership.getPublicMembershipId(),
            membership.getPublicCompanyId(),
            membership.getUserId(),
            membership.getStatus().name(),
            membership.getRoles(),
            membership.getCreatedAt(),
            membership.getUpdatedAt(),
            membership.getVersion()
        );
    }
}
