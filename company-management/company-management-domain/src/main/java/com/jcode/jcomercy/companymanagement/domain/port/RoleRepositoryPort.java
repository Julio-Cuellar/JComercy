package com.jcode.jcomercy.companymanagement.domain.port;

import com.jcode.jcomercy.companymanagement.domain.model.role.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepositoryPort {
    Role save(Role role);
    Optional<Role> findById(UUID id);
    Optional<Role> findByPublicRoleId(String publicRoleId);
    List<Role> findByPublicCompanyIdOrSystem(String publicCompanyId);
}
