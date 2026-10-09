package org.example.profitpilot.api.alpha_vantage;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.example.profitpilot.database.ARFFConverter;
import org.example.profitpilot.database.DatabaseService;
import org.example.profitpilot.database.MarketIndicators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


public class ApiClient {

    private static final Logger logger =
            LoggerFactory.getLogger(ApiClient.class);

    private final DatabaseService dbService;
    private final AlphaVantageCache alphaVantageCache;

    public ApiClient( DatabaseService dbService,
            AlphaVantageCache alphaVantageCache)
    {
        this.dbService = dbService;
        this.alphaVantageCache = alphaVantageCache;
    }

    public void syncDailyPrices(String symbol) throws SQLException {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException();
        }

        String normalizedSymbol =
                symbol.trim().toUpperCase(Locale.ROOT);

        if (!alphaVantageCache.isExternalApiEnabled()) {
            logger.info(
                    "Skipped import for {}: External Api disabled",
                    normalizedSymbol
            );
            return;
        }

        if (!dbService.tryStartDailySync(normalizedSymbol)) {
            logger.info(
                    "Skipped import for {}: Tried already today",
                    normalizedSymbol
            );
            return;
        }


        ApiResponse data = alphaVantageCache.fetchData(normalizedSymbol);

        if (data == null || data.timeSeries() == null
                || data.timeSeries().isEmpty())
        {
            throw new IllegalStateException(
                    "No valid data for: " + normalizedSymbol
            );
        }

        List<DatabaseService.SharePrice> sharePrices =
                new ArrayList<>();

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
                "Succes for {}: {} datasets",
                normalizedSymbol,
                sharePrices.size()
        );
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
}
