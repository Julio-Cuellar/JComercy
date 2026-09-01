package com.jcode.jcomercy.inventory.infra.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.ExecutionResultImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PersistedQueryInterceptor implements WebGraphQlInterceptor {
    public static final String HASH_HEADER = "X-GraphQL-Operation-Hash";
    private final boolean persistedOnly;
    private final Set<String> allowedHashes;

    public PersistedQueryInterceptor(
            @Value("${graphql.security.persisted-only:false}") boolean persistedOnly,
            @Value("${graphql.security.allowed-operation-hashes:}") String configuredHashes) {
        this.persistedOnly = persistedOnly;
        this.allowedHashes = Arrays.stream(configuredHashes.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        String expectedHash = sha256(request.getDocument());
        String suppliedHash = request.getHeaders().getFirst(HASH_HEADER);
        boolean valid = suppliedHash != null && MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII), suppliedHash.trim().getBytes(StandardCharsets.US_ASCII));
        if ((persistedOnly || !allowedHashes.isEmpty())
                && (!valid || !allowedHashes.isEmpty() && !allowedHashes.contains(expectedHash))) {
            return Mono.just(rejected(request, "GraphQL operation is not persisted or is not allowed"));
        }
        return chain.next(request);
    }

    static String sha256(String document) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(document.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash GraphQL operation", exception);
        }
    }

    private static WebGraphQlResponse rejected(WebGraphQlRequest request, String message) {
        GraphQLError error = GraphqlErrorBuilder.newError().message(message).build();
        var result = ExecutionResultImpl.newExecutionResult().data(null).errors(List.of(error)).build();
        return new WebGraphQlResponse(new DefaultExecutionGraphQlResponse(request.toExecutionInput(), result));
    }
}
