package com.jcode.jcomercy.companymanagement.infra.persistence.repository;
import com.jcode.jcomercy.companymanagement.infra.persistence.entity.RefreshTokenJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional; import java.util.UUID;
public interface RefreshTokenSpringDataRepository extends JpaRepository<RefreshTokenJpaEntity,UUID>{ Optional<RefreshTokenJpaEntity> findByTokenHash(String hash); }
