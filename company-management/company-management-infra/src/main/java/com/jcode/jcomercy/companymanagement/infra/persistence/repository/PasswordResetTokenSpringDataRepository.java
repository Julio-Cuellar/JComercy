package com.jcode.jcomercy.companymanagement.infra.persistence.repository;
import com.jcode.jcomercy.companymanagement.infra.persistence.entity.PasswordResetTokenJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional; import java.util.UUID;
public interface PasswordResetTokenSpringDataRepository extends JpaRepository<PasswordResetTokenJpaEntity,UUID>{ Optional<PasswordResetTokenJpaEntity> findByTokenHash(String hash); }
