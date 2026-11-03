package com.jcode.jcomercy.companymanagement.infra.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import java.util.ArrayList; import java.util.List; import java.util.Set; import java.util.TreeSet;

@Component
public class PermissionResolver {
    private final JdbcTemplate jdbc; private final EffectivePermissions effectivePermissions;
    public PermissionResolver(JdbcTemplate jdbc, EffectivePermissions effectivePermissions){this.jdbc=jdbc;this.effectivePermissions=effectivePermissions;}
    /**
     * Los permisos que lleva el token: la unión de lo que la persona puede hacer en cada una de sus empresas. Quien es
     * dueña de alguna lleva "*"; quien entra por una membresía, los permisos de su rol. Company-management no lee esto
     * del token (calcula los permisos por empresa en cada petición); lo leen otros módulos, como inventario, que no
     * tienen acceso a estos datos.
     */
    public List<String> resolve(String userId) {
        Set<String> result = new TreeSet<>();
        for (String companyId : resolveCompanyIds(userId)) {
            result.addAll(effectivePermissions.inCompany(userId, companyId));
        }
        return List.copyOf(result);
    }

    public List<String> resolveCompanyIds(String accountId) {
        List<String> result = new ArrayList<>();
        try {
            jdbc.query("SELECT public_company_id FROM company_management.company WHERE public_account_id=?",
                new Object[]{accountId}, (RowCallbackHandler) rs -> result.add(rs.getString(1)));
            jdbc.query("SELECT public_company_id FROM company_management.company_membership WHERE user_id=? AND status='ACTIVE'",
                new Object[]{accountId}, (RowCallbackHandler) rs -> result.add(rs.getString(1)));
        } catch (RuntimeException ignored) { }
        return result.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    public List<String> resolveBranchIds(String accountId) {
        List<String> result = new ArrayList<>();
        try {
            jdbc.query("""
                SELECT b.public_branch_id
                FROM company_management.branch b
                JOIN company_management.company c ON c.public_company_id=b.public_company_id
                WHERE c.public_account_id=? AND b.status='ACTIVE'
                """, new Object[]{accountId}, (RowCallbackHandler) rs -> result.add(rs.getString(1)));
            jdbc.query("""
                SELECT ba.public_branch_id
                FROM company_management.branch_assignment ba
                JOIN company_management.company_membership cm
                  ON cm.public_membership_id = ba.public_membership_id
                WHERE cm.user_id=? AND cm.status='ACTIVE' AND ba.status='ACTIVE'
                """, new Object[]{accountId}, (RowCallbackHandler) rs -> result.add(rs.getString(1)));
        } catch (RuntimeException ignored) { }
        return result.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }
}
