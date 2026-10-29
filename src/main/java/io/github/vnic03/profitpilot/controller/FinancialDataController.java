package io.github.vnic03.profitpilot.controller;

import io.github.vnic03.profitpilot.catalog.StockCatalog;
import io.github.vnic03.profitpilot.database.DatabaseService;
import io.github.vnic03.profitpilot.model.StockChange;
import io.github.vnic03.profitpilot.sync.PriceFreshness;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance")
public class FinancialDataController {

    private final DatabaseService dbService;

    @Autowired
    public FinancialDataController(DatabaseService dbService) {
        this.dbService = dbService;
    }

    @GetMapping("/winnersAndLosers/{amount}")
    public ResponseEntity<Map<String, List<StockChange>>> getWinnersAndLosers(
            @PathVariable int amount) {
        return ResponseEntity.ok(dbService.getWinnersAndLosers(amount));
    }

    @GetMapping("/import-budget")
    public ResponseEntity<DatabaseService.ImportBudgetStatus> getImportBudget() {
        return ResponseEntity.ok(dbService.getImportBudgetStatus());
    }

    @GetMapping("/chart/{symbol}")
    public ResponseEntity<DatabaseService.ChartData> getChartData(@PathVariable String symbol) {
        return ResponseEntity.ok(dbService.getChartData(ApiSupport.requireSymbol(symbol)));
    }

    public record StockDataStatus(
            String symbol,
            long priceCount,
            LocalDate latestPriceDate,
            OffsetDateTime lastAttemptAt,
            OffsetDateTime lastSuccessAt,
            PriceFreshness.Status freshness,
            LocalDate referenceDate) {}

    @GetMapping("/data-status")
    public ResponseEntity<List<StockDataStatus>> getDataStatus() {
        Instant now = Instant.now();

        List<StockDataStatus> rows =
                dbService.getDataStatus(StockCatalog.symbols()).stream()
                        .map(
                                row -> {
                                    var assessment =
                                            PriceFreshness.assess(row.latestPriceDate(), now);

                                    return new StockDataStatus(
                                            row.symbol(),
                                            row.priceCount(),
                                            row.latestPriceDate(),
                                            row.lastAttemptAt(),
                                            row.lastSuccessAt(),
                                            assessment.status(),
                                            assessment.referenceDate());
                                })
                        .toList();

        return ResponseEntity.ok(rows);
    }
}
