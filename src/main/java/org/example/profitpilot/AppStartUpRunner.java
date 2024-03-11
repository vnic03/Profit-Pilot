package org.example.profitpilot;

import org.example.profitpilot.api.ApiClient;
import org.example.profitpilot.database.DatabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class AppStartUpRunner implements CommandLineRunner {

    private static final List<String> SYMBOLS_TO_PRELOAD =
            Arrays.asList("IBM", "AAPL", "GOOGL", "MSFT", "AMZN");

    private static final int DEFAULT_SMA_PERIOD = 14;
    private static final int DEFAULT_EMA_PERIOD = 14;
    private static final int DEFAULT_RSI_PERIOD = 14;
    private static final int DEFAULT_LONG_PERIOD = 26;
    private static final int DEFAULT_SHORT_PERIOD = 12;
    private static final int DEFAULT_SIGNAL_PERIOD = 9;

    private final DatabaseService dbService;
    private final ApiClient apiClient;

    @Autowired
    public AppStartUpRunner(DatabaseService dbService, ApiClient apiClient) {
        this.dbService = dbService;
        this.apiClient = apiClient;
    }

    @Override
    public void run(String... args) {
        SYMBOLS_TO_PRELOAD.forEach(symbol -> {
            try {
                apiClient.fetchDailyTimeSeries
                        (symbol, DEFAULT_SMA_PERIOD, DEFAULT_EMA_PERIOD, DEFAULT_RSI_PERIOD,
                                DEFAULT_LONG_PERIOD, DEFAULT_SHORT_PERIOD, DEFAULT_SIGNAL_PERIOD);
            } catch (Exception e) {
                System.err.println("Error loading data for: " + symbol);
            }
        });
    }
}
