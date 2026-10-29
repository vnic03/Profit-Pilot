package io.github.vnic03.profitpilot.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.vnic03.profitpilot.catalog.StockCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StockCatalogControllerTest {

    @Test
    void exposesStockMetadataWithoutDatabaseOrProvider() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new StockCatalogController()).build();

        mvc.perform(get("/api/finance/stocks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].symbol").value("IBM"))
                .andExpect(jsonPath("$[0].name").value("International Business Machines"))
                .andExpect(jsonPath("$[0].exchange").value("NYSE"))
                .andExpect(jsonPath("$[0].currency").value("USD"));
    }

    @Test
    void supportsNormalizedSymbolsAndRejectsUnknownValues() {
        assertTrue(StockCatalog.supports(" ibm "));
        assertTrue(StockCatalog.supports("aapl"));

        assertFalse(StockCatalog.supports("UNKNOWN"));
        assertFalse(StockCatalog.supports(""));
        assertFalse(StockCatalog.supports(null));
    }

    @Test
    void catalogContainsUniqueSymbolsAndCompleteMetadata() {
        var stocks = StockCatalog.stocks();

        assertFalse(stocks.isEmpty());

        assertEquals(
                stocks.size(), stocks.stream().map(StockCatalog.Stock::symbol).distinct().count());

        for (var stock : stocks) {
            assertFalse(stock.symbol().isBlank());
            assertFalse(stock.name().isBlank());
            assertFalse(stock.exchange().isBlank());
            assertFalse(stock.currency().isBlank());

            assertEquals(stock.symbol(), StockCatalog.normalize(stock.symbol()));
        }
    }
}
