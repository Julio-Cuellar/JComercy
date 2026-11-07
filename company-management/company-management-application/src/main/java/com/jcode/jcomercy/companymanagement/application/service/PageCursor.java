package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.domain.exception.InvalidRequestException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Dónde quedó la página anterior de una lista ordenada por fecha de alta: la fecha y el identificador del último elemento
 * que se vio. Sale como texto opaco (el {@code nextCursor} de la página) y vuelve en {@code after}.
 */
record PageCursor(Instant createdAt, String id) {

    String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((createdAt + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    /** Un {@code after} vacío es la primera página (nulo); uno que no se puede leer es una petición mal formada. */
    static PageCursor decode(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8).split("\\|", 2);
            return new PageCursor(Instant.parse(parts[0]), parts[1]);
        } catch (RuntimeException e) {
            throw new InvalidRequestException("El cursor 'after' no es válido. Usa el nextCursor de la página anterior.");
        }
    }

    /** Si un elemento viene después de donde quedó la página anterior. */
    boolean isBefore(Instant elementCreatedAt, String elementId) {
        int byDate = elementCreatedAt.compareTo(createdAt);
        return byDate > 0 || (byDate == 0 && elementId.compareTo(id) > 0);
    }
}
