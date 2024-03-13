package org.example.profitpilot;

import org.example.profitpilot.api.alpha_vantage.ApiClient;
import org.example.profitpilot.database.DatabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

@Component
public class AppStartUpRunner implements CommandLineRunner {

    public static final List<String> SYMBOLS = Arrays.asList
            ("IBM", "AAPL", "GOOGL", "MSFT", "AMZN");

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
        Scanner scanner = new Scanner(System.in);
        System.out.print("Execute all API requests? (yes/no): ");
        String response = scanner.nextLine();

        if ("yes".equalsIgnoreCase(response)) {
            SYMBOLS.forEach(this::fetchSymbolData);

        } else if ("no".equalsIgnoreCase(response)) {
            System.out.println("nothing happens. . .");

        } else {
            System.out.println("Executing limited API requests. . .");
            fetchSymbolData(SYMBOLS.getFirst());
        }
    }

    private void fetchSymbolData(String symbol) {
        try {
            apiClient.fetchDailyTimeSeries(symbol, DEFAULT_SMA_PERIOD, DEFAULT_EMA_PERIOD, DEFAULT_RSI_PERIOD,
                    DEFAULT_LONG_PERIOD, DEFAULT_SHORT_PERIOD, DEFAULT_SIGNAL_PERIOD);
        } catch (Exception e) {
            System.err.println("Error loading data for: " + symbol);
        }
    }
}
