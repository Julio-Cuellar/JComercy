package com.jcode.jcomercy.inventory.infra.config;

import com.jcode.jcomercy.inventory.application.service.WarehouseApplicationService;
import com.jcode.jcomercy.inventory.domain.port.WarehouseBranchAccessRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.WarehouseLocationRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.WarehouseRepositoryPort;
import com.jcode.jcomercy.inventory.application.service.ProductApplicationService;
import com.jcode.jcomercy.inventory.domain.port.ProductRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.UnitOfMeasureRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.ProductUnitConversionRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.CompanyUnitOfMeasureRepositoryPort;
import com.jcode.jcomercy.inventory.application.service.ReservationApplicationService;
import com.jcode.jcomercy.inventory.application.service.StockApplicationService;
import com.jcode.jcomercy.inventory.domain.port.StockReservationRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.StockBalanceRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.InventoryMovementRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.SerialNumberGeneratorPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InventoryInfraConfig {

    @Bean
    WarehouseApplicationService warehouseApplicationService(WarehouseRepositoryPort warehouseRepository,
                                                             WarehouseBranchAccessRepositoryPort accessRepository,
                                                             WarehouseLocationRepositoryPort locationRepository,
                                                             UnitOfMeasureRepositoryPort units,
                                                             CompanyUnitOfMeasureRepositoryPort companyUnits,
                                                             com.jcode.jcomercy.inventory.domain.port.InventoryOutboxPort outboxPort) {
        return new WarehouseApplicationService(warehouseRepository, accessRepository, locationRepository, units, companyUnits, outboxPort);
    }

    @Bean
    ProductApplicationService productApplicationService(ProductRepositoryPort products, UnitOfMeasureRepositoryPort units,
                                                        ProductUnitConversionRepositoryPort conversions,
                                                        CompanyUnitOfMeasureRepositoryPort companyUnits) {
        return new ProductApplicationService(products, units, conversions, companyUnits);
    }

    @Bean
    ReservationApplicationService reservationApplicationService(WarehouseRepositoryPort warehouses,
                                                                 WarehouseBranchAccessRepositoryPort branchAccess,
                                                                 ProductRepositoryPort products,
                                                                 StockBalanceRepositoryPort balances,
                                                                 StockReservationRepositoryPort reservations,
                                                                 InventoryMovementRepositoryPort movements,
                                                                 com.jcode.jcomercy.inventory.domain.port.InventoryOutboxPort outboxPort) {
        return new ReservationApplicationService(warehouses, branchAccess, products, balances, reservations, movements, outboxPort);
    }

    @Bean
    StockApplicationService stockApplicationService(WarehouseRepositoryPort warehouses,
                                                     WarehouseLocationRepositoryPort locations,
                                                     WarehouseBranchAccessRepositoryPort branchAccess,
                                                     ProductRepositoryPort products,
                                                     StockBalanceRepositoryPort balances,
                                                     InventoryMovementRepositoryPort movements,
                                                     SerialNumberGeneratorPort serials,
                                                     com.jcode.jcomercy.inventory.domain.port.InventoryOutboxPort outboxPort) {
        return new StockApplicationService(warehouses, locations, branchAccess, products, balances, movements, serials, outboxPort);
    }
}
