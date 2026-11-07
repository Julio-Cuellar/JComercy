package com.jcode.jcomercy.sales.infra.config;

import com.jcode.jcomercy.sales.application.service.PriceApplicationService;
import com.jcode.jcomercy.sales.application.service.SaleQueryService;
import com.jcode.jcomercy.sales.domain.port.ProductPriceRepositoryPort;
import com.jcode.jcomercy.sales.domain.port.SaleRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

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
}
