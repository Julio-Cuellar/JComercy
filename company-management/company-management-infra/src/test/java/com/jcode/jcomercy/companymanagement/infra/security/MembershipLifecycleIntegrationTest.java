package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M7 del frente de roles: suspender, reactivar y revocar el acceso de una persona a la empresa. La suspensión se puede
 * deshacer; la revocación es definitiva. El efecto es inmediato en company-management: cada petición revisa la
 * membresía en la base de datos, y un token que ya se había entregado deja de servir en la siguiente.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MembershipLifecycleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    private RestWorld world;
    private String company;
    private String ownerToken;
    private RestWorld.Person ana;
    private String anaAccountToken;
    private String anaCompanyToken;
    private String membership;

    @BeforeEach
    void company() throws Exception {
        world = new RestWorld(mockMvc, objectMapper, jdbc);
        RestWorld.Person owner = world.register();
        String account = world.login(owner.email());
        company = world.createCompany(owner.accountId(), account);
        ownerToken = world.enter(owner.accountId(), company, account);
        String branch = world.createBranch(company, ownerToken, "MATRIZ");
        ana = world.register();
        world.addMember(company, ana.accountId(), "ACTIVE", "[\"STAFF\"]");
        world.assignBranch(company, ana.accountId(), branch);
        anaAccountToken = world.login(ana.email());
        anaCompanyToken = world.enter(ana.accountId(), company, anaAccountToken);
        membership = membershipOf(ana);
    }

    @Test
    @DisplayName("Suspender corta el acceso en la siguiente petición: el token ya entregado y volver a entrar dejan de servir")
    void suspendingCutsTheAccessRightAway() throws Exception {
        selfPermissions(anaCompanyToken).andExpect(status().isOk());

        action("suspension", ownerToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(membership))
            .andExpect(jsonPath("$.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.changedAt").exists());

        selfPermissions(anaCompanyToken).andExpect(status().isForbidden());
        enter(ana, anaAccountToken).andExpect(status().isForbidden());
        mockMvc.perform(request(HttpMethod.GET, "/api/v1/accounts/" + ana.accountId() + "/company-accesses").header("Authorization", anaAccountToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(request(HttpMethod.GET, "/api/v1/companies/" + company + "/memberships?status=SUSPENDED").header("Authorization", ownerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].userId").value(ana.accountId()));
    }

    @Test
    @DisplayName("Reactivar devuelve el acceso: el mismo token vuelve a servir y se puede volver a entrar")
    void reactivatingGivesTheAccessBack() throws Exception {
        action("suspension", ownerToken).andExpect(status().isOk());

        action("reactivation", ownerToken).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));

        selfPermissions(anaCompanyToken).andExpect(status().isOk());
        enter(ana, anaAccountToken).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Revocar es definitivo, desde activa o desde suspendida: ya no se puede suspender, reactivar ni revocar otra vez")
    void revokingIsDefinitive() throws Exception {
        action("revocation", ownerToken).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"));

        selfPermissions(anaCompanyToken).andExpect(status().isForbidden());
        action("reactivation", ownerToken).andExpect(status().isConflict());
        action("suspension", ownerToken).andExpect(status().isConflict());
        action("revocation", ownerToken).andExpect(status().isConflict());

        RestWorld.Person beto = world.register();
        world.addMember(company, beto.accountId(), "SUSPENDED", "[\"STAFF\"]");
        action("revocation", ownerToken, membershipOf(beto)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"));
    }

    @Test
    @DisplayName("Suspender a quien ya está suspendido, o reactivar a quien está activo, da conflicto")
    void repeatedOrImpossibleTransitionsAreConflicts() throws Exception {
        action("reactivation", ownerToken).andExpect(status().isConflict());
        action("suspension", ownerToken).andExpect(status().isOk());
        action("suspension", ownerToken).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Nadie se suspende ni se revoca a sí mismo, para no quedarse fuera de la empresa")
    void nobodyCanSuspendOrRevokeThemselves() throws Exception {
        RestWorld.Person admin = world.register();
        world.addMember(company, admin.accountId(), "ACTIVE", "[\"COMPANY_ADMIN\"]");
        String adminToken = world.enter(admin.accountId(), company, world.login(admin.email()));
        String own = membershipOf(admin);

        action("suspension", adminToken, own).andExpect(status().isUnprocessableEntity());
        action("revocation", adminToken, own).andExpect(status().isUnprocessableEntity());
        action("suspension", adminToken).andExpect(status().isOk());
    }

    @Test
    @DisplayName("El personal sin permiso no puede; la membresía de otra empresa o inexistente responde 404")
    void staffCannotAndForeignOrUnknownMembershipsAreNotFound() throws Exception {
        action("suspension", anaCompanyToken).andExpect(status().isForbidden());
        action("revocation", anaCompanyToken).andExpect(status().isForbidden());
        action("suspension", ownerToken, "mem_no_existe").andExpect(status().isNotFound());

        RestWorld.Person other = world.register();
        String otherAccount = world.login(other.email());
        String otherCompany = world.createCompany(other.accountId(), otherAccount);
        String otherToken = world.enter(other.accountId(), otherCompany, otherAccount);
        send(otherCompany, membership, "suspension", otherToken, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("La acción exige la clave de idempotencia y repetirla no repite el efecto ni el evento")
    void theActionNeedsTheIdempotencyKeyAndRepeatingItDoesNothingMore() throws Exception {
        send(company, membership, "suspension", ownerToken, null).andExpect(status().isBadRequest());

        String key = UUID.randomUUID().toString();
        send(company, membership, "suspension", ownerToken, key).andExpect(status().isOk());
        send(company, membership, "suspension", ownerToken, key).andExpect(status().isOk());

        List<String> types = jdbc.queryForList("SELECT event_type FROM company_management.outbox_event WHERE company_id=?", String.class, company);
        assertThat(types.stream().filter("membership.suspended"::equals).count()).as("un solo evento de suspensión").isEqualTo(1);
    }

    @Test
    @DisplayName("Cada cambio deja su evento con el estado nuevo y sin datos personales")
    void eachChangeLeavesItsEventWithoutPersonalData() throws Exception {
        action("suspension", ownerToken).andExpect(status().isOk());
        action("reactivation", ownerToken).andExpect(status().isOk());
        action("revocation", ownerToken).andExpect(status().isOk());

        assertThat(payloadOf("membership.suspended")).contains(membership).contains("\"SUSPENDED\"");
        assertThat(payloadOf("membership.reactivated")).contains(membership).contains("\"ACTIVE\"");
        assertThat(payloadOf("membership.revoked")).contains(membership).contains("\"REVOKED\"");
        assertThat(payloadOf("membership.suspended") + payloadOf("membership.reactivated") + payloadOf("membership.revoked"))
            .doesNotContain(ana.email());
    }

    // ---- Ayudas ----

    private String payloadOf(String type) {
        return String.join("\n", jdbc.queryForList("SELECT payload_json FROM company_management.outbox_event WHERE company_id=? AND event_type=?",
            String.class, company, type));
    }

    private String membershipOf(RestWorld.Person person) {
        return jdbc.queryForObject("SELECT public_membership_id FROM company_management.company_membership WHERE public_company_id=? AND user_id=?",
            String.class, company, person.accountId());
    }

    private ResultActions action(String name, String bearer) throws Exception {
        return action(name, bearer, membership);
    }

    private ResultActions action(String name, String bearer, String membershipId) throws Exception {
        return send(company, membershipId, name, bearer, UUID.randomUUID().toString());
    }

    private ResultActions send(String companyId, String membershipId, String name, String bearer, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder builder = request(HttpMethod.POST, "/api/v1/companies/" + companyId + "/memberships/" + membershipId + "/" + name)
            .header("Authorization", bearer);
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(builder);
    }

    private ResultActions selfPermissions(String companyToken) throws Exception {
        return mockMvc.perform(request(HttpMethod.GET, "/api/v1/companies/" + company + "/memberships/me/effective-permissions")
            .header("Authorization", companyToken));
    }

    private ResultActions enter(RestWorld.Person person, String accountToken) throws Exception {
        return mockMvc.perform(request(HttpMethod.POST, "/api/v1/accounts/" + person.accountId() + "/company-accesses/" + company + "/session")
            .header("Authorization", accountToken));
    }
}
