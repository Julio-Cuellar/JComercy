package com.jcode.jcomercy.companymanagement.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Códigos de un solo uso, como el del enlace de una invitación: 32 bytes aleatorios, imposibles de adivinar. En la base
 * de datos solo se guarda su huella (SHA-256), así que quien lea la tabla no puede usar ningún enlace.
 */
final class OneTimeCode {
    private static final SecureRandom RANDOM = new SecureRandom();

    private OneTimeCode() {
    }

    static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible en esta máquina virtual", e);
        }
    }
}
