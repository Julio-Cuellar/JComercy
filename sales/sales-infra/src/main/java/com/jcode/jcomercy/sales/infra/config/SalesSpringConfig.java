package com.jcode.jcomercy.sales.infra.config;

import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort;
import com.jcode.jcomercy.sales.application.port.SaleEventsPort;
import com.jcode.jcomercy.sales.application.service.PriceApplicationService;
import com.jcode.jcomercy.sales.application.service.SaleCancellationRecorder;
import com.jcode.jcomercy.sales.application.service.SaleCancellationService;
import com.jcode.jcomercy.sales.application.service.SaleCheckoutService;
import com.jcode.jcomercy.sales.application.service.SaleCompletionService;
import com.jcode.jcomercy.sales.application.service.SaleQueryService;
import com.jcode.jcomercy.sales.domain.port.FolioGeneratorPort;
import com.jcode.jcomercy.sales.domain.port.ProductPriceRepositoryPort;
import com.jcode.jcomercy.sales.domain.port.SaleRepositoryPort;
import com.jcode.jcomercy.sales.infra.inventory.HttpInventoryGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/** Los casos de uso de ventas no llevan anotaciones de Spring: aquí se cablean con sus puertos. */
@Configuration
public class SalesSpringConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PriceApplicationService priceApplicationService(ProductPriceRepositoryPort prices, Clock clock) {
        return new PriceApplicationService(prices, clock);
    }

    @Bean
    public SaleQueryService saleQueryService(SaleRepositoryPort sales) {
        return new SaleQueryService(sales);
    }

    /** El cliente HTTP de inventario: a dónde llamar y cuánto esperar antes de darlo por no disponible. */
    @Bean
    public InventoryGatewayPort inventoryGateway(@Value("${sales.inventory.base-url}") String baseUrl,
                                                 @Value("${sales.inventory.connect-timeout-ms:2000}") long connectTimeoutMs,
                                                 @Value("${sales.inventory.read-timeout-ms:5000}") long readTimeoutMs) {
        return new HttpInventoryGateway(baseUrl, Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(readTimeoutMs));
    }

    @Bean
    public SaleCompletionService saleCompletionService(SaleRepositoryPort sales, FolioGeneratorPort folios, SaleEventsPort events,
                                                       Clock clock) {
        return new SaleCompletionService(sales, folios, events, clock);
    }

    @Bean
    public SaleCancellationRecorder saleCancellationRecorder(SaleRepositoryPort sales, SaleEventsPort events, Clock clock) {
        return new SaleCancellationRecorder(sales, events, clock);
    }

    @Bean
    public SaleCancellationService saleCancellationService(SaleRepositoryPort sales, InventoryGatewayPort inventory,
                                                           SaleCancellationRecorder recorder) {
        return new SaleCancellationService(sales, inventory, recorder);
    }

    /** El plazo con que se aparta el stock de una venta: si se abandona, inventario lo libera solo al vencer. */
    @Bean
    public SaleCheckoutService saleCheckoutService(ProductPriceRepositoryPort prices, SaleRepositoryPort sales,
                                                   InventoryGatewayPort inventory, SaleCompletionService completion, Clock clock,
                                                   @Value("${sales.checkout.reservation-minutes:15}") long reservationMinutes) {
        return new SaleCheckoutService(prices, sales, inventory, completion, clock, Duration.ofMinutes(reservationMinutes));
    }
}
