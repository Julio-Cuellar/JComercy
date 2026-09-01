package com.jcode.jcomercy.companymanagement.infra.persistence.adapter;

import com.jcode.jcomercy.companymanagement.domain.model.account.Account;
import com.jcode.jcomercy.companymanagement.domain.port.AccountRepositoryPort;
import com.jcode.jcomercy.companymanagement.infra.persistence.entity.AccountJpaEntity;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.AccountSpringDataRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class AccountRepositoryAdapter implements AccountRepositoryPort {
    private final AccountSpringDataRepository repository;

    public AccountRepositoryAdapter(AccountSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Account save(Account account) {
        AccountJpaEntity entity = AccountJpaEntity.fromDomain(account);
        AccountJpaEntity saved = repository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return repository.findById(id).map(AccountJpaEntity::toDomain);
    }

    @Override
    public Optional<Account> findByPublicAccountId(String publicAccountId) {
        return repository.findByPublicAccountId(publicAccountId).map(AccountJpaEntity::toDomain);
    }

    @Override
    public boolean existsByPublicAccountId(String publicAccountId) {
        return repository.existsByPublicAccountId(publicAccountId);
    }

    @Override
    public boolean existsByOwnerRfc(String rfc) {
        return repository.existsByOwner_Rfc(rfc);
    }

    @Override
    public boolean existsByOwnerEmail(String email) {
        return repository.existsByOwner_Email(email);
    }

    @Override
    public Optional<Account> findByOwnerEmail(String email) {
        return repository.findByOwner_Email(email).map(AccountJpaEntity::toDomain);
    }
}
