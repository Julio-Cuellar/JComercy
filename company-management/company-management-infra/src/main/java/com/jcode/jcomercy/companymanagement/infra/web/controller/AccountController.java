package com.jcode.jcomercy.companymanagement.infra.web.controller;

import com.jcode.jcomercy.companymanagement.application.dto.AccountDto;
import com.jcode.jcomercy.companymanagement.application.service.AccountApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountApplicationService accountApplicationService;

    public AccountController(AccountApplicationService accountApplicationService) {
        this.accountApplicationService = accountApplicationService;
    }

    public record RegisterAccountRequest(String name, String ownerUserId) {}
    public record SuspendAccountRequest(String reason) {}

    @PostMapping("/registration")
    public ResponseEntity<AccountDto> registerAccount(
        @RequestBody RegisterAccountRequest request,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        AccountDto created = accountApplicationService.registerAccount(request.name(), request.ownerUserId(), correlationId);
        return ResponseEntity
            .created(URI.create("/api/v1/accounts/" + created.publicAccountId()))
            .header("ETag", "\"" + created.version() + "\"")
            .body(created);
    }

    @GetMapping("/{publicAccountId}")
    public ResponseEntity<AccountDto> getAccount(@PathVariable String publicAccountId) {
        AccountDto account = accountApplicationService.getAccountByPublicId(publicAccountId);
        return ResponseEntity
            .ok()
            .header("ETag", "\"" + account.version() + "\"")
            .body(account);
    }

    @PostMapping("/{publicAccountId}/suspension")
    public ResponseEntity<AccountDto> suspendAccount(
        @PathVariable String publicAccountId,
        @RequestBody SuspendAccountRequest request,
        @RequestHeader(value = "X-Actor-User-Id", required = false) String actorUserId,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        AccountDto suspended = accountApplicationService.suspendAccount(publicAccountId, request.reason(), actorUserId, correlationId);
        return ResponseEntity
            .ok()
            .header("ETag", "\"" + suspended.version() + "\"")
            .body(suspended);
    }
}
