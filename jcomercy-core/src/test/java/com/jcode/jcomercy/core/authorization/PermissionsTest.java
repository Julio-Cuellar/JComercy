package com.jcode.jcomercy.core.authorization;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionsTest {

    @Test
    void anExactPermissionGrantsItself() {
        assertTrue(Permissions.matches("branches.read", "branches.read"));
    }

    @Test
    void anExactPermissionDoesNotGrantAnotherOne() {
        assertFalse(Permissions.matches("branches.read", "branches.create"));
        assertFalse(Permissions.matches("branches.read", "branches.read.all"));
    }

    @Test
    void theStarGrantsEveryPermission() {
        assertTrue(Permissions.matches("*", "inventory.stock.write"));
        assertTrue(Permissions.matches("*", "accounts.suspend"));
    }

    @Test
    void aFamilyWildcardGrantsEverythingUnderThatFamily() {
        assertTrue(Permissions.matches("branches.*", "branches.create"));
        assertTrue(Permissions.matches("inventory.*", "inventory.stock.write"));
        assertTrue(Permissions.matches("inventory.stock.*", "inventory.stock.negative.enable"));
    }

    @Test
    void aFamilyWildcardDoesNotGrantOtherFamilies() {
        assertFalse(Permissions.matches("companies.*", "accounts.read"));
        assertFalse(Permissions.matches("inventory.*", "inventoryx.read"));
        assertFalse(Permissions.matches("inventory.stock.*", "inventory.products.manage"));
    }

    @Test
    void aFamilyWildcardDoesNotGrantTheBareFamilyName() {
        assertFalse(Permissions.matches("companies.*", "companies"));
    }

    @Test
    void aWildcardOnlyCountsAtTheEnd() {
        assertFalse(Permissions.matches("inventory.*.read", "inventory.stock.read"));
        assertFalse(Permissions.matches("*.read", "branches.read"));
    }

    @Test
    void blankOrMissingValuesGrantNothing() {
        assertFalse(Permissions.matches(null, "branches.read"));
        assertFalse(Permissions.matches("", "branches.read"));
        assertFalse(Permissions.matches("   ", "branches.read"));
        assertFalse(Permissions.matches("*", null));
        assertFalse(Permissions.matches("*", ""));
    }

    @Test
    void aSetGrantsWhenAnyOfItsPermissionsDoes() {
        List<String> granted = List.of("branches.read", "inventory.stock.*");

        assertTrue(Permissions.grants(granted, "branches.read"));
        assertTrue(Permissions.grants(granted, "inventory.stock.read"));
        assertFalse(Permissions.grants(granted, "branches.create"));
        assertFalse(Permissions.grants(granted, "inventory.products.manage"));
    }

    @Test
    void aMissingOrEmptySetGrantsNothing() {
        assertFalse(Permissions.grants(null, "branches.read"));
        assertFalse(Permissions.grants(List.of(), "branches.read"));
        assertFalse(Permissions.grants(Arrays.asList(null, "", " "), "branches.read"));
    }
}
