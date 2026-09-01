package com.jcode.jcomercy.inventory.infra.graphql;

import com.jcode.jcomercy.inventory.domain.port.WarehouseLocationRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.WarehouseRepositoryPort;
import com.jcode.jcomercy.inventory.domain.model.warehouse.Warehouse;
import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseLocation;
import com.jcode.jcomercy.inventory.domain.model.product.Product;
import com.jcode.jcomercy.inventory.domain.model.unit.UnitOfMeasure;
import com.jcode.jcomercy.inventory.domain.port.ProductRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.UnitOfMeasureRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.ProductUnitConversionRepositoryPort;
import com.jcode.jcomercy.inventory.domain.model.product.ProductUnitConversion;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import com.jcode.jcomercy.inventory.infra.security.JwtAuthorizationService;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class InventoryGraphQlController {

    private final WarehouseRepositoryPort warehouseRepository;
    private final WarehouseLocationRepositoryPort locationRepository;
    private final JwtAuthorizationService authorization;
    private final ProductRepositoryPort productRepository;
    private final UnitOfMeasureRepositoryPort unitRepository;
    private final ProductUnitConversionRepositoryPort conversionRepository;
    private final com.jcode.jcomercy.inventory.application.service.StockApplicationService stockApplicationService;
    private final com.jcode.jcomercy.inventory.domain.port.CompanyUnitOfMeasureRepositoryPort companyUnitRepository;

    public InventoryGraphQlController(WarehouseRepositoryPort warehouseRepository,
                                       WarehouseLocationRepositoryPort locationRepository,
                                       JwtAuthorizationService authorization,
                                       ProductRepositoryPort productRepository,
                                       UnitOfMeasureRepositoryPort unitRepository,
                                       ProductUnitConversionRepositoryPort conversionRepository,
                                       com.jcode.jcomercy.inventory.application.service.StockApplicationService stockApplicationService,
                                       com.jcode.jcomercy.inventory.domain.port.CompanyUnitOfMeasureRepositoryPort companyUnitRepository) {
        this.warehouseRepository = warehouseRepository;
        this.locationRepository = locationRepository;
        this.authorization = authorization;
        this.productRepository = productRepository;
        this.unitRepository = unitRepository;
        this.conversionRepository = conversionRepository;
        this.stockApplicationService = stockApplicationService;
        this.companyUnitRepository = companyUnitRepository;
    }

    @QueryMapping
    public Warehouse warehouse(@Argument UUID id, Authentication authentication) {
        var warehouse = warehouseRepository.findById(id).orElse(null);
        return authorization.canAccessWarehouse(authentication, warehouse)
                && authorization.hasAnyPermission(authentication, "inventory.warehouses.read", "inventory.warehouses.manage")
                ? warehouse : null;
    }

    @QueryMapping
    public java.util.List<WarehouseLocation> locations(@Argument UUID warehouseId, Authentication authentication) {
        var warehouse = warehouseRepository.findById(warehouseId).orElse(null);
        if (!authorization.canAccessWarehouse(authentication, warehouse)
                || !authorization.hasAnyPermission(authentication, "inventory.warehouses.read", "inventory.warehouses.manage")) {
            return java.util.List.of();
        }
        return locationRepository.findByWarehouseId(warehouseId);
    }

    @QueryMapping
    public Product product(@Argument UUID id, Authentication authentication) {
        var product = productRepository.findById(id).orElse(null);
        return product != null && authorization.canAccessCompany(authentication, product.companyId())
                && authorization.hasAnyPermission(authentication, "inventory.products.read", "inventory.products.manage")
                ? product : null;
    }

    @QueryMapping
    public List<Product> products(@Argument String companyId, Authentication authentication) {
        return authorization.canAccessCompany(authentication, companyId)
                && authorization.hasAnyPermission(authentication, "inventory.products.read", "inventory.products.manage")
                ? productRepository.findByCompanyId(companyId) : List.of();
    }

    @QueryMapping
    public List<UnitOfMeasure> unitsOfMeasure() {
        return unitRepository.findActive();
    }

    @QueryMapping
    public List<ProductUnitConversion> productUnitConversions(@Argument UUID productId, Authentication authentication) {
        var product = productRepository.findById(productId).orElse(null);
        return product != null && authorization.canAccessCompany(authentication, product.companyId())
                && authorization.hasAnyPermission(authentication, "inventory.products.read", "inventory.products.manage")
                ? conversionRepository.findByProductId(productId) : List.of();
    }

    @QueryMapping
    public List<com.jcode.jcomercy.inventory.domain.model.stock.StockBalance> stockBalances(@Argument String companyId,
                                                                                             @Argument UUID productId,
                                                                                             Authentication authentication) {
        return authorization.canAccessCompany(authentication, companyId)
                && authorization.hasPermission(authentication, "inventory.stock.read")
                ? stockApplicationService.listBalances(companyId, productId) : List.of();
    }

    @QueryMapping
    public List<com.jcode.jcomercy.inventory.domain.model.stock.InventoryMovement> inventoryMovements(@Argument String companyId,
                                                                                                       @Argument UUID productId,
                                                                                                       Authentication authentication) {
        return authorization.canAccessCompany(authentication, companyId)
                && authorization.hasPermission(authentication, "inventory.stock.read")
                ? stockApplicationService.listMovements(companyId, productId) : List.of();
    }

    @QueryMapping
    public List<UnitOfMeasure> companyUnits(@Argument String companyId, Authentication authentication) {
        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.hasAnyPermission(authentication, "inventory.units.read", "inventory.units.manage")) return List.of();
        return companyUnitRepository.findActiveByCompanyId(companyId).stream()
                .map(cu -> new UnitOfMeasure(cu.code(), cu.name(), cu.symbol(), cu.dimension(),
                        cu.baseUnitCode(), cu.conversionFactor(), cu.decimals()))
                .collect(Collectors.toList());
    }

    @BatchMapping(typeName = "Warehouse", field = "locations")
    public Map<Warehouse, List<WarehouseLocation>> warehouseLocations(List<Warehouse> warehouses,
                                                                         Authentication authentication) {
        var accessible = warehouses.stream()
                .filter(warehouse -> authorization.canAccessWarehouse(authentication, warehouse)
                        && authorization.hasAnyPermission(authentication, "inventory.warehouses.read", "inventory.warehouses.manage"))
                .toList();
        var locationsByWarehouse = locationRepository.findByWarehouseIds(
                accessible.stream().map(Warehouse::id).toList());
        return warehouses.stream().collect(Collectors.toMap(
                warehouse -> warehouse,
                warehouse -> locationsByWarehouse.getOrDefault(warehouse.id(), List.of())));
    }
}
