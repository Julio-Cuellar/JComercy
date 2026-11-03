package com.jcode.jcomercy.inventory.infra.security;

import com.jcode.jcomercy.inventory.domain.model.warehouse.Warehouse;
import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseScope;
import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseStatus;
import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseType;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtContextTest {
    private final JwtAuthorizationService authorization = new JwtAuthorizationService();

    @Test
    void allowsWarehouseWhenCompanyAndBranchAreInClaims() {
        var authentication = authentication(List.of("company-1"), List.of("branch-1"));
        var warehouse = warehouse("company-1", "branch-1", WarehouseScope.BRANCH_OWNED);

        assertThat(authorization.canAccessWarehouse(authentication, warehouse)).isTrue();
    }

    @Test
    void rejectsWarehouseFromAnotherCompany() {
        var authentication = authentication(List.of("company-1"), List.of("branch-1"));
        var warehouse = warehouse("company-2", "branch-1", WarehouseScope.BRANCH_OWNED);

        assertThat(authorization.canAccessWarehouse(authentication, warehouse)).isFalse();
    }

    @Test
    void rejectsBranchNotAssignedToUser() {
        var authentication = authentication(List.of("company-1"), List.of("branch-2"));
        var warehouse = warehouse("company-1", "branch-1", WarehouseScope.BRANCH_OWNED);

        assertThat(authorization.canAccessWarehouse(authentication, warehouse)).isFalse();
    }

    @Test
    void rejectsSharedWarehouseWhenUserHasNoAuthorizedBranch() {
        var authentication = authentication(List.of("company-1"), List.of());
        var warehouse = warehouse("company-1", null, WarehouseScope.COMPANY_SHARED);

        assertThat(authorization.canAccessWarehouse(authentication, warehouse)).isFalse();
    }

    private static UsernamePasswordAuthenticationToken authentication(List<String> companyIds,
                                                                       List<String> branchIds) {
        var authentication = new UsernamePasswordAuthenticationToken("account-1", null, List.of());
        authentication.setDetails(new JwtContext("account-1", companyIds, branchIds));
        return authentication;
    }

    private static Warehouse warehouse(String companyId, String branchId, WarehouseScope scope) {
        return new Warehouse(UUID.randomUUID(), UUID.randomUUID(), companyId, scope, branchId,
                "WH-1", "Principal", WarehouseType.GENERAL, WarehouseStatus.ACTIVE, false,
                null, null, null, null, "America/Mexico_City", "MXN", 0);
    }
}
