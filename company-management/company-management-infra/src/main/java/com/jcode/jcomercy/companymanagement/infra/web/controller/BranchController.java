package com.jcode.jcomercy.companymanagement.infra.web.controller;

import com.jcode.jcomercy.companymanagement.application.dto.BranchDto;
import com.jcode.jcomercy.companymanagement.application.service.BranchApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/companies/{publicCompanyId}/branches")
public class BranchController {

    private final BranchApplicationService branchApplicationService;

    public BranchController(BranchApplicationService branchApplicationService) {
        this.branchApplicationService = branchApplicationService;
    }

    public record CreateBranchRequest(String code, String name, boolean isHeadOffice) {}

    @PostMapping
    public ResponseEntity<BranchDto> createBranch(
        @PathVariable String publicCompanyId,
        @RequestBody CreateBranchRequest request,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        @RequestHeader(value = "X-Actor-User-Id", required = false) String actorUserId,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        BranchDto created = branchApplicationService.createBranch(publicCompanyId, request.code(), request.name(), request.isHeadOffice(), actorUserId, correlationId);
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
        BranchDto branch = branchApplicationService.getBranchByPublicId(publicBranchId);
        return ResponseEntity
            .ok()
            .header("ETag", "\"" + branch.version() + "\"")
            .body(branch);
    }

    @PostMapping("/{publicBranchId}/activation")
    public ResponseEntity<BranchDto> activateBranch(
        @PathVariable String publicCompanyId,
        @PathVariable String publicBranchId,
        @RequestHeader(value = "X-Actor-User-Id", required = false) String actorUserId,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        BranchDto activated = branchApplicationService.activateBranch(publicBranchId, actorUserId, correlationId);
        return ResponseEntity
            .ok()
            .header("ETag", "\"" + activated.version() + "\"")
            .body(activated);
    }
}
