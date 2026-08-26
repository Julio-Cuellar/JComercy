package com.jcode.jcomercy.companymanagement.infra.persistence.repository;

import com.jcode.jcomercy.companymanagement.infra.persistence.entity.OutboxEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OutboxSpringDataRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {
    boolean existsByEventId(String eventId);
}
