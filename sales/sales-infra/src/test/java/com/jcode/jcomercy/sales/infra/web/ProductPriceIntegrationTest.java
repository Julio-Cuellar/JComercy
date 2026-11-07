package com.jcode.jcomercy.sales.infra.web;

import com.jcode.jcomercy.sales.infra.SalesTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El precio de venta de cada producto: quién lo fija, quién lo consulta y que cada empresa vea solo los suyos. */
@SpringBootTest
@AutoConfigureMockMvc
class ProductPriceIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private String company;
    private String manager;
    private String reader;

    @BeforeEach
    void aCompanyOfItsOwn() {
        company = "cmp_price_" + UUID.randomUUID();
        manager = SalesTokens.bearer(company, List.of("br_1"), "sales.prices.write sales.read");
        reader = SalesTokens.bearer(company, List.of("br_1"), "sales.read");
    }

    @Test
    @DisplayName("Quien tiene sales.prices.write fija el precio, y quien tiene sales.read lo ve en la lista")
    void aManagerSetsThePriceAndAReaderSeesIt() throws Exception {
        UUID product = UUID.randomUUID();

        setPrice(manager, product, "{\"amount\":45.5,\"currency\":\"mxn\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(product.toString()))
            .andExpect(jsonPath("$.amount").value(45.5))
            .andExpect(jsonPath("$.currency").value("MXN"))
            .andExpect(jsonPath("$.updatedAt").exists());

        mockMvc.perform(get("/api/v1/companies/{c}/prices", company).header("Authorization", reader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].productId").value(product.toString()))
            .andExpect(jsonPath("$[0].amount").value(45.5));
    }

    @Test
    @DisplayName("Fijar el precio otra vez lo reemplaza")
    void settingThePriceAgainReplacesIt() throws Exception {
        UUID product = UUID.randomUUID();
        setPrice(manager, product, "{\"amount\":45.50,\"currency\":\"MXN\"}").andExpect(status().isOk());

        setPrice(manager, product, "{\"amount\":50,\"currency\":\"MXN\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/companies/{c}/prices", company).header("Authorization", reader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].amount").value(50.0));
    }

    @Test
    @DisplayName("Quien solo consulta no puede fijar precios")
    void aReaderCannotSetPrices() throws Exception {
        setPrice(reader, UUID.randomUUID(), "{\"amount\":45.50,\"currency\":\"MXN\"}").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sin sales.read no se ve la lista de precios")
    void withoutSalesReadThePriceListIsForbidden() throws Exception {
        String nobody = SalesTokens.bearer(company, List.of("br_1"), "sales.create");

        mockMvc.perform(get("/api/v1/companies/{c}/prices", company).header("Authorization", nobody))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un token de otra empresa no fija ni ve los precios de esta")
    void anotherCompanyTokenCannotTouchThesePrices() throws Exception {
        String other = SalesTokens.bearer("cmp_otra_" + UUID.randomUUID(), List.of("br_1"), "sales.*");

        setPrice(other, UUID.randomUUID(), "{\"amount\":45.50,\"currency\":\"MXN\"}").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/companies/{c}/prices", company).header("Authorization", other))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cada empresa ve solo sus precios aunque fije el mismo producto")
    void eachCompanySeesOnlyItsOwnPrices() throws Exception {
        UUID product = UUID.randomUUID();
        String otherCompany = "cmp_price_" + UUID.randomUUID();
        String otherManager = SalesTokens.bearer(otherCompany, List.of("br_1"), "sales.*");
        setPrice(manager, product, "{\"amount\":45.50,\"currency\":\"MXN\"}").andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/companies/{c}/products/{p}/price", otherCompany, product).header("Authorization", otherManager)
            .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":99.00,\"currency\":\"MXN\"}")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/companies/{c}/prices", company).header("Authorization", reader))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].amount").value(45.5));
    }

    @Test
    @DisplayName("Un precio negativo, una moneda mal escrita o un cuerpo sin importe responden 400 con qué corregir")
    void invalidPricesAreRejectedWithWhatToFix() throws Exception {
        UUID product = UUID.randomUUID();

        setPrice(manager, product, "{\"amount\":-1,\"currency\":\"MXN\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        setPrice(manager, product, "{\"amount\":10,\"currency\":\"PESOS\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        setPrice(manager, product, "{\"currency\":\"MXN\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        setPrice(manager, product, "no es json")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private ResultActions setPrice(String token, UUID product, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/companies/{c}/products/{p}/price", company, product).header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
