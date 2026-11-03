package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.application.dto.AccountDto;
import com.jcode.jcomercy.companymanagement.domain.exception.EntityNotFoundException;
import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;
import com.jcode.jcomercy.companymanagement.domain.exception.DuplicateResourceException;
import com.jcode.jcomercy.companymanagement.domain.model.account.Account;
import com.jcode.jcomercy.companymanagement.domain.model.account.OwnerPerson;
import com.jcode.jcomercy.companymanagement.domain.port.AccountRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;
import com.jcode.jcomercy.companymanagement.domain.port.PasswordHasher;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.transaction.annotation.Transactional;

public class AccountApplicationService {
    private final AccountRepositoryPort accountRepository;
    private final EventOutboxPort outboxPort;
    private final IdempotencyRegistry idempotencyRegistry;
    private final PasswordHasher passwordHasher;

    public AccountApplicationService(AccountRepositoryPort accountRepository, EventOutboxPort outboxPort, IdempotencyRegistry idempotencyRegistry, PasswordHasher passwordHasher) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.outboxPort = Objects.requireNonNull(outboxPort);
        this.idempotencyRegistry = Objects.requireNonNull(idempotencyRegistry);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
    }

    @Transactional
    public AccountDto registerAccount(String name, String firstName, String lastName, LocalDate birthDate, String rfc,
                                      String phoneNumber, String email, String state, String address,
                                      String password,
                                      String correlationId, String idempotencyKey) {
        return idempotencyRegistry.execute("account.register", idempotencyKey,
            () -> registerAccountInternal(name, firstName, lastName, birthDate, rfc, phoneNumber, email, state, address, password, correlationId));
    }

    private AccountDto registerAccountInternal(String name, String firstName, String lastName, LocalDate birthDate, String rfc,
                                               String phoneNumber, String email, String state, String address, String password, String correlationId) {
        if (password == null || password.length() < 8) {
            throw new DomainRuleViolationException("La contraseña debe tener al menos 8 caracteres.");
        }
        OwnerPerson owner = OwnerPerson.create(firstName, lastName, birthDate, rfc, phoneNumber, email, state, address, passwordHasher.hash(password));
        if (accountRepository.existsByOwnerRfc(owner.getRfc())) {
            throw duplicateIdentityConflict();
        }
        if (accountRepository.existsByOwnerEmail(owner.getEmail())) {
            throw duplicateIdentityConflict();
        }
        Account account = Account.create(name, owner);
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
            owner.getId().toString(),
            Map.of(
                "accountId", saved.getPublicAccountId(),
                "name", saved.getName(),
                "ownerPersonId", saved.getOwner().getId().toString(),
                "status", saved.getStatus().name()
            )
        );

        return AccountDto.fromDomain(saved);
    }

    private DuplicateResourceException duplicateIdentityConflict() {
        return new DuplicateResourceException("Conflicto con los datos proporcionados.");
    }

    public AccountDto getAccountByPublicId(String publicAccountId) {
        Account account = accountRepository.findByPublicAccountId(publicAccountId)
            .orElseThrow(() -> new EntityNotFoundException("Account", publicAccountId));
        return AccountDto.fromDomain(account);
    }

    @Transactional
    public AccountDto suspendAccount(String publicAccountId, String reason, String actorUserId, String correlationId, String idempotencyKey) {
        return idempotencyRegistry.execute("account.suspend:" + publicAccountId, idempotencyKey,
            () -> suspendAccountInternal(publicAccountId, reason, actorUserId, correlationId));
    }

    private AccountDto suspendAccountInternal(String publicAccountId, String reason, String actorUserId, String correlationId) {
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
