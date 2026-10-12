package org.example.profitpilot.api.alpha_vantage;

import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.example.profitpilot.database.ARFFConverter;
import org.example.profitpilot.database.DatabaseService;
import org.example.profitpilot.database.MarketIndicators;
import org.example.profitpilot.sync.PriceFreshness;
import org.example.profitpilot.catalog.StockCatalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


public class ApiClient {

    public enum SyncResult {
        IMPORTED,
        SKIPPED,
        DISABLED
    }

    private static final Logger logger =
            LoggerFactory.getLogger(ApiClient.class);

    private final DatabaseService dbService;
    private final AlphaVantageCache alphaVantageCache;

    private final Clock clock;

    public ApiClient( DatabaseService dbService,
            AlphaVantageCache alphaVantageCache)
    {
        this(dbService, alphaVantageCache, Clock.systemUTC());
    }

    public ApiClient(
            DatabaseService dbService,
            AlphaVantageCache alphaVantageCache,
            Clock clock)
    {
        this.dbService = dbService;
        this.alphaVantageCache = alphaVantageCache;
        this.clock = clock;
    }

    public SyncResult syncDailyPrices(String symbol) throws SQLException {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException();
        }

        String normalizedSymbol =
                symbol.trim().toUpperCase(Locale.ROOT);

        if (!StockCatalog.supports(normalizedSymbol)) {
            throw new IllegalArgumentException(
                    "Unsupported stock symbol: " + normalizedSymbol
            );
        }

        if (!alphaVantageCache.isExternalApiEnabled()) {
            logger.info(
                    "Skipped import for {}: External Api disabled",
                    normalizedSymbol
            );
            return SyncResult.DISABLED;
        }

        var storedStatus = dbService
                .getDataStatus(List.of(normalizedSymbol))
                .stream()
                .findFirst();

        var latestPriceDate = storedStatus
                .map(DatabaseService.DataStatus::latestPriceDate)
                .orElse(null);

        var freshness = PriceFreshness.assess(
                latestPriceDate,
                clock.instant()
        );

        if (freshness.status() == PriceFreshness.Status.FUTURE_DATE) {
            throw new IllegalStateException(
                    "Stored price date is in the future for "
                            + normalizedSymbol
            );
        }

        if (freshness.status() == PriceFreshness.Status.CALENDAR_UNAVAILABLE) {
            throw new IllegalStateException(
                    "Trading calendar is unavailable for the current date. "
                            + "Update calendar coverage before importing "
                            + normalizedSymbol
            );
        }

        if (freshness.status() == PriceFreshness.Status.CURRENT) {
            logger.info(
                    "Skipped import for {}: stored price date {} "
                            + "meets trading-session reference {}.",
                    normalizedSymbol,
                    latestPriceDate,
                    freshness.referenceDate()
            );

            return SyncResult.SKIPPED;
        }

        if (!dbService.tryStartDailySync(normalizedSymbol)) {
            logger.info(
                    "Skipped import for {}: daily attempt already recorded "
                        + "or request budget exhausted",
                    normalizedSymbol
            );
            return SyncResult.SKIPPED;
        }

        try {
            ApiResponse data = alphaVantageCache.fetchData(normalizedSymbol);

            if (data == null
                    || data.timeSeries() == null
                    || data.timeSeries().isEmpty()) {
                throw new IllegalStateException(
                        "No valid data for: " + normalizedSymbol
                );
            }

            List<DatabaseService.SharePrice> sharePrices = new ArrayList<>();

            data.timeSeries().forEach((date, dailyData) ->
                    sharePrices.add(new DatabaseService.SharePrice(
                            normalizedSymbol,
                            date,
                            dailyData.open(),
                            dailyData.high(),
                            dailyData.low(),
                            dailyData.close(),
                            dailyData.volume()
                    ))
            );

            dbService.insertSharePrice(sharePrices);
            dbService.markDailySyncSuccessful(normalizedSymbol);

            logger.info(
                    "Import succeeded for {}: {} records.",
                    normalizedSymbol,
                    sharePrices.size()
            );

            return SyncResult.IMPORTED;

        } catch (SQLException | RuntimeException failure) {
            try {
                dbService.markDailySyncFailed(normalizedSymbol);
            } catch (SQLException | RuntimeException recordingFailure) {
                failure.addSuppressed(recordingFailure);
            }

            throw failure;
        }
    }

    public void exportFinancialDataToArff(
            String symbol,
            String outputPath) {
        List<MarketIndicators> dataList =
                dbService.queryFinancialDataForSymbol(symbol);

        ARFFConverter.convert(dataList, outputPath);
    }

    @Configuration
    public static class AppConfig {

        @Bean
        public ApiClient apiClient(
                DatabaseService dbService,
                AlphaVantageCache alphaVantageCache) {
            return new ApiClient(dbService, alphaVantageCache);
        }
    }

    public boolean isImportEnabled() {
        return alphaVantageCache.isExternalApiEnabled();
    }
}
