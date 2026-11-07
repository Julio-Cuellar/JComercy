package com.jcode.jcomercy.sales.infra.web.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/** Traduce los errores de Ventas a respuestas con un código y un mensaje que dice qué hacer, nunca a un 500 sin explicar. */
@RestControllerAdvice
public class SalesExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(SalesExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleUnknownRoute(NoResourceFoundException exception) {
        log.warn("Ruta de ventas inexistente: {}", exception.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND",
                "message", "La dirección solicitada no existe. Revisa que esté bien escrita."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleUnsupportedMethod(HttpRequestMethodNotSupportedException exception) {
        log.warn("Método no permitido en ventas: {}", exception.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(exception.getHeaders())
                .body(Map.of("code", "METHOD_NOT_ALLOWED",
                        "message", "Esa dirección no admite este método. Revisa la documentación del contrato."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
        var details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        log.warn("Petición de ventas inválida campos={}", details);
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "message", String.join(", ", details)));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> handleMalformedRequest(Exception exception) {
        log.warn("Petición de ventas mal formada tipo={} reason={}", exception.getClass().getSimpleName(),
                exception.getMessage());
        return ResponseEntity.badRequest().body(Map.of("code", "MALFORMED_REQUEST", "message", "Solicitud inválida"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception exception) {
        log.error("Error inesperado en ventas", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", "INTERNAL_SERVER_ERROR", "message", "Error interno del servidor"));
    }
}
