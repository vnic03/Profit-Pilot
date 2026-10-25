package io.github.vnic03.profitpilot.api.alphavantage;

import io.github.vnic03.profitpilot.catalog.StockCatalog;
import io.github.vnic03.profitpilot.database.ARFFConverter;
import io.github.vnic03.profitpilot.database.DatabaseService;
import io.github.vnic03.profitpilot.database.MarketIndicators;
import io.github.vnic03.profitpilot.sync.PriceFreshness;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class ApiClient {

  public enum SyncResult {
    IMPORTED,
    WAITING_FOR_DATA,
    SKIPPED,
    BUDGET_OR_COOLDOWN,
    DISABLED
  }

  private static final Logger logger = LoggerFactory.getLogger(ApiClient.class);

  private final DatabaseService dbService;
  private final AlphaVantageCache alphaVantageCache;

  private final Clock clock;

  public ApiClient(DatabaseService dbService, AlphaVantageCache alphaVantageCache) {
    this(dbService, alphaVantageCache, Clock.systemUTC());
  }

  public ApiClient(DatabaseService dbService, AlphaVantageCache alphaVantageCache, Clock clock) {
    this.dbService = dbService;
    this.alphaVantageCache = alphaVantageCache;
    this.clock = clock;
  }

  public SyncResult syncDailyPrices(String symbol) throws SQLException {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException();
    }

    String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);

    if (!StockCatalog.supports(normalizedSymbol)) {
      throw new IllegalArgumentException("Unsupported stock symbol: " + normalizedSymbol);
    }

    if (!alphaVantageCache.isExternalApiEnabled()) {
      logger.info("Skipped import for {}: External Api disabled", normalizedSymbol);
      return SyncResult.DISABLED;
    }

    var storedStatus = dbService.getDataStatus(List.of(normalizedSymbol)).stream().findFirst();

    var latestPriceDate =
        storedStatus.map(DatabaseService.DataStatus::latestPriceDate).orElse(null);

    var freshness = PriceFreshness.assess(latestPriceDate, clock.instant());

    if (freshness.status() == PriceFreshness.Status.FUTURE_DATE) {
      throw new IllegalStateException("Stored price date is in the future for " + normalizedSymbol);
    }

    if (freshness.status() == PriceFreshness.Status.CALENDAR_UNAVAILABLE) {
      throw new IllegalStateException(
          "Trading calendar is unavailable for the current date. "
              + "Update calendar coverage before importing "
              + normalizedSymbol);
    }

    // Check stored dates before reserving budget or contacting the provider.
    if (freshness.status() == PriceFreshness.Status.CURRENT) {
      logger.info(
          "Skipped import for {}: stored price date {} " + "meets trading-session reference {}.",
          normalizedSymbol,
          latestPriceDate,
          freshness.referenceDate());

      return SyncResult.SKIPPED;
    }

    if (!dbService.tryStartDailySync(normalizedSymbol)) {
      logger.info(
          "Skipped import for {}: daily attempt already recorded " + "or request budget exhausted",
          normalizedSymbol);
      return SyncResult.BUDGET_OR_COOLDOWN;
    }

    try {
      ApiResponse data = alphaVantageCache.fetchData(normalizedSymbol);

      if (data == null || data.timeSeries() == null || data.timeSeries().isEmpty()) {
        throw new IllegalStateException("No valid data for: " + normalizedSymbol);
      }

      LocalDate latestResponseDate =
          data.timeSeries().keySet().stream()
              .map(LocalDate::parse)
              .max(LocalDate::compareTo)
              .orElseThrow(
                  () -> new IllegalStateException("Provider response contains no price dates."));

      var responseAssessment = PriceFreshness.assess(latestResponseDate, clock.instant());

      if (responseAssessment.status() == PriceFreshness.Status.FUTURE_DATE) {
        throw new IllegalStateException(
            "Provider returned a future price date for "
                + normalizedSymbol
                + ": "
                + latestResponseDate);
      }

      List<DatabaseService.SharePrice> sharePrices = new ArrayList<>();

      data.timeSeries()
          .forEach(
              (date, dailyData) ->
                  sharePrices.add(
                      new DatabaseService.SharePrice(
                          normalizedSymbol,
                          date,
                          dailyData.open(),
                          dailyData.high(),
                          dailyData.low(),
                          dailyData.close(),
                          dailyData.volume())));

      dbService.insertSharePrice(sharePrices);

      // Valid prices are worth saving even if the provider is still behind.
      // Leave the stock eligible for a later, budget-limited retry.
      if (latestResponseDate.isBefore(freshness.referenceDate())) {
        dbService.markDailySyncFailed(normalizedSymbol);

        logger.info(
            "Waiting for current provider data for {}: "
                + "latest response date {}, required reference {}. "
                + "Received prices were saved. "
                + "A later run may retry within the existing limits.",
            normalizedSymbol,
            latestResponseDate,
            freshness.referenceDate());

        return SyncResult.WAITING_FOR_DATA;
      }

      dbService.markDailySyncSuccessful(normalizedSymbol);

      logger.info("Import succeeded for {}: {} records.", normalizedSymbol, sharePrices.size());

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

  public void exportFinancialDataToArff(String symbol, String outputPath) {
    List<MarketIndicators> dataList = dbService.queryFinancialDataForSymbol(symbol);

    ARFFConverter.convert(dataList, outputPath);
  }

  @Configuration
  public static class AppConfig {

    @Bean
    public ApiClient apiClient(DatabaseService dbService, AlphaVantageCache alphaVantageCache) {
      return new ApiClient(dbService, alphaVantageCache);
    }
  }

  public boolean isImportEnabled() {
    return alphaVantageCache.isExternalApiEnabled();
  }
}
