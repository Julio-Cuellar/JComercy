package com.jcode.jcomercy.companymanagement.application.dto;

import com.jcode.jcomercy.companymanagement.domain.model.account.Account;

import java.time.Instant;

public record AccountDto(
    String publicAccountId,
    String name,
    String status,
    String ownerUserId,
    Instant createdAt,
    Instant updatedAt,
    long version
) {
    public static AccountDto fromDomain(Account account) {
        return new AccountDto(
            account.getPublicAccountId(),
            account.getName(),
            account.getStatus().name(),
            account.getOwnerUserId(),
            account.getCreatedAt(),
            account.getUpdatedAt(),
            account.getVersion()
        );
    }
}
