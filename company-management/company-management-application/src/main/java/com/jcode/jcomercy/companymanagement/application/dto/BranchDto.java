package com.jcode.jcomercy.companymanagement.application.dto;

import com.jcode.jcomercy.companymanagement.domain.model.branch.Branch;

import java.time.Instant;

public record BranchDto(
    String publicBranchId,
    String publicCompanyId,
    String code,
    String name,
    boolean isHeadOffice,
    String status,
    String timeZone,
    Instant createdAt,
    Instant updatedAt,
    long version
) {
    public static BranchDto fromDomain(Branch branch) {
        return new BranchDto(
            branch.getPublicBranchId(),
            branch.getPublicCompanyId(),
            branch.getCode(),
            branch.getName(),
            branch.isHeadOffice(),
            branch.getStatus().name(),
            branch.getTimeZone(),
            branch.getCreatedAt(),
            branch.getUpdatedAt(),
            branch.getVersion()
        );
    }
}
