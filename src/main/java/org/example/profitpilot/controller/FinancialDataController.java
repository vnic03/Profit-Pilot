package org.example.profitpilot.controller;

import org.example.profitpilot.api.alpha_vantage.StockChange;
import org.example.profitpilot.database.DatabaseService;
import org.example.profitpilot.feature_engineering.MarketIndicatorsCalculator;
import org.example.profitpilot.catalog.StockCatalog;
import org.example.profitpilot.sync.PriceFreshness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;


@RestController
@RequestMapping("/api/finance")
public class FinancialDataController {

    private final DatabaseService dbService;

    @Autowired
    public FinancialDataController(DatabaseService dbService) {
        this.dbService = dbService;
    }

    @GetMapping("/high/{symbol}")
    public ResponseEntity<Map<String, Double>> getHigh(@PathVariable String symbol) {
        return ResponseEntity.ok(dbService.getHigh(symbol));
    }

    @GetMapping("/low/{symbol}")
    public ResponseEntity<Map<String, Double>> getLow(@PathVariable String symbol) {
        return ResponseEntity.ok(dbService.getLow(symbol));
    }

    @GetMapping("/open/{symbol}")
    public ResponseEntity<Map<String, Double>> getOpen(@PathVariable String symbol) {
        return ResponseEntity.ok(dbService.getOpen(symbol));
    }

    @GetMapping("/volume/{symbol}")
    public ResponseEntity<Map<String, Long>> getVolume(@PathVariable String symbol) {
        return ResponseEntity.ok(dbService.getVolume(symbol));
    }

    @GetMapping("/prices/{symbol}")
    public ResponseEntity<Map<String, Double>> getClosePrices(@PathVariable String symbol) {
        return ResponseEntity.ok(dbService.getClosePrices(symbol));
    }

    @GetMapping("/macd/{symbol}")
    public ResponseEntity<Map<String, Double>> getMACD(@PathVariable String symbol) {
        return ResponseEntity.ok(dbService.getMACD(symbol));
    }

    @GetMapping("/winnersAndLosers/{amount}")
    public ResponseEntity<Map<String, List<StockChange>>> getWinnersAndLosers(@PathVariable int amount) {
        Map<String, List<StockChange>> winnersAndLosers = dbService.getWinnersAndLosers(amount);
        if (winnersAndLosers.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(winnersAndLosers);
    }

    @GetMapping("/macd-series/{symbol}")
    public ResponseEntity<MarketIndicatorsCalculator.MacdResult> getMacdSeries(
            @PathVariable String symbol) {

        return ResponseEntity.ok(dbService.getMacdSeries(symbol));
    }

    @GetMapping("/import-budget")
    public ResponseEntity<DatabaseService.ImportBudgetStatus> getImportBudget() {
        return ResponseEntity.ok(dbService.getImportBudgetStatus());
    }

    public record StockDataStatus(
            String symbol,
            long priceCount,
            LocalDate latestPriceDate,
            OffsetDateTime lastAttemptAt,
            OffsetDateTime lastSuccessAt,
            PriceFreshness.Status freshness,
            LocalDate referenceDate)
    { }

    @GetMapping("/data-status")
    public ResponseEntity<List<StockDataStatus>> getDataStatus() {
        Instant now = Instant.now();

        List<StockDataStatus> rows = dbService
                .getDataStatus(StockCatalog.symbols())
                .stream()
                .map(row -> {
                    var assessment = PriceFreshness.assess(
                            row.latestPriceDate(),
                            now
                    );

                    return new StockDataStatus(
                            row.symbol(),
                            row.priceCount(),
                            row.latestPriceDate(),
                            row.lastAttemptAt(),
                            row.lastSuccessAt(),
                            assessment.status(),
                            assessment.referenceDate()
                    );
                })
                .toList();

        return ResponseEntity.ok(rows);
    }
}
