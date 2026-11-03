package com.jcode.jcomercy.inventory.infra.graphql;

import graphql.ExecutionInput;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PersistedQueryInterceptorTest {

    private static final String REGISTERED = "query Locations { locations(warehouseId: \"1\") { publicLocationId } }";
    private static final String UNREGISTERED = "query { locations(warehouseId: \"1\") { publicLocationId code } }";

    @Test
    void allowsARegisteredOperationInClosedMode() {
        var interceptor = new PersistedQueryInterceptor(true, new PersistedOperationRegistry(List.of(REGISTERED)));
        var response = mock(WebGraphQlResponse.class);
        var chain = chainReturning(response);

        assertThat(interceptor.intercept(request(REGISTERED, null), chain).block()).isSameAs(response);
        verify(chain).next(any());
    }

    @Test
    void rejectsAnUnlistedOperationEvenWithItsOwnValidHash() {
        var interceptor = new PersistedQueryInterceptor(true, new PersistedOperationRegistry(List.of(REGISTERED)));
        var chain = chainReturning(mock(WebGraphQlResponse.class));

        var response = interceptor.intercept(request(UNREGISTERED, sha256(UNREGISTERED)), chain).block();

        assertThat(response.getErrors()).isNotEmpty();
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsEverythingWhenNoOperationIsRegistered() {
        var interceptor = new PersistedQueryInterceptor(true, new PersistedOperationRegistry(List.of()));
        var chain = chainReturning(mock(WebGraphQlResponse.class));

        var response = interceptor.intercept(request(REGISTERED, null), chain).block();

        assertThat(response.getErrors()).isNotEmpty();
        verifyNoInteractions(chain);
    }

    @Test
    void allowsAnyOperationInExplicitOpenMode() {
        var interceptor = new PersistedQueryInterceptor(false, new PersistedOperationRegistry(List.of()));
        var response = mock(WebGraphQlResponse.class);
        var chain = chainReturning(response);

        assertThat(interceptor.intercept(request(UNREGISTERED, null), chain).block()).isSameAs(response);
        verify(chain).next(any());
    }

    @Test
    void explainsTheRejectionInBusinessLanguageWithAStableCode() {
        var interceptor = new PersistedQueryInterceptor(true, new PersistedOperationRegistry(List.of(REGISTERED)));

        var response = interceptor.intercept(request(UNREGISTERED, null), chainReturning(mock(WebGraphQlResponse.class))).block();

        var error = response.getErrors().get(0);
        assertThat(error.getMessage()).isEqualTo("Esta consulta no está permitida. Actualiza la aplicación e intenta de nuevo.");
        assertThat(error.getExtensions()).containsEntry("code", "OPERATION_NOT_ALLOWED");
    }

    private static WebGraphQlInterceptor.Chain chainReturning(WebGraphQlResponse response) {
        var chain = mock(WebGraphQlInterceptor.Chain.class);
        when(chain.next(any())).thenReturn(Mono.just(response));
        return chain;
    }

    private static WebGraphQlRequest request(String document, String hash) {
        var request = mock(WebGraphQlRequest.class);
        var headers = new HttpHeaders();
        if (hash != null) {
            headers.set("X-GraphQL-Operation-Hash", hash);
        }
        when(request.getDocument()).thenReturn(document);
        when(request.getHeaders()).thenReturn(headers);
        when(request.toExecutionInput()).thenReturn(ExecutionInput.newExecutionInput().query(document).build());
        return request;
    }

    private static String sha256(String document) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(document.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
