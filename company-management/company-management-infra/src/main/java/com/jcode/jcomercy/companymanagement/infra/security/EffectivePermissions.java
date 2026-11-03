package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.core.authorization.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lo que una persona puede hacer en una cuenta o en una empresa. Se calcula en cada petición desde la base de datos y
 * no se lee del token: así una membresía suspendida o un rol cambiado surten efecto de inmediato, y los permisos de
 * una empresa no se mezclan con los de otra.
 *
 * <p>El dueño de la cuenta puede todo en lo que es suyo. Cualquier otra persona recibe únicamente los permisos de los
 * roles de su membresía activa en esa empresa: los roles del sistema y los propios de esa empresa, nunca los de otra.
 */
@Component
public class EffectivePermissions {
    private static final Logger log = LoggerFactory.getLogger(EffectivePermissions.class);
    private static final TypeReference<List<String>> TEXT_LIST = new TypeReference<>() { };

    private final CompanyRepositoryPort companies;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public EffectivePermissions(CompanyRepositoryPort companies, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.companies = companies;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Dentro de su propia cuenta, quien actúa puede todo; en la de otra persona, nada. */
    public Set<String> inAccount(String actorId, String accountId) {
        return actorId != null && actorId.equals(accountId) ? Set.of(Permissions.ALL) : Set.of();
    }

    public Set<String> inCompany(String actorId, String companyId) {
        if (isBlank(actorId) || isBlank(companyId)) {
            return Set.of();
        }
        return ownsTheCompany(actorId, companyId) ? Set.of(Permissions.ALL) : permissionsOfTheActiveMembership(actorId, companyId);
    }

    /**
     * Si la persona pertenece a la empresa: es dueña de su cuenta o tiene en ella una membresía activa. De esto depende
     * que pueda entrar; lo que puede hacer dentro lo decide su rol ({@link #inCompany}). Sale de la base de datos y no
     * del token, así una membresía suspendida o una empresa creada después del login se reflejan en la siguiente petición.
     */
    public boolean belongsToCompany(String actorId, String companyId) {
        if (isBlank(actorId) || isBlank(companyId)) {
            return false;
        }
        return ownsTheCompany(actorId, companyId) || hasAnActiveMembership(actorId, companyId);
    }

    /** Si la cuenta de la persona es la dueña de la empresa. Una empresa que no existe no es de nadie. */
    public boolean ownsTheCompany(String actorId, String companyId) {
        return companies.findByPublicCompanyId(companyId)
            .map(company -> actorId.equals(company.getPublicAccountId()))
            .orElse(false);
    }

    /** Los ids de las empresas donde la persona tiene una membresía activa. */
    public List<String> activeMembershipCompanyIds(String actorId) {
        return jdbc.queryForList("""
            SELECT public_company_id FROM company_management.company_membership
            WHERE user_id=? AND status='ACTIVE'
            """, String.class, actorId);
    }

    /** Los nombres de los roles de la membresía activa de la persona en esa empresa; vacío si no la tiene. */
    public List<String> rolesInCompany(String actorId, String companyId) {
        List<String> roles = new ArrayList<>();
        List<String> memberships = jdbc.queryForList("""
            SELECT roles_json FROM company_management.company_membership
            WHERE public_company_id=? AND user_id=? AND status='ACTIVE'
            """, String.class, companyId, actorId);
        for (String rolesJson : memberships) {
            roles.addAll(readList(rolesJson, "roles de la membresía"));
        }
        return roles;
    }

    private boolean hasAnActiveMembership(String actorId, String companyId) {
        Integer active = jdbc.queryForObject("""
            SELECT COUNT(*) FROM company_management.company_membership
            WHERE public_company_id=? AND user_id=? AND status='ACTIVE'
            """, Integer.class, companyId, actorId);
        return active != null && active > 0;
    }

    public boolean canInAccount(Authentication authentication, String accountId, String permission) {
        return Permissions.grants(inAccount(nameOf(authentication), accountId), permission);
    }

    public boolean canInCompany(Authentication authentication, String companyId, String permission) {
        return Permissions.grants(inCompany(nameOf(authentication), companyId), permission);
    }

    private Set<String> permissionsOfTheActiveMembership(String actorId, String companyId) {
        Set<String> permissions = new TreeSet<>();
        for (String role : rolesInCompany(actorId, companyId)) {
            jdbc.queryForList("""
                SELECT permissions_json FROM company_management.role
                WHERE name=? AND status='ACTIVE' AND (public_company_id IS NULL OR public_company_id=?)
                """, String.class, role, companyId)
                .forEach(permissionsJson -> permissions.addAll(readList(permissionsJson, "permisos del rol " + role)));
        }
        return permissions;
    }

    /** Un dato dañado no concede nada: se avisa en el registro y esa fila se ignora. */
    private List<String> readList(String json, String what) {
        try {
            return mapper.readValue(json, TEXT_LIST);
        } catch (Exception e) {
            log.warn("No se pudieron leer los {} y se ignoran", what);
            return List.of();
        }
    }

    private static String nameOf(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
