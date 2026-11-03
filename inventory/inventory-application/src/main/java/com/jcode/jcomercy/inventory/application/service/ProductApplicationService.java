package com.jcode.jcomercy.inventory.application.service;

import com.jcode.jcomercy.inventory.application.exception.InventoryApplicationException;
import com.jcode.jcomercy.inventory.domain.model.product.Product;
import com.jcode.jcomercy.inventory.domain.model.product.ProductStatus;
import com.jcode.jcomercy.inventory.domain.model.product.ProductTrackingMode;
import com.jcode.jcomercy.inventory.domain.model.product.ProductUnitConversion;
import com.jcode.jcomercy.inventory.domain.model.unit.UnitOfMeasure;
import com.jcode.jcomercy.inventory.domain.port.ProductRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.UnitOfMeasureRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.ProductUnitConversionRepositoryPort;
import com.jcode.jcomercy.inventory.domain.port.CompanyUnitOfMeasureRepositoryPort;
import com.jcode.jcomercy.inventory.domain.model.unit.CompanyUnitOfMeasure;
import com.jcode.jcomercy.inventory.domain.model.unit.UnitDimension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.math.RoundingMode;

public class ProductApplicationService {
    private static final Logger log = LoggerFactory.getLogger(ProductApplicationService.class);
    private final ProductRepositoryPort products;
    private final UnitOfMeasureRepositoryPort units;
    private final ProductUnitConversionRepositoryPort conversions;
    private final CompanyUnitOfMeasureRepositoryPort companyUnits;

    public ProductApplicationService(ProductRepositoryPort products, UnitOfMeasureRepositoryPort units,
                                     ProductUnitConversionRepositoryPort conversions) {
        this(products, units, conversions, null);
    }

    public ProductApplicationService(ProductRepositoryPort products, UnitOfMeasureRepositoryPort units,
                                     ProductUnitConversionRepositoryPort conversions,
                                     CompanyUnitOfMeasureRepositoryPort companyUnits) {
        this.products = products;
        this.units = units;
        this.conversions = conversions;
        this.companyUnits = companyUnits;
    }

