package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import java.util.ArrayList; import java.util.List;

@Component
public class PermissionResolver {
    private final JdbcTemplate jdbc; private final ObjectMapper mapper;
    public PermissionResolver(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    public List<String> resolve(String userId) {
        List<String> result = new ArrayList<>();
        result.addAll(List.of("accounts.read", "companies.create", "companies.activate", "branches.create", "branches.activate"));
        try {
            jdbc.query("SELECT roles_json FROM company_management.company_membership WHERE user_id=? AND status='ACTIVE'", rs -> {
                try { for (String role : mapper.readValue(rs.getString(1), new TypeReference<List<String>>(){})) {
                    jdbc.query("SELECT permissions_json FROM company_management.role WHERE name=? AND status='ACTIVE'", new Object[]{role}, r -> {
                        try { result.addAll(mapper.readValue(r.getString(1), new TypeReference<List<String>>(){})); } catch (Exception ignored) { }
                    });
                }} catch (Exception ignored) { }
            }, userId);
        } catch (RuntimeException ignored) { }
        return result.stream().distinct().toList();
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
