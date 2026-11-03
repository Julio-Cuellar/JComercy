package com.jcode.jcomercy.companymanagement.infra.web.controller;

import com.jcode.jcomercy.companymanagement.infra.security.CompanyAccessService;
import com.jcode.jcomercy.companymanagement.infra.security.CompanyAccessService.CompanyAccess;
import com.jcode.jcomercy.companymanagement.infra.security.CompanyAccessService.CompanySession;
import com.jcode.jcomercy.companymanagement.infra.security.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * La pantalla «Tus empresas»: las empresas a las que la cuenta puede entrar, la entrada a una de ellas, que entrega
 * el token de esa empresa, y la contraseña opcional que cada persona le puede poner a su acceso.
 *
 * <p>La variable de la empresa no se llama {@code publicCompanyId} a propósito: así los interceptores tratan estas
 * rutas como rutas de cuenta, que es lo que son. Con ese nombre, el interceptor de permisos las trataría como rutas de
 * empresa y un miembro no podría entrar a su propia empresa.
 */
@RestController
@RequestMapping("/api/v1/accounts/{publicAccountId}/company-accesses")
public class CompanyAccessController {

    /** La contraseña de la empresa, solo si el acceso tiene una. */
    public record EnterCompanyRequest(String password) { }

    /** {@code currentPassword} solo se pide al cambiar una contraseña que ya existe. */
    public record SetCompanyPasswordRequest(String currentPassword, String newPassword) { }

    /** La contraseña de la cuenta: es la salida si se olvidó la de la empresa. */
    public record RemoveCompanyPasswordRequest(String accountPassword) { }

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
                                                       @PathVariable String targetCompanyId,
                                                       @RequestBody(required = false) EnterCompanyRequest request) {
        return ResponseEntity.ok(service.enter(publicAccountId, targetCompanyId, request == null ? null : request.password()));
    }

    @PutMapping("/{targetCompanyId}/password")
    @RequiresPermission(value = "accounts.company_accesses.password.set", action = "poner o cambiar la contraseña de una empresa")
    public ResponseEntity<Void> setCompanyPassword(@PathVariable String publicAccountId,
                                                   @PathVariable String targetCompanyId,
                                                   @RequestBody SetCompanyPasswordRequest request) {
        service.setPassword(publicAccountId, targetCompanyId, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{targetCompanyId}/password")
    @RequiresPermission(value = "accounts.company_accesses.password.remove", action = "quitar la contraseña de una empresa")
    public ResponseEntity<Void> removeCompanyPassword(@PathVariable String publicAccountId,
                                                      @PathVariable String targetCompanyId,
                                                      @RequestBody(required = false) RemoveCompanyPasswordRequest request) {
        service.removePassword(publicAccountId, targetCompanyId, request == null ? null : request.accountPassword());
        return ResponseEntity.noContent().build();
    }
}
