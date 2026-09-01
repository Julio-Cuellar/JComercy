package com.jcode.jcomercy.inventory.infra.web.exception;

import com.jcode.jcomercy.inventory.application.exception.InventoryApplicationException;
import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class InventoryExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(InventoryExceptionHandler.class);

    @ExceptionHandler(InventoryApplicationException.class)
    public ResponseEntity<Map<String, String>> handleApplication(InventoryApplicationException exception) {
        log.warn("Operación de inventario rechazada: {}", exception.getMessage());
        var status = exception.getMessage().contains("not found") ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(Map.of("code", "INVENTORY_OPERATION_REJECTED", "message", exception.getMessage()));
    }

    @ExceptionHandler(InventoryDomainException.class)
    public ResponseEntity<Map<String, String>> handleDomain(InventoryDomainException exception) {
        log.warn("Regla de dominio de inventario incumplida: {}", exception.getMessage());
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("code", "INVENTORY_DOMAIN_RULE_VIOLATION", "message", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception exception) {
        log.error("Error inesperado en inventory", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", "INTERNAL_SERVER_ERROR", "message", "Error interno del servidor"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
        var details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        log.warn("Petición de almacenes inválida campos={}", details);
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "message", String.join(", ", details)));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> handleMalformedRequest(Exception exception) {
        log.warn("Petición de almacenes mal formada tipo={} reason={}", exception.getClass().getSimpleName(),
                exception.getMessage());
        return ResponseEntity.badRequest().body(Map.of("code", "MALFORMED_REQUEST", "message", "Solicitud inválida"));
    }
}
