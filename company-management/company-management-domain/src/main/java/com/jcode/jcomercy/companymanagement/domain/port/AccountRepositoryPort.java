package com.jcode.jcomercy.companymanagement.domain.port;

import com.jcode.jcomercy.companymanagement.domain.model.account.Account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepositoryPort {
    Account save(Account account);
    Optional<Account> findById(UUID id);
    Optional<Account> findByPublicAccountId(String publicAccountId);
    boolean existsByPublicAccountId(String publicAccountId);
}
