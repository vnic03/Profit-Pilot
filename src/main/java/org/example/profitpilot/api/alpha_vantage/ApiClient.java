package org.example.profitpilot.api.alpha_vantage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.example.profitpilot.database.ARFFConverter;
import org.example.profitpilot.database.DatabaseService;
import org.example.profitpilot.database.MarketIndicators;
import org.example.profitpilot.feature_engineering.MarketIndicatorsCalculator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    public void fetchDailyTimeSeries
            (String symbol, int smaPeriod, int emaPeriod, int rsiPeriod,
             int longPeriod, int shortPeriod, int signalPeriod)
    {
        ApiResponse data = alphaVantageCache.getData(symbol);
        if (data == null) {
            logger.error("Failed to fetch data for symbol: {}", symbol);
            return;
        }
        List<Double> closePrices = new ArrayList<>();

        List<DatabaseService.SharePrice> sharePrices = new ArrayList<>();

        data.timeSeries().forEach((date, dailyData) -> {
            double open = dailyData.open();
            double high = dailyData.high();
            double low = dailyData.low();
            double close = dailyData.close();
            long volume = dailyData.volume();

            closePrices.add(close);
            sharePrices.add(new DatabaseService.SharePrice(symbol, date, open, high, low, close, volume));
        });
        dbService.insertSharePrice(sharePrices);

        if (closePrices.size() > 14) {
            double sma = MarketIndicatorsCalculator.calculateSMA(closePrices, smaPeriod);
            double rsi = MarketIndicatorsCalculator.calculateRSI(closePrices, rsiPeriod);
            double ema = MarketIndicatorsCalculator.calculateEMA(closePrices, emaPeriod);

            List<Double> macdValues = MarketIndicatorsCalculator.calculateMACD(closePrices, longPeriod, shortPeriod);
            double signalLine = MarketIndicatorsCalculator.calculateSignalLine(closePrices, signalPeriod);

            System.out.printf
                    ("SMA(14): %.2f%nRSI(14): %.2f%nEMA(14): %.2f%nMACD: %s%nSignal-line: %.2f%n",
                            sma, rsi, ema, macdValues, signalLine);

            int index = 0; // for MACD-values
            for (var entry : data.timeSeries().entrySet()) {
                String date = entry.getKey();
                double macd = macdValues.size() > index ? macdValues.get(index) : 0;
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

    @Configuration
    public static class AppConfig {
        @Bean
        public ApiClient apiClient(DatabaseService dbService, AlphaVantageCache alphaVantageCache) {
            return new ApiClient(dbService, alphaVantageCache);
        }
    }
}
