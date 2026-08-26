package com.jcode.jcomercy.companymanagement.infra.web.controller;

import com.jcode.jcomercy.companymanagement.application.dto.CompanyDto;
import com.jcode.jcomercy.companymanagement.application.service.CompanyApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CompanyController {

    private final CompanyApplicationService companyApplicationService;

    public CompanyController(CompanyApplicationService companyApplicationService) {
        this.companyApplicationService = companyApplicationService;
    }

    public record CreateCompanyRequest(String legalName, String tradeName, String taxId) {}

    @PostMapping("/accounts/{publicAccountId}/companies")
    public ResponseEntity<CompanyDto> createCompany(
        @PathVariable String publicAccountId,
        @RequestBody CreateCompanyRequest request,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        @RequestHeader(value = "X-Actor-User-Id", required = false) String actorUserId,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        CompanyDto created = companyApplicationService.createCompany(publicAccountId, request.legalName(), request.tradeName(), request.taxId(), actorUserId, correlationId);
        return ResponseEntity
            .created(URI.create("/api/v1/companies/" + created.publicCompanyId()))
            .header("ETag", "\"" + created.version() + "\"")
            .body(created);
    }

    @GetMapping("/companies/{publicCompanyId}")
    public ResponseEntity<CompanyDto> getCompany(@PathVariable String publicCompanyId) {
        CompanyDto company = companyApplicationService.getCompanyByPublicId(publicCompanyId);
        return ResponseEntity
            .ok()
            .header("ETag", "\"" + company.version() + "\"")
            .body(company);
    }

    @GetMapping("/accounts/{publicAccountId}/companies")
    public ResponseEntity<List<CompanyDto>> listCompaniesByAccount(@PathVariable String publicAccountId) {
        List<CompanyDto> companies = companyApplicationService.listCompaniesByAccount(publicAccountId);
        return ResponseEntity.ok(companies);
    }

    @PostMapping("/companies/{publicCompanyId}/activation")
    public ResponseEntity<CompanyDto> activateCompany(
        @PathVariable String publicCompanyId,
        @RequestHeader(value = "X-Actor-User-Id", required = false) String actorUserId,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        CompanyDto activated = companyApplicationService.activateCompany(publicCompanyId, actorUserId, correlationId);
        return ResponseEntity
            .ok()
            .header("ETag", "\"" + activated.version() + "\"")
            .body(activated);
    }
}
