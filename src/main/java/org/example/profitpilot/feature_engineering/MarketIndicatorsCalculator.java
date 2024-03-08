package org.example.profitpilot.feature_engineering;

import java.util.*;

public class MarketIndicatorsCalculator {

    public static Map<String, Double> SMA(Map<String, Double> pricesAndDates, int period) {
        Map<String, Double> sma = new LinkedHashMap<>();
        List<String> dates = new ArrayList<>(pricesAndDates.keySet());
        List<Double> prices = new ArrayList<>(pricesAndDates.values());

        if (prices.size() < period) {
            return sma;
        }

        for (int i = period - 1; i < dates.size(); i++) {
            double sum = 0.0;

            for (int j = i - period + 1; j <= i; j++) {
                sum += prices.get(j);
            }
            double average = sum / period;

            sma.put(dates.get(i), average);
        }
        return sma;
    }

    public static Map<String, Double> RSI(Map<String, Double> pricesAndDates, int period) {
        Map<String, Double> rsi = new LinkedHashMap<>();
        List<String> dates = new ArrayList<>(pricesAndDates.keySet());
        List<Double> prices = new ArrayList<>(pricesAndDates.values());

        if (prices.size() < period) {
            return rsi;
        }

        double averageGain = 0; double averageLoss = 0;

        for (int i = 1; i <= period; i++) {
            double difference = prices.get(i) - prices.get(i - 1);
            if (difference > 0) averageGain += difference / period;
            else averageLoss -= difference / period;
        }

        double rs = averageGain / Math.max(averageLoss, 1);

        rsi.put(dates.get(period), 100 - (100 / (1 + rs)));

        for (int i = period + 1; i < dates.size(); i++) {
            double difference = prices.get(i) - prices.get(i - 1);
            if (difference > 0) {
                averageGain = (averageGain * (period - 1) + difference) / period;
                averageLoss = (averageLoss * (period - 1)) / period;
            } else {
                averageGain = (averageGain * (period - 1)) / period;
                averageLoss = (averageLoss * (period - 1) - difference) / period;
            }

            rs = averageGain / Math.max(averageLoss, 1);

            rsi.put(dates.get(i), 100 - (100 / (1 + rs)));
        }
        return rsi;
    }

    public static Map<String, Double> EMA(Map<String, Double> pricesAndDates, int period) {
        Map<String, Double> ema = new LinkedHashMap<>();
        List<String> dates = new ArrayList<>(pricesAndDates.keySet());
        List<Double> prices = new ArrayList<>(pricesAndDates.values());

        if (prices.size() < period) {
            return ema;
        }

        double result = prices.getFirst();
        double multiplier = 2.0 / (period + 1);

        for (int i = 1; i < dates.size(); i++) {
            result = (prices.get(i) - result) * multiplier + result;
            ema.put(dates.get(i), result);
        }

        return ema;
    }

    public static double calculateSMA(List<Double> prices, int period) {
        if (!hasEnoughData(prices, period)) {
            return -1;
        }
        return prices.subList(prices.size() - period, prices.size()).stream()
                .mapToDouble(d -> d).average().orElse(-1);
    }

    public static double calculateRSI(List<Double> prices, int period) {
        if (!hasEnoughData(prices, period)) {
            return -1;
        }
        double averageGain = 0, averageLoss = 0;

        for (int i = prices.size() - period; i < prices.size() - 1; i++) {
            double difference = prices.get(i + 1) - prices.get(i);
            if (difference > 0) {
                averageGain += difference;
            } else {
                averageLoss -= difference;
            }
        }
        averageGain /= period;
        averageLoss /= period;

        if (averageLoss == 0) return 100;
        double rs = averageGain / averageLoss;

        return 100 - (100 / (1 + rs));
    }

    public static double calculateEMA(List<Double> prices, int period) {
        if (!hasEnoughData(prices, period)) {
            return -1;
        }
        double multiplier = 2.0 / (period + 1);
        double ema = prices.get(prices.size() - period);

        for (int i = prices.size() - period + 1; i < prices.size(); i++) {
            ema = (prices.get(i) - ema) * multiplier + ema;
        }
        return ema;
    }

    public static List<Double> calculateMACD
            (List<Double> prices, int longPeriod, int shortPeriod)
    {
        List<Double> longEmaValues = calculateEMAValues(prices, longPeriod);
        List<Double> shortEmaValues = calculateEMAValues(prices, shortPeriod);

        List<Double> macdValues = new ArrayList<>();

        for (int i = 0; i < prices.size(); i++) {
            if (longEmaValues.get(i) != null && shortEmaValues.get(i) != null) {
                double macd = shortEmaValues.get(i) - longEmaValues.get(i);
                macdValues.add(macd);
            } else {
                macdValues.add(null);
            }
        }
        return macdValues;
    }

    private static List<Double> calculateEMAValues(List<Double> prices, int period) {
        List<Double> emaValues = new ArrayList<>();
        double multiplier = 2.0 / (period + 1);
        double ema = 0;

        if (prices.size() >= period) {
            double sum = 0;
            for (int i = 0; i < period; i++) {
                sum += prices.get(i);
            }
            ema = sum / period;
        }
        for (int i = 0; i < period - 1; i++) {
            emaValues.add(ema);
        }
        for (int i = period - 1; i < prices.size(); i++) {
            ema = (prices.get(i) - ema) * multiplier + ema;
            emaValues.add(ema);
        }
        return emaValues;
    }

    public static double calculateSignalLine(List<Double> macdValues, int signalPeriod) {
        return calculateEMA(macdValues, signalPeriod);
    }

    private static boolean hasEnoughData(List<Double> prices, int period) {
        return prices.size() >= period;
    }
}
