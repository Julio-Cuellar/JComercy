package com.jcode.jcomercy.sales.application.service;

import com.jcode.jcomercy.sales.application.exception.InsufficientStockException;
import com.jcode.jcomercy.sales.application.exception.InventoryRejectedException;
import com.jcode.jcomercy.sales.application.exception.InventoryUnavailableException;
import com.jcode.jcomercy.sales.application.exception.SaleConfirmationPendingException;
import com.jcode.jcomercy.sales.application.exception.SalesApplicationException;
import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort;
import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort.Caller;
import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort.ProductInfo;
import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort.StockReference;
import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort.StockReservation;
import com.jcode.jcomercy.sales.domain.exception.DuplicateSaleException;
import com.jcode.jcomercy.sales.domain.exception.SalesDomainException;
import com.jcode.jcomercy.sales.domain.model.price.ProductPrice;
import com.jcode.jcomercy.sales.domain.model.sale.Payment;
import com.jcode.jcomercy.sales.domain.model.sale.Sale;
import com.jcode.jcomercy.sales.domain.model.sale.SaleChannel;
import com.jcode.jcomercy.sales.domain.model.sale.SaleLine;
import com.jcode.jcomercy.sales.domain.model.sale.SaleStatus;
import com.jcode.jcomercy.sales.domain.port.ProductPriceRepositoryPort;
import com.jcode.jcomercy.sales.domain.port.SaleRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cobra una venta de mostrador. Busca cada producto en inventario, toma su precio de la lista de la empresa, aparta el
 * stock, registra el pago, confirma la salida de inventario y, al final, da el folio.
 *
 * <p>No es transaccional: llama a inventario por red y no debe tener una transacción abierta mientras espera. En su lugar
 * es <b>reentrante</b>. Cada operación contra inventario lleva una llave que sale de la venta y de la línea (nunca del
 * momento), y la venta se guarda con la llave de la petición. Repetir la petición con la misma llave retoma la venta
 * donde se quedó, y inventario no repite lo que ya hizo.
 *
 * <p>Los pasos y qué queda si falla cada uno: antes de guardar nada se valida todo lo que no necesita inventario
 * (productos, precios, pagos), así que un error ahí no deja rastro. Si falla el apartado se libera lo apartado y la venta
 * queda cancelada. Si falla la confirmación, la venta queda confirmándose, ya cobrada, y se retoma repitiendo la petición.
 */
public class SaleCheckoutService {
    private static final Logger log = LoggerFactory.getLogger(SaleCheckoutService.class);
    private static final String REFERENCE_TYPE = "SALE";
    /** Lo que cabe en la columna de la llave. */
    private static final int MAX_KEY_LENGTH = 100;

    private final ProductPriceRepositoryPort prices;
    private final SaleRepositoryPort sales;
    private final InventoryGatewayPort inventory;
    private final SaleCompletionService completion;
    private final Clock clock;
    private final Duration reservationTtl;

    public SaleCheckoutService(ProductPriceRepositoryPort prices, SaleRepositoryPort sales, InventoryGatewayPort inventory,
                               SaleCompletionService completion, Clock clock, Duration reservationTtl) {
        this.prices = prices;
        this.sales = sales;
        this.inventory = inventory;
        this.completion = completion;
        this.clock = clock;
        this.reservationTtl = reservationTtl;
    }

    public CheckoutResult checkout(CheckoutCommand command) {
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new SalesDomainException("Falta la llave de idempotencia de la venta.");
        }
        String key = command.idempotencyKey().trim();
        if (key.length() > MAX_KEY_LENGTH) {
            throw new SalesDomainException("La llave de idempotencia no puede pasar de %d caracteres.".formatted(MAX_KEY_LENGTH));
        }
        var existing = sales.findByIdempotencyKey(command.companyId(), command.branchId(), key);
        if (existing.isPresent()) {
            return resume(existing.get(), command);
        }

