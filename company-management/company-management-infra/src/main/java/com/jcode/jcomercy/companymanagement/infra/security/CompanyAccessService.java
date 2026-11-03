package com.jcode.jcomercy.companymanagement.infra.security;

import com.jcode.jcomercy.companymanagement.domain.exception.UnauthorizedAccessException;
import com.jcode.jcomercy.companymanagement.domain.model.company.Company;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Las empresas a las que una cuenta puede entrar y la entrada misma. Entrar entrega un token que nombra solo esa
 * empresa, con los permisos y las sucursales que la persona tiene en ella (ERR-011): nunca junta las de varias.
 */
@Component
public class CompanyAccessService {
    private static final Logger log = LoggerFactory.getLogger(CompanyAccessService.class);

    /**
     * Una empresa a la que la cuenta puede entrar, como dueña ("OWNER") o por una membresía activa ("MEMBER").
     * {@code passwordProtected} dice si la persona le puso contraseña a su acceso.
     */
    public record CompanyAccess(String publicCompanyId, String tradeName, String status, String relationship,
                                List<String> roles, boolean passwordProtected) { }

    public record CompanySession(String accessToken, String tokenType, long expiresIn, String publicCompanyId) { }

    private final CompanyRepositoryPort companies;
    private final EffectivePermissions permissions;
    private final CompanyAccessPasswords passwords;
    private final JwtTokenService tokens;
    private final JdbcTemplate jdbc;
    private final long expirationMilliseconds;

    public CompanyAccessService(CompanyRepositoryPort companies, EffectivePermissions permissions,
                                CompanyAccessPasswords passwords, JwtTokenService tokens, JdbcTemplate jdbc,
                                @Value("${security.jwt.expiration-ms:3600000}") long expirationMilliseconds) {
        this.companies = companies;
        this.permissions = permissions;
        this.passwords = passwords;
        this.tokens = tokens;
        this.jdbc = jdbc;
        this.expirationMilliseconds = expirationMilliseconds;
    }

    public List<CompanyAccess> list(String accountId) {
        List<CompanyAccess> accesses = new ArrayList<>();
        Set<String> owned = new HashSet<>();
        Set<String> protectedCompanies = passwords.protectedCompanyIds(accountId);
        for (Company company : companies.findByPublicAccountId(accountId)) {
            owned.add(company.getPublicCompanyId());
            accesses.add(new CompanyAccess(company.getPublicCompanyId(), company.getTradeName(),
                company.getStatus().name(), "OWNER", List.of(), protectedCompanies.contains(company.getPublicCompanyId())));
        }
        for (String companyId : permissions.activeMembershipCompanyIds(accountId)) {
            if (owned.contains(companyId)) {
                continue;
            }
            companies.findByPublicCompanyId(companyId).ifPresent(company -> accesses.add(new CompanyAccess(
                company.getPublicCompanyId(), company.getTradeName(), company.getStatus().name(), "MEMBER",
                permissions.rolesInCompany(accountId, companyId), protectedCompanies.contains(companyId))));
        }
        return accesses;
    }

    /** Entra a la empresa. Si la persona le puso contraseña a su acceso, hay que escribirla bien. */
    public CompanySession enter(String accountId, String companyId, String password) {
        requireAccess(accountId, companyId);
        passwords.checkToEnter(accountId, companyId, password);
        List<String> granted = List.copyOf(permissions.inCompany(accountId, companyId));
        List<String> branchIds = branchIdsOf(accountId, companyId);
        log.info("Entrada a empresa accountId={} companyId={} sucursales={}", accountId, companyId, branchIds.size());
        return new CompanySession(tokens.issueCompanyToken(accountId, companyId, granted, branchIds),
            "Bearer", expirationMilliseconds / 1000, companyId);
    }

    /** Pone la contraseña del acceso de la persona a esa empresa o la cambia (entonces exige la actual). */
    public void setPassword(String accountId, String companyId, String currentPassword, String newPassword) {
        requireAccess(accountId, companyId);
        passwords.set(accountId, companyId, currentPassword, newPassword);
    }

    /** Quita la contraseña del acceso. Exige la contraseña de la cuenta, que es la salida si se olvidó la de la empresa. */
    public void removePassword(String accountId, String companyId, String accountPassword) {
        requireAccess(accountId, companyId);
        passwords.remove(accountId, companyId, accountPassword);
    }

    /** Una empresa ajena responde lo mismo que una que no existe, para no revelar cuáles hay. */
    private void requireAccess(String accountId, String companyId) {
        if (!permissions.belongsToCompany(accountId, companyId)) {
            log.warn("Acceso a empresa denegado accountId={} companyId={}", accountId, companyId);
            throw new UnauthorizedAccessException(RestOwnershipInterceptor.COMPANY_DENIED);
        }
    }

    /** La dueña opera todas las sucursales activas de su empresa; un miembro, solo las que tiene asignadas. */
    private List<String> branchIdsOf(String accountId, String companyId) {
        if (permissions.ownsTheCompany(accountId, companyId)) {
            return jdbc.queryForList("""
                SELECT public_branch_id FROM company_management.branch
                WHERE public_company_id=? AND status='ACTIVE'
                """, String.class, companyId);
        }
        return jdbc.queryForList("""
            SELECT ba.public_branch_id
            FROM company_management.branch_assignment ba
            JOIN company_management.company_membership cm ON cm.public_membership_id = ba.public_membership_id
            WHERE cm.user_id=? AND cm.public_company_id=? AND cm.status='ACTIVE' AND ba.status='ACTIVE'
            """, String.class, accountId, companyId);
    }
}