    @Transactional
    public Product create(CreateProductCommand command) {
        String sku = command.sku().trim().toUpperCase();
        log.info("Iniciando alta de producto companyId={} sku={} trackingMode={}", command.companyId(), sku, command.trackingMode());
        if (products.existsByCompanyIdAndSku(command.companyId(), sku)) {
            log.warn("Alta de producto rechazada por SKU duplicado companyId={} sku={}", command.companyId(), sku);
            throw new InventoryApplicationException("product sku already exists for company");
        }
        validateUnit(command.companyId(), command.stockUnitCode(), "stockUnitCode");
        validateOptionalUnit(command.companyId(), command.purchaseUnitCode(), "purchaseUnitCode");
        validateOptionalUnit(command.companyId(), command.saleUnitCode(), "saleUnitCode");
        validateOptionalUnit(command.companyId(), command.netQuantityUnitCode(), "netQuantityUnitCode");
        var product = new Product(UUID.randomUUID(), UUID.randomUUID(), command.companyId(), sku, command.name(),
                command.categoryId(), command.stockUnitCode(), command.purchaseUnitCode(), command.saleUnitCode(),
                command.netQuantity(), command.netQuantityUnitCode(), command.trackingMode(), command.allowNegativeStock(),
                ProductStatus.ACTIVE);
        var saved = products.save(product);
        log.info("Producto creado productId={} companyId={} sku={}", saved.publicProductId(), saved.companyId(), saved.sku());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Product> listByCompany(String companyId) {
        var result = products.findByCompanyId(companyId);
        log.info("Productos consultados companyId={} total={}", companyId, result.size());
        return result;
    }

    @Transactional(readOnly = true)
    public Product get(UUID productId) {
        return products.findById(productId).orElseThrow(() -> InventoryApplicationException.notFound("product not found"));
    }

    @Transactional(readOnly = true)
    public List<UnitOfMeasure> listUnits() {
        var result = units.findActive();
        log.info("Unidades de medida consultadas total={}", result.size());
        return result;
    }

    @Transactional(readOnly = true)
    public List<UnitCatalogItem> listUnits(String companyId) {
        var global = units.findActive().stream().map(unit -> new UnitCatalogItem(unit.code(), unit.name(), unit.symbol(),
                unit.dimension(), unit.baseUnitCode(), unit.conversionFactor(), unit.decimals(), true)).toList();
        var custom = companyUnits == null ? List.<UnitCatalogItem>of() : companyUnits.findActiveByCompanyId(companyId).stream()
                .map(unit -> new UnitCatalogItem(unit.code(), unit.name(), unit.symbol(), unit.dimension(), unit.baseUnitCode(),
                        unit.conversionFactor(), unit.decimals(), false)).toList();
        return java.util.stream.Stream.concat(global.stream(), custom.stream()).toList();
    }

    @Transactional
    public CompanyUnitOfMeasure createCompanyUnit(CreateCompanyUnitCommand command) {
        if (companyUnits == null) throw new InventoryApplicationException("company unit catalog is not available");
        if (companyUnits.existsByCompanyIdAndCode(command.companyId(), command.code()) || units.findByCode(command.code()).isPresent()) {
            throw new InventoryApplicationException("unit code already exists for company or system catalog");
        }
        var base = resolveUnit(command.companyId(), command.baseUnitCode(), "baseUnitCode");
        if (base.dimension() != command.dimension()) throw new InventoryApplicationException("base unit must belong to the same dimension");
        var saved = companyUnits.save(new CompanyUnitOfMeasure(UUID.randomUUID(), UUID.randomUUID(), command.companyId(),
                command.code(), command.name(), command.symbol(), command.dimension(), command.baseUnitCode(),
                command.conversionFactor(), command.decimals(), CompanyUnitOfMeasure.UnitStatus.ACTIVE));
        log.info("Unidad propia creada companyId={} code={} baseUnit={}", saved.companyId(), saved.code(), saved.baseUnitCode());
        return saved;
    }

    @Transactional
    public ProductUnitConversion addConversion(String companyId, UUID productId, AddConversionCommand command) {
        var product = get(productId);
        if (!product.companyId().equals(companyId)) throw InventoryApplicationException.notFound("product not found");
        var from = resolveUnit(companyId, command.fromUnitCode(), "fromUnitCode");
        var to = resolveUnit(companyId, command.toUnitCode(), "toUnitCode");
        if (from.dimension() != to.dimension()) {
            throw new InventoryApplicationException("conversion units must have the same dimension");
        }
        if (conversions.existsByProductIdAndFromUnitCodeAndToUnitCode(productId, command.fromUnitCode(), command.toUnitCode())) {
            throw new InventoryApplicationException("product unit conversion already exists");
        }
        var saved = conversions.save(new ProductUnitConversion(UUID.randomUUID(), productId, command.fromUnitCode(),
                command.toUnitCode(), command.factor(), command.roundingScale(), command.roundingMode(),
                command.purchaseUnit(), command.salesUnit(), command.defaultConversion(), ProductUnitConversion.ConversionStatus.ACTIVE));
        log.info("Conversión de producto creada productId={} from={} to={} factor={}", productId, saved.fromUnitCode(),
                saved.toUnitCode(), saved.factor());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ProductUnitConversion> listConversions(String companyId, UUID productId) {
        var product = get(productId);
        if (!product.companyId().equals(companyId)) throw InventoryApplicationException.notFound("product not found");
        var result = conversions.findByProductId(productId);
        log.info("Conversiones de producto consultadas productId={} total={}", productId, result.size());
        return result;
    }

    private void validateUnit(String companyId, String code, String field) {
        if (code == null || resolveUnitOrNull(companyId, code) == null) {
            log.warn("Alta de producto rechazada: unidad inexistente field={} code={}", field, code);
            throw new InventoryApplicationException(field + " does not reference an active unit");
        }
    }
    private ResolvedUnit resolveUnit(String companyId, String code, String field) {
        var result = resolveUnitOrNull(companyId, code);
        if (result == null) throw new InventoryApplicationException(field + " does not reference an active unit");
        return result;
    }
    private ResolvedUnit resolveUnitOrNull(String companyId, String code) {
        if (code == null || code.isBlank()) return null;
        var system = units.findByCode(code).map(unit -> new ResolvedUnit(unit.dimension()));
        if (system.isPresent()) return system.get();
        if (companyUnits == null) return null;
        return companyUnits.findActiveByCompanyIdAndCode(companyId, code).map(unit -> new ResolvedUnit(unit.dimension())).orElse(null);
    }
    private void validateOptionalUnit(String companyId, String code, String field) { if (code != null && !code.isBlank()) validateUnit(companyId, code, field); }

    public record CreateProductCommand(String companyId, String sku, String name, String categoryId,
                                       String stockUnitCode, String purchaseUnitCode, String saleUnitCode,
                                       BigDecimal netQuantity, String netQuantityUnitCode,
                                       ProductTrackingMode trackingMode, boolean allowNegativeStock) { }
    public record AddConversionCommand(String fromUnitCode, String toUnitCode, BigDecimal factor, int roundingScale,
                                       RoundingMode roundingMode, boolean purchaseUnit, boolean salesUnit,
                                       boolean defaultConversion) { }
    public record CreateCompanyUnitCommand(String companyId, String code, String name, String symbol,
                                           UnitDimension dimension, String baseUnitCode, BigDecimal conversionFactor,
                                           int decimals) { }
    public record UnitCatalogItem(String code, String name, String symbol, UnitDimension dimension, String baseUnitCode,
                                  BigDecimal conversionFactor, int decimals, boolean system) { }
    private record ResolvedUnit(UnitDimension dimension) { }
}
