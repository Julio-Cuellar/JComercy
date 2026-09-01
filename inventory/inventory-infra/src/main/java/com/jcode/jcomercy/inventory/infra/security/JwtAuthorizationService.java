package com.jcode.jcomercy.inventory.infra.security;

import com.jcode.jcomercy.inventory.domain.model.warehouse.Warehouse;
import com.jcode.jcomercy.inventory.domain.port.WarehouseBranchAccessRepositoryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthorizationService {
    private final WarehouseBranchAccessRepositoryPort accessRepository;

    public JwtAuthorizationService() {
        this.accessRepository = null;
    }

    @Autowired
    public JwtAuthorizationService(WarehouseBranchAccessRepositoryPort accessRepository) {
        this.accessRepository = accessRepository;
    }

    public boolean canAccessCompany(Authentication authentication, String companyId) {
        return JwtContext.from(authentication).canAccessCompany(companyId);
    }

    public boolean canAccessBranch(Authentication authentication, String branchId) {
        return JwtContext.from(authentication).canAccessBranch(branchId);
    }

    public boolean hasPermission(Authentication authentication, String permission) {
        if (authentication == null || permission == null) return false;
        String normalized = permission.toUpperCase().replace('.', '_');
        boolean direct = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("PERM_" + permission)
                        || authority.getAuthority().equals("PERM_" + normalized));
        if (direct) return true;

        // Compatibilidad temporal con los permisos heredados del primer JWT local.
        return switch (permission) {
            case "inventory.warehouses.manage", "inventory.products.manage", "inventory.units.manage",
                    "inventory.stock.write", "inventory.stock.adjust" ->
                    hasAuthority(authentication, "PERM_INVENTORY_WRITE");
            case "inventory.warehouses.read", "inventory.products.read", "inventory.units.read", "inventory.stock.read" ->
                    hasAuthority(authentication, "PERM_INVENTORY_READ")
                            || hasAuthority(authentication, "PERM_INVENTORY_WRITE");
            default -> false;
        };
    }

    public boolean hasAnyPermission(Authentication authentication, String... permissions) {
        for (String permission : permissions) {
            if (hasPermission(authentication, permission)) return true;
        }
        return false;
    }

    public boolean canAccessWarehouse(Authentication authentication, Warehouse warehouse) {
        if (warehouse == null) return false;
        var context = JwtContext.from(authentication);
        if (!context.canAccessCompany(warehouse.companyId())) return false;
        if (warehouse.branchId() != null) return context.canAccessBranch(warehouse.branchId());
        if (hasPermission(authentication, "inventory.warehouses.manage")) return true;
        return accessRepository != null && context.branchIds().stream()
                .anyMatch(branchId -> accessRepository.findWarehouseIdsByBranchId(branchId).contains(warehouse.id()));
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals(authority));
    }
}
