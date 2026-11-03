package com.jcode.jcomercy.companymanagement.infra.graphql;

import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.graphql.execution.GraphQlSource;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Las operaciones permitidas y el esquema deben decir lo mismo: ninguna operación inválida y ninguna consulta sin operación. */
@SpringBootTest
class GraphQlOperationsConsistencyTest {

    @Autowired
    private GraphQlSource graphQlSource;

    @Autowired
    private ResourcePatternResolver resolver;

    @Value("${graphql.security.operations-location:classpath:graphql/operations/*.graphql}")
    private String location;

    @Test
    @DisplayName("Cada operación registrada es válida contra el esquema")
    void everyRegisteredOperationIsValidAgainstTheSchema() throws Exception {
        GraphQLSchema schema = graphQlSource.schema();
        Resource[] resources = resolver.getResources(location);

        assertThat(resources).as("operaciones registradas").isNotEmpty();
        for (Resource resource : resources) {
            Document document = Parser.parse(resource.getContentAsString(StandardCharsets.UTF_8));
            var errors = new Validator().validateDocument(schema, document, Locale.ENGLISH);
            assertThat(errors).as("errores de %s", resource.getFilename()).isEmpty();
        }
    }

    @Test
    @DisplayName("Cada consulta del esquema tiene al menos una operación registrada")
    void everyQueryInTheSchemaHasARegisteredOperation() throws Exception {
        Set<String> covered = new TreeSet<>();
        for (Resource resource : resolver.getResources(location)) {
            Document document = Parser.parse(resource.getContentAsString(StandardCharsets.UTF_8));
            for (OperationDefinition operation : document.getDefinitionsOfType(OperationDefinition.class)) {
                operation.getSelectionSet().getSelections().stream()
                    .filter(Field.class::isInstance)
                    .map(selection -> ((Field) selection).getName())
                    .forEach(covered::add);
            }
        }
        Set<String> queries = new TreeSet<>();
        for (GraphQLFieldDefinition field : graphQlSource.schema().getQueryType().getFieldDefinitions()) {
            queries.add(field.getName());
        }

        queries.removeAll(covered);

        assertThat(queries).as("consultas del esquema sin operación registrada").isEmpty();
    }
}
