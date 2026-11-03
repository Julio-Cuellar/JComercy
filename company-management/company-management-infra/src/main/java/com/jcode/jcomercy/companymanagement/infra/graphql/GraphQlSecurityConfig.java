package com.jcode.jcomercy.companymanagement.infra.graphql;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class GraphQlSecurityConfig {

    @Bean
    GraphQlSourceBuilderCustomizer graphQlLimits(
            @Value("${graphql.security.max-depth:8}") int maxDepth,
            @Value("${graphql.security.max-complexity:100}") int maxComplexity) {
        return builder -> builder.instrumentation(List.of(
                new MaxQueryDepthInstrumentation(maxDepth),
                new MaxQueryComplexityInstrumentation(maxComplexity)));
    }
}
