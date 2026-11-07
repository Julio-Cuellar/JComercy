package com.jcode.jcomercy.sales.application.service;

import com.jcode.jcomercy.sales.domain.model.price.ProductPrice;
import com.jcode.jcomercy.sales.domain.port.ProductPriceRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Fijar y consultar el precio de venta de cada producto de una empresa. */
public class PriceApplicationService {
    private static final Logger log = LoggerFactory.getLogger(PriceApplicationService.class);

    private final ProductPriceRepositoryPort prices;
    private final Clock clock;

    public PriceApplicationService(ProductPriceRepositoryPort prices, Clock clock) {
        this.prices = prices;
        this.clock = clock;
    }

    /** Crea el precio del producto o lo reemplaza; las ventas ya hechas conservan el suyo. */
    @Transactional
    public ProductPrice setPrice(SetPriceCommand command) {
        var price = new ProductPrice(command.companyId(), command.productId(), command.amount(), command.currency(),
                clock.instant(), command.actorUserId());
        log.info("Precio fijado companyId={} productId={} amount={} currency={} actor={}", price.companyId(),
                price.productId(), price.amount(), price.currency(), price.updatedBy());
        return prices.save(price);
    }

    @Transactional(readOnly = true)
    public List<ProductPrice> list(String companyId) {
        return prices.findByCompanyId(companyId);
    }

    public record SetPriceCommand(String companyId, UUID productId, BigDecimal amount, String currency,
                                  String actorUserId) { }
}
