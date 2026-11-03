package com.jcode.jcomercy.companymanagement.infra.graphql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistedOperationRegistryTest {

    private static final String REGISTERED = "query Companies($id: ID!) { companiesByAccount(publicAccountId: $id) { publicCompanyId legalName } }";

    @Test
    void recognizesARegisteredOperationRegardlessOfSpacingAndComments() {
        var registry = new PersistedOperationRegistry(List.of(REGISTERED));

        assertThat(registry.isAllowed(REGISTERED)).isTrue();
        assertThat(registry.isAllowed("query   Companies( $id : ID! ){ companiesByAccount( publicAccountId:$id ){publicCompanyId   legalName} }")).isTrue();
        assertThat(registry.isAllowed("# comentario\nquery Companies($id: ID!) {\n  companiesByAccount(publicAccountId: $id) {\n    publicCompanyId\n    legalName\n  }\n}")).isTrue();
    }

    @Test
    void rejectsAnOperationThatIsNotRegistered() {
        var registry = new PersistedOperationRegistry(List.of(REGISTERED));

        assertThat(registry.isAllowed("query Companies($id: ID!) { companiesByAccount(publicAccountId: $id) { publicCompanyId legalName rfc } }")).isFalse();
        assertThat(registry.isAllowed("query Other($id: ID!) { companiesByAccount(publicAccountId: $id) { publicCompanyId legalName } }")).isFalse();
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
