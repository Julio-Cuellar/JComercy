package com.jcode.jcomercy.inventory.infra.graphql;

import graphql.language.AstPrinter;
import graphql.parser.Parser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

/**
 * Las únicas operaciones GraphQL que el servicio ejecuta: las que viven como archivos del proyecto.
 * Un documento se reconoce por el hash de su forma normalizada, así que los espacios y comentarios no cuentan.
 */
public class PersistedOperationRegistry {

    private final Set<String> allowedHashes;

    public PersistedOperationRegistry(Collection<String> documents) {
        Set<String> hashes = new HashSet<>();
        for (String document : documents) {
            hashes.add(hashOf(document).orElseThrow(() ->
                new IllegalStateException("La operación GraphQL registrada no es válida: " + document)));
        }
        this.allowedHashes = Set.copyOf(hashes);
    }

    public boolean isAllowed(String document) {
        return hashOf(document).map(allowedHashes::contains).orElse(false);
    }

    /** Hash de la forma normalizada del documento, o vacío si no se puede leer como GraphQL. */
    public Optional<String> hashOf(String document) {
        if (document == null || document.isBlank()) {
            return Optional.empty();
        }
        try {
            String normalized = AstPrinter.printAstCompact(Parser.parse(document));
            return Optional.of(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8))));
        } catch (RuntimeException | NoSuchAlgorithmException exception) {
            return Optional.empty();
        }
    }

    public int size() {
        return allowedHashes.size();
    }
}
