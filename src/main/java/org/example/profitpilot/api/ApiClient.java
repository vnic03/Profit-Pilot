package org.example.profitpilot.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.cdimascio.dotenv.Dotenv;
import org.example.profitpilot.database.ARFFConverter;
import org.example.profitpilot.database.DatabaseService;
import org.example.profitpilot.database.MarketIndicators;
import org.example.profitpilot.feature_engineering.MarketIndicatorsCalculator;
import org.example.profitpilot.visuals.Prototype;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiClient {

    private final DatabaseService dbService;
    private final AlphaVantageCache alphaVantageCache;
    private final Logger logger = LoggerFactory.getLogger(ApiClient.class);

    public ApiClient(DatabaseService dbService, AlphaVantageCache alphaVantageCache) {
        this.dbService = dbService;
        this.alphaVantageCache = alphaVantageCache;
    }

    public void fetchDailyTimeSeries(String symbol) {
        ApiResponse data = alphaVantageCache.getData(symbol);
        if (data == null) {
            logger.error("Failed to fetch data for symbol: {}", symbol);
            return;
        }
        List<Double> closePrices = new ArrayList<>();

        data.timeSeries().forEach((date, dailyData) -> {
            double open = dailyData.open();
            double high = dailyData.high();
            double low = dailyData.low();
            double close = dailyData.close();
            long volume = dailyData.volume();

            closePrices.add(close);
            dbService.insertSharePrice(symbol, date, open, high, low, close, volume);
        });

        if (closePrices.size() > 14) {
            double sma = MarketIndicatorsCalculator.calculateSMA(closePrices, 14);
            double rsi = MarketIndicatorsCalculator.calculateRSI(closePrices, 14);
            double ema = MarketIndicatorsCalculator.calculateEMA(closePrices, 14);

            int longPeriod = 26;
            int shortPeriod = 12;
            int signalPeriod = 9;

            List<Double> macdValues = MarketIndicatorsCalculator.calculateMACD(closePrices, longPeriod, shortPeriod);
            double signalLine = MarketIndicatorsCalculator.calculateSignalLine(closePrices, signalPeriod);

            System.out.printf
                    ("SMA(14): %.2f%nRSI(14): %.2f%nEMA(14): %.2f%nMACD: %s%nSignal-line: %.2f%n",
                            sma, rsi, ema, macdValues, signalLine);

            int index = 0; // for MACD-values
            for (var entry : data.timeSeries().entrySet()) {
                String date = entry.getKey();
                double macd = macdValues.size() > index ? macdValues.get(index) : 0; // Sicherstellen, dass Index im Bereich ist
                dbService.insertFinancialIndicator(symbol, date, sma, rsi, ema, macd, signalLine);
                index++;
            }
        }
    }

    private int getIndexForDate(String date, Set<String> dates) {
        List<String> dateList = new ArrayList<>(dates);
        return dateList.indexOf(date);
    }

    public void exportFinancialDataToArff(String symbol, String outputPath) {
        List<MarketIndicators> dataList = dbService.queryFinancialDataForSymbol(symbol);
        ARFFConverter.convert(dataList, outputPath);
    }

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(dotenv.get("DRIVER_CLASSNAME"));
        dataSource.setUrl(dotenv.get("DATA_SOURCE_URL"));
        dataSource.setUsername(dotenv.get("DATA_SOURCE_USER"));
        dataSource.setPassword(dotenv.get("DATA_SOURCE_PASSWORD"));

        DatabaseService dbService = new DatabaseService(dataSource);
        AlphaVantageCache alphaVantageCache = new AlphaVantageCache();
        ApiClient client = new ApiClient(dbService, alphaVantageCache);
        client.fetchDailyTimeSeries("IBM");

        // String outputPath = "financialdata.arff";
        // client.exportFinancialDataToArff("IBM", outputPath);

        Prototype chart = new Prototype(dbService);
        chart.createChart("IBM");
    }
    @Configuration
    public static class AppConfig {
        @Bean
        public ApiClient apiClient(DatabaseService dbService, AlphaVantageCache alphaVantageCache) {
            return new ApiClient(dbService, alphaVantageCache);
        }
    }
}
