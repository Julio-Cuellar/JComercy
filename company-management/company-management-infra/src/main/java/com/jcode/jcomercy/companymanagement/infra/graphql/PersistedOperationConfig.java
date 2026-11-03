package com.jcode.jcomercy.companymanagement.infra.graphql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class PersistedOperationConfig {
    private static final Logger log = LoggerFactory.getLogger(PersistedOperationConfig.class);

    @Bean
    PersistedOperationRegistry persistedOperationRegistry(
            ResourcePatternResolver resolver,
            @Value("${graphql.security.operations-location:classpath:graphql/operations/*.graphql}") String location)
            throws IOException {
        List<String> documents = new ArrayList<>();
        for (Resource resource : resolver.getResources(location)) {
            documents.add(resource.getContentAsString(StandardCharsets.UTF_8));
        }
        var registry = new PersistedOperationRegistry(documents);
        log.info("Operaciones GraphQL registradas cantidad={}", registry.size());
        return registry;
    }
}
