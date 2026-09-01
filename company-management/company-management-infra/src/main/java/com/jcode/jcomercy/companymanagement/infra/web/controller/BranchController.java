package com.jcode.jcomercy.companymanagement.infra.web.controller;

import com.jcode.jcomercy.companymanagement.application.dto.BranchDto;
import com.jcode.jcomercy.companymanagement.application.service.BranchApplicationService;
import com.jcode.jcomercy.companymanagement.domain.model.branch.BranchType;
import com.jcode.jcomercy.companymanagement.domain.exception.UnauthorizedAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/v1/companies/{publicCompanyId}/branches")
public class BranchController {
    private static final Logger log = LoggerFactory.getLogger(BranchController.class);

    private final BranchApplicationService branchApplicationService;

    public BranchController(BranchApplicationService branchApplicationService) {
        this.branchApplicationService = branchApplicationService;
    }

    public record CreateBranchRequest(String code, String name, BranchType type,
                                      DomicilioRequest domicilio, String phoneNumber, String email,
                                      String rfc, String fiscalName, String fiscalPostalCode,
                                      String centroCostoId, String almacenId, String listaPreciosId, String serieFacturacion,
                                      Long folioActual, String moneda, String responsableId,
                                      LocalDate fechaApertura, LocalDate fechaCierre) {}
    public record DomicilioRequest(String calle, String numeroExterior, String numeroInterior, String colonia,
                                   String codigoPostal, String municipioAlcaldia, String estado, String pais) {}

    @PostMapping
    public ResponseEntity<BranchDto> createBranch(
        @PathVariable String publicCompanyId,
        @RequestBody CreateBranchRequest request,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        Principal principal,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        BranchDto created = branchApplicationService.createBranch(publicCompanyId, request.code(), request.name(),
            request.type() != null ? request.type() : BranchType.SUCURSAL,
            request.domicilio().calle(), request.domicilio().numeroExterior(), request.domicilio().numeroInterior(), request.domicilio().colonia(),
            request.domicilio().codigoPostal(), request.domicilio().municipioAlcaldia(), request.domicilio().estado(), request.domicilio().pais(), request.phoneNumber(), request.email(),
            request.rfc(), request.fiscalName(), request.fiscalPostalCode(), request.centroCostoId(), request.almacenId(), request.listaPreciosId(), request.serieFacturacion(),
            request.folioActual(), request.moneda(), request.responsableId(), request.fechaApertura(), request.fechaCierre(),
            actor(principal), correlationId, idempotencyKey);
        log.info("Sucursal creada branchId={} companyId={} actor={} correlationId={}", created.publicBranchId(), publicCompanyId, principal.getName(), correlationId);
        return ResponseEntity
            .created(URI.create("/api/v1/companies/" + publicCompanyId + "/branches/" + created.publicBranchId()))
            .header("ETag", "\"" + created.version() + "\"")
            .body(created);
    }

    @GetMapping
    public ResponseEntity<List<BranchDto>> listBranches(@PathVariable String publicCompanyId) {
        List<BranchDto> branches = branchApplicationService.listBranchesByCompany(publicCompanyId);
        return ResponseEntity.ok(branches);
    }

    @GetMapping("/{publicBranchId}")
    public ResponseEntity<BranchDto> getBranch(@PathVariable String publicCompanyId, @PathVariable String publicBranchId) {
        BranchDto branch = branchApplicationService.getBranchByPublicId(publicCompanyId, publicBranchId);
        return ResponseEntity
            .ok()
            .header("ETag", "\"" + branch.version() + "\"")
            .body(branch);
    }

    @PostMapping("/{publicBranchId}/activation")
    public ResponseEntity<BranchDto> activateBranch(
        @PathVariable String publicCompanyId,
        @PathVariable String publicBranchId,
        Principal principal,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        BranchDto activated = branchApplicationService.activateBranch(publicCompanyId, publicBranchId, actor(principal), correlationId, idempotencyKey);
        log.info("Sucursal activada branchId={} companyId={} actor={} correlationId={}", publicBranchId, publicCompanyId, principal.getName(), correlationId);
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
