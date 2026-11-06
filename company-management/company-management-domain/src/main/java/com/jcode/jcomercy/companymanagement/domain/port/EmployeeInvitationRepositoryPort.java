package com.jcode.jcomercy.companymanagement.domain.port;

import com.jcode.jcomercy.companymanagement.domain.model.employee.EmployeeInvitation;

import java.util.List;
import java.util.Optional;

public interface EmployeeInvitationRepositoryPort {
    EmployeeInvitation save(EmployeeInvitation invitation);
    Optional<EmployeeInvitation> findByPublicInvitationId(String publicInvitationId);
    Optional<EmployeeInvitation> findByTokenHash(String tokenHash);
    /** Las invitaciones de una persona, de la más nueva a la más vieja. */
    List<EmployeeInvitation> findByPublicEmployeeId(String publicEmployeeId);
}