        Built built = build(command);
        Sale sale = built.sale();
        sale.registerPayments(command.payments());
        try {
            sales.create(sale, key);
        } catch (DuplicateSaleException duplicate) {
            var raced = sales.findByIdempotencyKey(command.companyId(), command.branchId(), key);
            if (raced.isPresent()) {
                return resume(raced.get(), command);
            }
            throw duplicate;
        }
        log.info("Venta iniciada saleId={} companyId={} branchId={} lineas={} total={} actor={}", sale.id(), sale.companyId(),
                sale.branchId(), sale.lines().size(), sale.total(), command.actorUserId());
        return new CheckoutResult(process(sale, command, built.units()), false);
    }

    private CheckoutResult resume(Sale existing, CheckoutCommand command) {
        if (!existing.actorUserId().equals(command.actorUserId())) {
            throw SalesApplicationException.conflict("Esa llave ya se usó en otra venta. Usa una llave nueva.");
        }
        return switch (existing.status()) {
            case COMPLETED -> new CheckoutResult(existing, true);
            case CANCELLED -> throw SalesApplicationException.conflict("Esa venta ya se canceló. Para vender de nuevo usa otra llave.");
            case PENDING, CONFIRMING -> {
                log.info("Se retoma la venta saleId={} en estado {}", existing.id(), existing.status());
                yield new CheckoutResult(process(existing, command, null), false);
            }
        };
    }

    /**
     * Lleva la venta de donde esté hasta completarla. Cada paso es seguro de repetir. Las unidades de stock salen de
     * cuando se armó la venta; al retomar una venta guardada no se tienen y se buscan en inventario.
     */
    private Sale process(Sale sale, CheckoutCommand command, Map<UUID, String> knownUnits) {
        Sale current = sale;
        if (current.status() == SaleStatus.PENDING) {
            reserveAll(current, command, knownUnits != null ? knownUnits : unitsOf(current, command));
            try {
                current = sales.save(current.registerPayments(command.payments()));
            } catch (SalesDomainException invalidPayments) {
                abort(current, command, current.lines(), "Pago inválido: " + invalidPayments.getMessage());
                throw invalidPayments;
            }
        }
        commitAll(current, command);
        return completion.complete(current, command.caller().correlationId());
    }

    private Built build(CheckoutCommand command) {
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new SalesDomainException("La venta necesita al menos un producto.");
        }
        List<ProductInfo> products = new ArrayList<>();
        for (LineInput input : command.lines()) {
            ProductInfo info = inventory.findProduct(command.caller(), input.productId())
                    .filter(found -> found.companyId().equals(command.companyId()))
                    .orElseThrow(() -> SalesApplicationException.notFound("No encontramos uno de los productos en inventario."));
            if (!"ACTIVE".equals(info.status())) {
                throw new SalesDomainException("%s no está activo en inventario.".formatted(info.sku()));
            }
            if (!"NONE".equals(info.trackingMode())) {
                throw new SalesDomainException(
                        "%s se vende por lote o serie y todavía no se puede vender en el mostrador.".formatted(info.sku()));
            }
            products.add(info);
        }
        Map<UUID, ProductPrice> priceByProduct = new HashMap<>();
        prices.findByCompanyIdAndProductIds(command.companyId(), products.stream().map(ProductInfo::id).toList())
                .forEach(price -> priceByProduct.put(price.productId(), price));
        List<SaleLine> lines = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            ProductInfo info = products.get(i);
            ProductPrice price = priceByProduct.get(info.id());
            if (price == null) {
                throw new SalesDomainException("Fija el precio de %s antes de venderlo.".formatted(info.sku()));
            }
            lines.add(SaleLine.of(info.id(), info.sku(), info.name(), command.lines().get(i).quantity(), price.amount()));
        }
        var currencies = lines.stream().map(line -> priceByProduct.get(line.productId()).currency()).collect(Collectors.toSet());
        if (currencies.size() > 1) {
            throw new SalesDomainException(
                    "Los productos de esta venta tienen precios en monedas distintas. Corrige los precios antes de vender.");
        }
        Sale sale = Sale.create(UUID.randomUUID(), command.companyId(), command.branchId(), command.warehouseId(),
                SaleChannel.MOSTRADOR, null, currencies.iterator().next(), lines, command.actorUserId(), clock.instant());
        Map<UUID, String> units = new HashMap<>();
        products.forEach(info -> units.put(info.id(), info.stockUnitCode()));
        return new Built(sale, units);
    }

    /** Las unidades de stock de los productos de una venta ya guardada: inventario es quien las sabe. */
    private Map<UUID, String> unitsOf(Sale sale, CheckoutCommand command) {
        Map<UUID, String> units = new HashMap<>();
        for (SaleLine line : sale.lines()) {
            ProductInfo info = inventory.findProduct(command.caller(), line.productId())
                    .orElseThrow(() -> SalesApplicationException.notFound("No encontramos uno de los productos en inventario."));
            units.put(info.id(), info.stockUnitCode());
        }
        return units;
    }

    private void reserveAll(Sale sale, CheckoutCommand command, Map<UUID, String> units) {
        List<SaleLine> reserved = new ArrayList<>();
        for (SaleLine line : sale.lines()) {
            try {
                inventory.reserve(command.caller(), new StockReservation(sale.companyId(), sale.branchId(), sale.warehouseId(),
                        line.productId(), line.quantity().stripTrailingZeros(), units.get(line.productId()), REFERENCE_TYPE,
                        referenceId(sale, line), sale.createdAt().plus(reservationTtl), key(sale, "reserve", line)));
                reserved.add(line);
            } catch (InsufficientStockException e) {
                abort(sale, command, reserved, "Sin existencias suficientes de " + line.sku());
                throw SalesApplicationException.conflict(
                        "No alcanza el stock de %s. Revisa las existencias o baja la cantidad.".formatted(line.sku()));
            } catch (InventoryRejectedException e) {
                abort(sale, command, reserved, "Inventario no permitió apartar " + line.sku());
                throw SalesApplicationException.conflict(
                        "Inventario no permitió apartar %s: %s".formatted(line.sku(), e.getMessage()));
            } catch (InventoryUnavailableException e) {
                // Puede que inventario sí haya apartado esta línea y la respuesta se perdiera: se libera también.
                List<SaleLine> toRelease = new ArrayList<>(reserved);
                toRelease.add(line);
                abort(sale, command, toRelease, "Inventario no respondió al apartar el stock");
                throw e;
            }
        }
    }

    private void commitAll(Sale sale, CheckoutCommand command) {
        for (SaleLine line : sale.lines()) {
            try {
                inventory.commit(command.caller(), reference(sale, line, "commit", null));
            } catch (InventoryUnavailableException e) {
                log.warn("La venta saleId={} queda confirmándose: inventario no respondió al confirmar {}", sale.id(), line.sku());
                throw new SaleConfirmationPendingException(sale.id());
            } catch (InventoryRejectedException | InsufficientStockException e) {
                log.error("La venta saleId={} queda confirmándose: inventario rechazó confirmar {}: {}", sale.id(), line.sku(),
                        e.getMessage());
                throw SalesApplicationException.conflict(
                        "Inventario no pudo confirmar la salida de %s: %s. La venta quedó por confirmar; avisa a quien administra el negocio."
                                .formatted(line.sku(), e.getMessage()));
            }
        }
    }

    /** Libera lo apartado, lo mejor que se pueda, y deja la venta cancelada con su motivo. */
    private void abort(Sale sale, CheckoutCommand command, List<SaleLine> toRelease, String reason) {
        for (SaleLine line : toRelease) {
            try {
                inventory.release(command.caller(), reference(sale, line, "release", reason));
            } catch (RuntimeException e) {
                // El apartado trae un plazo: si no se pudo liberar ahora, se libera solo cuando venza.
                log.warn("No se pudo liberar el apartado de {} de la venta saleId={}: {}", line.sku(), sale.id(), e.getMessage());
            }
        }
        sales.save(sale.cancel(reason, clock.instant()));
        log.warn("Venta cancelada saleId={} motivo={}", sale.id(), reason);
    }

    private StockReference reference(Sale sale, SaleLine line, String operation, String reason) {
        return new StockReference(sale.companyId(), sale.branchId(), REFERENCE_TYPE, referenceId(sale, line), reason,
                key(sale, operation, line));
    }

    private static String referenceId(Sale sale, SaleLine line) {
        return sale.id() + ":" + line.lineNumber();
    }

    private static String key(Sale sale, String operation, SaleLine line) {
        return "sale-%s-%s-%d".formatted(sale.id(), operation, line.lineNumber());
    }

    /** La venta armada y la unidad de stock de cada producto, que inventario dijo al armarla. */
    private record Built(Sale sale, Map<UUID, String> units) { }

    public record CheckoutCommand(String companyId, String branchId, UUID warehouseId, List<LineInput> lines,
                                  List<Payment> payments, String actorUserId, String idempotencyKey, Caller caller) { }

    public record LineInput(UUID productId, BigDecimal quantity) { }

    /** La venta cobrada, y si ya estaba cobrada de antes (la misma llave repetida). */
    public record CheckoutResult(Sale sale, boolean replayed) { }
}
