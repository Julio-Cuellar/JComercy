package com.jcode.jcomercy.companymanagement.infra.web.controller;

import com.jcode.jcomercy.companymanagement.infra.security.CompanyAccessService;
import com.jcode.jcomercy.companymanagement.infra.security.CompanyAccessService.CompanyAccess;
import com.jcode.jcomercy.companymanagement.infra.security.CompanyAccessService.CompanySession;
import com.jcode.jcomercy.companymanagement.infra.security.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * La pantalla «Tus empresas»: las empresas a las que la cuenta puede entrar y la entrada a una de ellas, que entrega
 * el token de esa empresa.
 *
 * <p>La variable de la empresa no se llama {@code publicCompanyId} a propósito: así los interceptores tratan estas
 * rutas como rutas de cuenta, que es lo que son. Con ese nombre, el interceptor de permisos las trataría como rutas de
 * empresa y un miembro no podría entrar a su propia empresa.
 */
@RestController
@RequestMapping("/api/v1/accounts/{publicAccountId}/company-accesses")
public class CompanyAccessController {

    private final CompanyAccessService service;

    public CompanyAccessController(CompanyAccessService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission(value = "accounts.company_accesses.read", action = "consultar tus empresas")
    public ResponseEntity<List<CompanyAccess>> listCompanyAccesses(@PathVariable String publicAccountId) {
        return ResponseEntity.ok(service.list(publicAccountId));
    }

    @PostMapping("/{targetCompanyId}/session")
    @RequiresPermission(value = "accounts.company_accesses.enter", action = "entrar a una empresa")
    public ResponseEntity<CompanySession> enterCompany(@PathVariable String publicAccountId,
                                                       @PathVariable String targetCompanyId) {
        return ResponseEntity.ok(service.enter(publicAccountId, targetCompanyId));
    }
}
