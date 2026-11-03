package com.jcode.jcomercy.core.authorization;

import java.util.Collection;

/**
 * Decide si un permiso concedido cubre el permiso que pide una operación. Los roles del sistema conceden familias
 * enteras ("branches.*") y el dueño de la cuenta lo concede todo ("*"), así que comparar texto exacto no basta.
 * El comodín solo cuenta al final: "inventory.*.read" no es un comodín, es un permiso que no existe.
 */
public final class Permissions {

    /** Concede cualquier permiso. */
    public static final String ALL = "*";

    private static final String FAMILY_SUFFIX = ".*";

    private Permissions() {
    }

    public static boolean matches(String granted, String required) {
        if (isBlank(granted) || isBlank(required)) {
            return false;
        }
        if (ALL.equals(granted) || granted.equals(required)) {
            return true;
        }
        if (granted.endsWith(FAMILY_SUFFIX)) {
            String family = granted.substring(0, granted.length() - 1);
            return !family.contains("*") && required.startsWith(family) && required.length() > family.length();
        }
        return false;
    }

    public static boolean grants(Collection<String> granted, String required) {
        return granted != null && granted.stream().anyMatch(permission -> matches(permission, required));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
