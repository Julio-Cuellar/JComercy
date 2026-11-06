package com.jcode.jcomercy.companymanagement.infra.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los enlaces de la cola de company-management contra el AsyncAPI. La cola comparte exchange con inventario, así que no
 * puede recibirlo todo ({@code #}); pero tampoco debe dejar fuera un evento que el contrato declara. No necesita Docker:
 * lee los enlaces declarados, no un broker.
 */
@SpringBootTest(properties = "outbox.publisher.fixed-delay-ms=3600000")
class EventBindingsContractIntegrationTest {

    private static final String CONTRACT = "asyncapi-company-management-v1.yaml";
    private static final String CHANNEL_PREFIX = "events/";

    @Autowired
    @Qualifier("jcomercyEventsStagingBindings")
    private Declarables bindings;

    @Test
    @DisplayName("Todo evento que el AsyncAPI declara coincide con algún enlace de la cola de company-management")
    @SuppressWarnings("unchecked")
    void everyEventOfTheContractHasABinding() throws Exception {
        List<String> patterns = patterns();
        Map<String, Object> channels;
        try (InputStream in = new ClassPathResource(CONTRACT).getInputStream()) {
            Map<String, Object> contract = new Yaml().load(in);
            channels = (Map<String, Object>) contract.get("channels");
        }

        TreeSet<String> unbound = new TreeSet<>();
        int declared = 0;
        for (String channel : channels.keySet()) {
            if (!channel.startsWith(CHANNEL_PREFIX)) {
                continue;
            }
            declared++;
            // El canal «events/branch-addon/created» es el evento «branch-addon.created» (la clave de enrutamiento).
            String eventType = channel.substring(CHANNEL_PREFIX.length()).replace('/', '.');
            if (patterns.stream().noneMatch(pattern -> matches(pattern, eventType))) {
                unbound.add(eventType);
            }
        }
        assertThat(declared).as("canales de eventos que el contrato declara").isGreaterThan(40);
        assertThat(unbound).as("eventos del contrato sin enlace en la cola; sus prefijos van en outbox.rabbit.staging-bindings").isEmpty();
    }

    @Test
    @DisplayName("La cola de company-management no recibe todo lo del exchange ni los eventos de inventario")
    void theQueueDoesNotReceiveEverythingNorInventoryEvents() {
        List<String> patterns = patterns();

        assertThat(patterns).as("enlaces de la cola").isNotEmpty().doesNotContain("#", "*");
        for (String foreign : List.of("inventory.stock.issued", "inventory.warehouse.created", "inventory.reservation.created")) {
            assertThat(patterns.stream().anyMatch(pattern -> matches(pattern, foreign)))
                .as("la cola no debe recibir %s", foreign).isFalse();
        }
    }

    private List<String> patterns() {
        return bindings.getDeclarablesByType(Binding.class).stream().map(Binding::getRoutingKey).toList();
    }

    /** Solo los patrones que usa la cola: la clave exacta, o un prefijo con «.#» (cero o más palabras después). */
    private static boolean matches(String pattern, String routingKey) {
        if (pattern.endsWith(".#")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return routingKey.startsWith(prefix) || routingKey.equals(pattern.substring(0, pattern.length() - 2));
        }
        assertThat(pattern).as("patrón no soportado por esta prueba").doesNotContain("#", "*");
        return pattern.equals(routingKey);
    }
}
