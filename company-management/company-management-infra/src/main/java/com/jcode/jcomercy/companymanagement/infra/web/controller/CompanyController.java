package com.jcode.jcomercy.companymanagement.infra.web.controller;

import com.jcode.jcomercy.companymanagement.application.dto.CompanyDto;
import com.jcode.jcomercy.companymanagement.application.service.CompanyApplicationService;
import com.jcode.jcomercy.companymanagement.domain.exception.UnauthorizedAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/v1")
public class CompanyController {
    private static final Logger log = LoggerFactory.getLogger(CompanyController.class);

    private final CompanyApplicationService companyApplicationService;

    public CompanyController(CompanyApplicationService companyApplicationService) {
        this.companyApplicationService = companyApplicationService;
    }

    public record CreateCompanyRequest(String legalName, String tradeName, String rfc, String taxRegime,
                                       LocalDate incorporationDate, String phone, String email, String website, String logoUrl,
                                       FiscalAddressRequest fiscalAddress) {}
    public record FiscalAddressRequest(String calle, String numeroExterior, String numeroInterior, String colonia,
                                       String codigoPostal, String municipioAlcaldia, String estado, String pais) {}

    @PostMapping("/accounts/{publicAccountId}/companies")
    public ResponseEntity<CompanyDto> createCompany(
        @PathVariable String publicAccountId,
        @RequestBody CreateCompanyRequest request,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        Principal principal,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        CompanyDto created = companyApplicationService.createCompany(publicAccountId, request.legalName(), request.tradeName(), request.rfc(), request.taxRegime(),
            request.incorporationDate(), request.phone(), request.email(), request.website(), request.logoUrl(),
            request.fiscalAddress().calle(), request.fiscalAddress().numeroExterior(), request.fiscalAddress().numeroInterior(),
            request.fiscalAddress().colonia(), request.fiscalAddress().codigoPostal(), request.fiscalAddress().municipioAlcaldia(),
            request.fiscalAddress().estado(), request.fiscalAddress().pais(), actor(principal), correlationId, idempotencyKey);
        log.info("Empresa creada companyId={} accountId={} actor={} correlationId={}", created.publicCompanyId(), publicAccountId, principal.getName(), correlationId);
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
        Principal principal,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        CompanyDto activated = companyApplicationService.activateCompany(publicCompanyId, actor(principal), correlationId, idempotencyKey);
        log.info("Empresa activada companyId={} actor={} correlationId={}", publicCompanyId, principal.getName(), correlationId);
        return ResponseEntity
            .ok()
            .header("ETag", "\"" + activated.version() + "\"")
            .body(activated);
    }

    private static String actor(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new UnauthorizedAccessException("Autenticación requerida");
        }
        return principal.getName();
    }
}
