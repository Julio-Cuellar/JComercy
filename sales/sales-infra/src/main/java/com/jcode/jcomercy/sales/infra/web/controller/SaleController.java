package com.jcode.jcomercy.sales.infra.web.controller;

import com.jcode.jcomercy.sales.application.service.SaleQueryService;
import com.jcode.jcomercy.sales.infra.security.JwtAuthorizationService;
import com.jcode.jcomercy.sales.infra.web.dto.SaleResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Las ventas de una sucursal: se consultan con {@code sales.read}, solo en las sucursales que nombra el token. */
@RestController
@RequestMapping("/api/v1")
public class SaleController {

    private final SaleQueryService queries;
    private final JwtAuthorizationService authorization;

    public SaleController(SaleQueryService queries, JwtAuthorizationService authorization) {
        this.queries = queries;
        this.authorization = authorization;
    }

    @GetMapping("/companies/{companyId}/branches/{branchId}/sales")
    public ResponseEntity<List<SaleResponse>> list(@PathVariable String companyId, @PathVariable String branchId,
                                                   Authentication authentication) {
        if (!canRead(authentication, companyId, branchId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(queries.list(companyId, branchId).stream().map(SaleResponse::from).toList());
    }

    @GetMapping("/companies/{companyId}/branches/{branchId}/sales/{saleId}")
    public ResponseEntity<SaleResponse> get(@PathVariable String companyId, @PathVariable String branchId,
                                            @PathVariable UUID saleId, Authentication authentication) {
        if (!canRead(authentication, companyId, branchId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(SaleResponse.from(queries.get(companyId, branchId, saleId)));
    }

    private boolean canRead(Authentication authentication, String companyId, String branchId) {
        return authorization.canAccessCompany(authentication, companyId)
                && authorization.canAccessBranch(authentication, branchId)
                && authorization.hasPermission(authentication, "sales.read");
    }
}
