package com.jcode.jcomercy.companymanagement.infra.web.controller;

import com.jcode.jcomercy.companymanagement.application.dto.PageDto;
import com.jcode.jcomercy.companymanagement.application.dto.RoleViewDto;
import com.jcode.jcomercy.companymanagement.application.service.RoleApplicationService;
import com.jcode.jcomercy.companymanagement.infra.security.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{publicCompanyId}/roles")
public class RoleController {

    private final RoleApplicationService roles;

    public RoleController(RoleApplicationService roles) {
        this.roles = roles;
    }

    @GetMapping
    @RequiresPermission(value = "roles.read", action = "consultar los roles")
    public ResponseEntity<PageDto<RoleViewDto>> list(
        @PathVariable String publicCompanyId,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String after
    ) {
        return ResponseEntity.ok(roles.list(publicCompanyId, type, status, limit, after));
    }

    @GetMapping("/{roleId}")
    @RequiresPermission(value = "roles.read", action = "consultar los roles")
    public ResponseEntity<RoleViewDto> get(@PathVariable String publicCompanyId, @PathVariable String roleId) {
        RoleViewDto role = roles.get(publicCompanyId, roleId);
        return ResponseEntity.ok().header("ETag", "\"" + role.version() + "\"").body(role);
    }
}
