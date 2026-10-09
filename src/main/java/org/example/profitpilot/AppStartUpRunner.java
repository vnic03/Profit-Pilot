package org.example.profitpilot;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.example.profitpilot.api.alpha_vantage.ApiClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AppStartUpRunner implements CommandLineRunner {

    private static final Logger logger =
            LoggerFactory.getLogger(AppStartUpRunner.class);

    public static final List<String> SYMBOLS =
            List.of("IBM", "AAPL", "GOOGL", "MSFT", "AMZN");

    private final ApiClient apiClient;
    private final boolean syncOnStartup;
    private final String configuredSymbols;

    public AppStartUpRunner(
            ApiClient apiClient,
            @Value("${profitpilot.sync.on-startup:false}")
            boolean syncOnStartup,
            @Value("${profitpilot.sync.symbols:${profitpilot.sync.symbol:}}")
            String configuredSymbols) {

        this.apiClient = apiClient;
        this.syncOnStartup = syncOnStartup;
        this.configuredSymbols = configuredSymbols;
    }

    @Override
    public void run(String... args) throws SQLException, InterruptedException {
        if (!syncOnStartup) {
            logger.info("Startup price import is disabled.");
            return;
        }

        List<String> requestedSymbols =
                Arrays.stream(configuredSymbols.split(",", -1))
                        .map(String::trim)
                        .map(symbol -> symbol.toUpperCase(Locale.ROOT))
                        .distinct()
                        .toList();

        for (String symbol : requestedSymbols) {
            if (!SYMBOLS.contains(symbol)) {
                throw new IllegalArgumentException(
                        "Unsupported sync symbol: " + symbol
                                + ". Supported symbols: " + SYMBOLS
                );
            }
        }

        logger.info("Starting price sync checks for {}.", requestedSymbols);

        for (int index = 0; index < requestedSymbols.size(); index++) {
            if (index > 0) {
              try {
                Thread.sleep(1500);
              } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw e;
              }
            }

            String symbol = requestedSymbols.get(index);

            logger.info("Checking daily price sync for {}.", symbol);
            apiClient.syncDailyPrices(symbol);
        }

        logger.info("Requested price sync checks completed.");
    }
}
