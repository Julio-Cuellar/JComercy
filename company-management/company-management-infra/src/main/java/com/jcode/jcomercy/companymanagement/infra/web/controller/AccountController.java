package com.jcode.jcomercy.companymanagement.infra.web.controller;

import com.jcode.jcomercy.companymanagement.application.dto.AccountDto;
import com.jcode.jcomercy.companymanagement.application.service.AccountApplicationService;
import com.jcode.jcomercy.companymanagement.domain.exception.UnauthorizedAccessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountApplicationService accountApplicationService;

    public AccountController(AccountApplicationService accountApplicationService) {
        this.accountApplicationService = accountApplicationService;
    }

    public record RegisterAccountRequest(String name, @NotNull(message = "es obligatorio") OwnerRequest owner) {}
    public record OwnerRequest(String firstName, String lastName, LocalDate birthDate, String rfc,
                               String phoneNumber, String email, String state, String address, String password) {}
    public record SuspendAccountRequest(String reason) {}

    @PostMapping("/registration")
    public ResponseEntity<AccountDto> registerAccount(
        @Valid @RequestBody RegisterAccountRequest request,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        AccountDto created = accountApplicationService.registerAccount(
            request.name(), request.owner().firstName(), request.owner().lastName(), request.owner().birthDate(),
            request.owner().rfc(), request.owner().phoneNumber(), request.owner().email(), request.owner().state(),
            request.owner().address(), request.owner().password(), correlationId, idempotencyKey);
        log.info("Cuenta registrada accountId={} correlationId={}", created.publicAccountId(), correlationId);
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
        Principal principal,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        AccountDto suspended = accountApplicationService.suspendAccount(publicAccountId, request.reason(), actor(principal), correlationId, idempotencyKey);
        log.info("Cuenta suspendida accountId={} actor={} correlationId={}", publicAccountId, principal.getName(), correlationId);
        return ResponseEntity
            .ok()
            .header("ETag", "\"" + suspended.version() + "\"")
            .body(suspended);
    }

    private static String actor(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new UnauthorizedAccessException("Autenticación requerida");
        }
        return principal.getName();
    }
}
