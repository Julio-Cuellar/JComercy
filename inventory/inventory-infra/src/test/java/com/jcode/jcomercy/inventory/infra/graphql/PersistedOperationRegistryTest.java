package com.jcode.jcomercy.inventory.infra.graphql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistedOperationRegistryTest {

    private static final String REGISTERED = "query Units { unitsOfMeasure { code name } }";

    @Test
    void recognizesARegisteredOperationRegardlessOfSpacingAndComments() {
        var registry = new PersistedOperationRegistry(List.of(REGISTERED));

        assertThat(registry.isAllowed(REGISTERED)).isTrue();
        assertThat(registry.isAllowed("query   Units{ unitsOfMeasure{code   name} }")).isTrue();
        assertThat(registry.isAllowed("# comentario\nquery Units {\n  unitsOfMeasure {\n    code\n    name\n  }\n}")).isTrue();
    }

    @Test
    void rejectsAnOperationThatIsNotRegistered() {
        var registry = new PersistedOperationRegistry(List.of(REGISTERED));

        assertThat(registry.isAllowed("query Units { unitsOfMeasure { code name symbol } }")).isFalse();
        assertThat(registry.isAllowed("query Other { unitsOfMeasure { code name } }")).isFalse();
        assertThat(registry.isAllowed("{ __schema { types { name } } }")).isFalse();
    }

    @Test
    void rejectsADocumentThatDoesNotParseInsteadOfFailing() {
        var registry = new PersistedOperationRegistry(List.of(REGISTERED));

        assertThat(registry.isAllowed("query {")).isFalse();
        assertThat(registry.isAllowed("")).isFalse();
        assertThat(registry.isAllowed(null)).isFalse();
    }

    @Test
    void rejectsEverythingWhenNoOperationIsRegistered() {
        var registry = new PersistedOperationRegistry(List.of());

        assertThat(registry.isAllowed(REGISTERED)).isFalse();
        assertThat(registry.size()).isZero();
    }

    @Test
    void refusesToStartWithARegisteredDocumentThatDoesNotParse() {
        assertThatThrownBy(() -> new PersistedOperationRegistry(List.of(REGISTERED, "query {")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("query {");
    }
}
