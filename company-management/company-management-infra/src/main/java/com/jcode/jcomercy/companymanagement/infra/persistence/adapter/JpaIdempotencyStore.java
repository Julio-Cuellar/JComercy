package com.jcode.jcomercy.companymanagement.infra.persistence.adapter;

import com.jcode.jcomercy.companymanagement.application.service.IdempotencyStore;
import com.jcode.jcomercy.companymanagement.infra.persistence.entity.IdempotencyKeyJpaEntity;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.IdempotencyKeySpringDataRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaIdempotencyStore implements IdempotencyStore {
    private final IdempotencyKeySpringDataRepository repository;
    public JpaIdempotencyStore(IdempotencyKeySpringDataRepository repository){this.repository=repository;}
    @Override @Transactional
    public boolean claim(String operation, String key) {
        if (repository.existsByOperationAndKey(operation,key)) return false;
        try { repository.saveAndFlush(IdempotencyKeyJpaEntity.create(operation,key)); return true; }
        catch (DataIntegrityViolationException ex) { return false; }
    }
    @Override @Transactional public void release(String operation,String key){repository.deleteByOperationAndKey(operation,key);}
}
