package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.application.dto.AccountDto;
import com.jcode.jcomercy.companymanagement.domain.exception.EntityNotFoundException;
import com.jcode.jcomercy.companymanagement.domain.model.account.Account;
import com.jcode.jcomercy.companymanagement.domain.port.AccountRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class AccountApplicationService {
    private final AccountRepositoryPort accountRepository;
    private final EventOutboxPort outboxPort;

    public AccountApplicationService(AccountRepositoryPort accountRepository, EventOutboxPort outboxPort) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.outboxPort = Objects.requireNonNull(outboxPort);
    }

    public AccountDto registerAccount(String name, String ownerUserId, String correlationId) {
        Account account = Account.create(name, ownerUserId);
        Account saved = accountRepository.save(account);

        // Record outbox event in same transaction
        outboxPort.recordEvent(
            "account.created",
            "1.0",
            saved.getVersion(),
            1L,
            "company_management",
            correlationId != null ? correlationId : "cor_" + UUID.randomUUID().toString().replace("-", ""),
            null,
            saved.getPublicAccountId(),
            "",
            null,
            ownerUserId,
            Map.of(
                "accountId", saved.getPublicAccountId(),
                "name", saved.getName(),
                "ownerUserId", saved.getOwnerUserId(),
                "status", saved.getStatus().name()
            )
        );

        return AccountDto.fromDomain(saved);
    }

    public AccountDto getAccountByPublicId(String publicAccountId) {
        Account account = accountRepository.findByPublicAccountId(publicAccountId)
            .orElseThrow(() -> new EntityNotFoundException("Account", publicAccountId));
        return AccountDto.fromDomain(account);
    }

    public AccountDto suspendAccount(String publicAccountId, String reason, String actorUserId, String correlationId) {
        Account account = accountRepository.findByPublicAccountId(publicAccountId)
            .orElseThrow(() -> new EntityNotFoundException("Account", publicAccountId));

        account.suspend(reason);
        Account saved = accountRepository.save(account);

        outboxPort.recordEvent(
            "account.suspended",
            "1.0",
            saved.getVersion(),
            2L,
            "company_management",
            correlationId != null ? correlationId : "cor_" + UUID.randomUUID().toString().replace("-", ""),
            null,
            saved.getPublicAccountId(),
            "",
            null,
            actorUserId,
            Map.of(
                "accountId", saved.getPublicAccountId(),
                "reason", reason != null ? reason : "Suspensión administrativa",
                "status", saved.getStatus().name()
            )
        );

        return AccountDto.fromDomain(saved);
    }
}
