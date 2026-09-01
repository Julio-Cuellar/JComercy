package com.jcode.jcomercy.companymanagement.infra.graphql;

import com.jcode.jcomercy.companymanagement.application.dto.CompanyDto;
import com.jcode.jcomercy.companymanagement.application.service.CompanyApplicationService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import com.jcode.jcomercy.companymanagement.infra.security.JwtAuthorizationService;

import java.util.List;

@Controller
public class CompanyGraphQlController {

    private final CompanyApplicationService service;
    private final JwtAuthorizationService authorization;

    public CompanyGraphQlController(CompanyApplicationService service, JwtAuthorizationService authorization) {

        this.service = service;
        this.authorization = authorization;
    }

    @QueryMapping
    public CompanyDto company(@Argument String publicCompanyId, Authentication authentication) {
        return authorization.canAccessCompany(authentication, publicCompanyId)
                ? service.getCompanyByPublicId(publicCompanyId) : null;
    }

    @QueryMapping
    public List<CompanyDto> companiesByAccount(@Argument String publicAccountId, Authentication authentication) {
        return authorization.canAccessAccount(authentication, publicAccountId)
                ? service.listCompaniesByAccount(publicAccountId) : List.of();
    }
}
