package com.jcode.jcomercy.inventory.infra.graphql;

import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Deja pasar solo las operaciones registradas como archivos del proyecto. Con `graphql.security.persisted-only=false`
 * (desarrollo local) deja pasar cualquiera.
 */
@Component
public class PersistedQueryInterceptor implements WebGraphQlInterceptor {
    private static final Logger log = LoggerFactory.getLogger(PersistedQueryInterceptor.class);

    private final boolean persistedOnly;
    private final PersistedOperationRegistry registry;

    public PersistedQueryInterceptor(@Value("${graphql.security.persisted-only:true}") boolean persistedOnly,
                                     PersistedOperationRegistry registry) {
        this.persistedOnly = persistedOnly;
        this.registry = registry;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        if (!persistedOnly || registry.isAllowed(request.getDocument())) {
            return chain.next(request);
        }
        // Se registra el hash y no el documento: así se puede rastrear la consulta sin guardar lo que pidió el cliente.
        log.warn("Operación GraphQL rechazada hash={}", registry.hashOf(request.getDocument()).orElse("ilegible"));
        return Mono.just(rejected(request));
    }

    private static WebGraphQlResponse rejected(WebGraphQlRequest request) {
        GraphQLError error = GraphqlErrorBuilder.newError()
            .message("Esta consulta no está permitida. Actualiza la aplicación e intenta de nuevo.")
            .extensions(Map.of("code", "OPERATION_NOT_ALLOWED"))
            .build();
        var result = ExecutionResultImpl.newExecutionResult().data(null).errors(List.of(error)).build();
        return new WebGraphQlResponse(new DefaultExecutionGraphQlResponse(request.toExecutionInput(), result));
    }
}
