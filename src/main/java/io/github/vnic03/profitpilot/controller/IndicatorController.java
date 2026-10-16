package io.github.vnic03.profitpilot.controller;

import java.util.Locale;
import java.util.Map;

import io.github.vnic03.profitpilot.catalog.StockCatalog;
import io.github.vnic03.profitpilot.database.DatabaseService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/finance")
public class IndicatorController {

    private static final Logger logger =
            LoggerFactory.getLogger(IndicatorController.class);

    private final DatabaseService database;

    public IndicatorController(DatabaseService database) {
        this.database = database;
    }

    public record IndicatorError(String code, String message) {
    }

    @GetMapping(
            "/{indicator:dynamicSMA|dynamicEMA|dynamicRSI}/{symbol}/{period}"
    )
    public ResponseEntity<?> getIndicator(
            @PathVariable("indicator") String indicator,
            @PathVariable("symbol") String symbol,
            @PathVariable("period") String period) {

        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);

        if (!StockCatalog.supports(normalizedSymbol)) {
            return error(
                    400,
                    "unsupported_symbol",
                    "This stock is not supported."
            );
        }

        int parsedPeriod;

        try {
            parsedPeriod = Integer.parseInt(period);
        } catch (NumberFormatException e) {
            return invalidPeriod();
        }

        if (parsedPeriod < 1 || parsedPeriod > 50) {
            return invalidPeriod();
        }

        try {
            Map<String, Double> values = switch (indicator) {
                case "dynamicSMA" -> database.getSMA(normalizedSymbol, parsedPeriod);
                case "dynamicEMA" -> database.getEMA(normalizedSymbol, parsedPeriod);
                case "dynamicRSI" -> database.getRSI(normalizedSymbol, parsedPeriod);
                default ->
                        throw new IllegalStateException(
                                "Unexpected indicator route."
                        );
            };

            return ResponseEntity.ok(values);

        } catch (IllegalStateException e) {
            logger.error(
                    "Could not load {} for {} with period {}.",
                    indicator,
                    normalizedSymbol,
                    parsedPeriod,
                    e
            );

            return error(
                    500,
                    "indicator_failed",
                    "Could not load indicator data. Please try again."
            );
        }
    }

    private ResponseEntity<IndicatorError> invalidPeriod() {
        return error(
                400,
                "invalid_period",
                "Period must be a whole number between 1 and 50."
        );
    }

    private ResponseEntity<IndicatorError> error(int status, String code, String message)
    {
        return ResponseEntity.status(status).body(new IndicatorError(code, message));
    }
}
