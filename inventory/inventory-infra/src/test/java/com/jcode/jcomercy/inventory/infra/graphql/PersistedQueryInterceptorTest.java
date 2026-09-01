package com.jcode.jcomercy.inventory.infra.graphql;

import graphql.ExecutionInput;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PersistedQueryInterceptorTest {
    private static final String DOCUMENT = "query { locations(warehouseId: \"00000000-0000-0000-0000-000000000001\") { publicLocationId } }";

    @Test
    void allowsOperationInLocalOpenMode() {
        var interceptor = new PersistedQueryInterceptor(false, "");
        var chain = mock(WebGraphQlInterceptor.Chain.class);
        var response = mock(WebGraphQlResponse.class);
        when(chain.next(any())).thenReturn(Mono.just(response));

        assertThat(interceptor.intercept(request(null), chain).block()).isSameAs(response);
        verify(chain).next(any());
    }

    @Test
    void rejectsOperationWithoutHashInStrictMode() {
        var interceptor = new PersistedQueryInterceptor(true, "");
        var chain = mock(WebGraphQlInterceptor.Chain.class);

        var response = interceptor.intercept(request(null), chain).block();

        assertThat(response.getErrors()).isNotEmpty();
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsHashOutsideAllowlist() {
        var interceptor = new PersistedQueryInterceptor(false, PersistedQueryInterceptor.sha256("other"));
        var chain = mock(WebGraphQlInterceptor.Chain.class);

        var response = interceptor.intercept(request(PersistedQueryInterceptor.sha256(DOCUMENT)), chain).block();

        assertThat(response.getErrors()).isNotEmpty();
        verifyNoInteractions(chain);
    }

    private static WebGraphQlRequest request(String hash) {
        var request = mock(WebGraphQlRequest.class);
        var headers = new org.springframework.http.HttpHeaders();
        if (hash != null) headers.set(PersistedQueryInterceptor.HASH_HEADER, hash);
        when(request.getDocument()).thenReturn(DOCUMENT);
        when(request.getHeaders()).thenReturn(headers);
        when(request.toExecutionInput()).thenReturn(ExecutionInput.newExecutionInput().query(DOCUMENT).build());
        return request;
    }
}
