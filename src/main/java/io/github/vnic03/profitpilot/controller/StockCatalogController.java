package io.github.vnic03.profitpilot.controller;

import io.github.vnic03.profitpilot.catalog.StockCatalog;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance")
public class StockCatalogController {

    @GetMapping("/stocks")
    public List<StockCatalog.Stock> getStocks() {
        return StockCatalog.stocks();
    }
}
