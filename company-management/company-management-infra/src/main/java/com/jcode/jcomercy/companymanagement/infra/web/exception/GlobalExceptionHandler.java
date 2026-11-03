package com.jcode.jcomercy.companymanagement.infra.web.exception;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;
import com.jcode.jcomercy.companymanagement.domain.exception.DuplicateResourceException;
import com.jcode.jcomercy.companymanagement.domain.exception.EntityNotFoundException;
import com.jcode.jcomercy.companymanagement.domain.exception.UnauthorizedAccessException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ProblemDetails> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.of(
            "https://jcomercy.com/errors/not-found",
            "Recurso No Encontrado",
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage(),
            request.getRequestURI(),
            "RESOURCE_NOT_FOUND"
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ProblemDetails> handleConflict(DuplicateResourceException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.of(
            "https://jcomercy.com/errors/conflict",
            "Conflicto de Recurso",
            HttpStatus.CONFLICT.value(),
            ex.getMessage(),
            request.getRequestURI(),
            "RESOURCE_ALREADY_EXISTS"
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(DomainRuleViolationException.class)
    public ResponseEntity<ProblemDetails> handleUnprocessable(DomainRuleViolationException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.of(
            "https://jcomercy.com/errors/unprocessable-entity",
            "Regla de Negocio Violada",
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            ex.getMessage(),
            request.getRequestURI(),
            "DOMAIN_RULE_VIOLATION"
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ProblemDetails> handleUnauthorized(UnauthorizedAccessException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.of(
            "https://jcomercy.com/errors/forbidden",
            "Acceso Denegado",
            HttpStatus.FORBIDDEN.value(),
            ex.getMessage(),
            request.getRequestURI(),
            "INSUFFICIENT_PERMISSIONS"
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetails> handleUnknownRoute(NoResourceFoundException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.of(
            "https://jcomercy.com/errors/route-not-found",
            "Dirección No Encontrada",
            HttpStatus.NOT_FOUND.value(),
            "La dirección solicitada no existe. Revisa que esté bien escrita.",
            request.getRequestURI(),
            "NOT_FOUND"
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetails> handleUnsupportedMethod(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.of(
            "https://jcomercy.com/errors/method-not-allowed",
            "Método No Permitido",
            HttpStatus.METHOD_NOT_ALLOWED.value(),
            "Esa dirección no admite este método. Revisa la documentación del contrato.",
            request.getRequestURI(),
            "METHOD_NOT_ALLOWED"
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(ex.getHeaders()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetails> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Error inesperado en company-management uri={}", request.getRequestURI(), ex);
        ProblemDetails problem = ProblemDetails.of(
            "https://jcomercy.com/errors/internal-server-error",
            "Error Interno del Servidor",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "No pudimos completar la operación por un problema nuestro. Intenta de nuevo en unos minutos.",
            request.getRequestURI(),
            "INTERNAL_SERVER_ERROR"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
