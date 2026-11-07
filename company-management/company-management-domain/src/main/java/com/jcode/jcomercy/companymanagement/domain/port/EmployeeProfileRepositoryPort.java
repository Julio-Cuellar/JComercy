package com.jcode.jcomercy.companymanagement.domain.port;

import com.jcode.jcomercy.companymanagement.domain.model.employee.EmployeeProfile;

import java.util.List;
import java.util.Optional;

public interface EmployeeProfileRepositoryPort {
    EmployeeProfile save(EmployeeProfile profile);
    Optional<EmployeeProfile> findByPublicEmployeeId(String publicEmployeeId);
    List<EmployeeProfile> findByPublicCompanyId(String publicCompanyId);
}
