package com.jcode.jcomercy.companymanagement.infra.graphql;

import com.jcode.jcomercy.companymanagement.application.dto.CompanyDto;
import com.jcode.jcomercy.companymanagement.application.service.CompanyApplicationService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import com.jcode.jcomercy.companymanagement.infra.security.EffectivePermissions;

import java.util.List;

@Controller
public class CompanyGraphQlController {

    private final CompanyApplicationService service;
    private final EffectivePermissions permissions;

    public CompanyGraphQlController(CompanyApplicationService service, EffectivePermissions permissions) {

        this.service = service;
        this.permissions = permissions;
    }

    @QueryMapping
    public CompanyDto company(@Argument String publicCompanyId, Authentication authentication) {
        return permissions.canInCompany(authentication, publicCompanyId, "companies.read")
                ? service.getCompanyByPublicId(publicCompanyId) : null;
    }

    @QueryMapping
    public List<CompanyDto> companiesByAccount(@Argument String publicAccountId, Authentication authentication) {
        return permissions.canInAccount(authentication, publicAccountId, "accounts.companies.read")
                ? service.listCompaniesByAccount(publicAccountId) : List.of();
    }
}
