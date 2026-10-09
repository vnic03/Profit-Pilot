package org.example.profitpilot.feature_engineering;

import java.util.*;

public class MarketIndicatorsCalculator {


    public static Map<String, Double> SMA(Map<String, Double> pricesAndDates,
        int period)
    {

        if (period < 1) {
            throw new IllegalArgumentException("Period must be positive.");
        }

        Objects.requireNonNull(pricesAndDates, "Prices must not be null.");

        List<Map.Entry<String, Double>> entries =
                new ArrayList<>(new TreeMap<>(pricesAndDates).entrySet());

        for (Map.Entry<String, Double> entry : entries) {
            Double price = entry.getValue();

            if (price == null || !Double.isFinite(price)) {
                throw new IllegalArgumentException(
                        "Price must be finite for date: " + entry.getKey()
                );
            }
        }

        Map<String, Double> result = new LinkedHashMap<>();
        double sum = 0.0;

        for (int index = 0; index < entries.size(); index++) {
            sum += entries.get(index).getValue();

            if (index >= period) {
                sum -= entries.get(index - period).getValue();
            }

            if (index >= period - 1) {
                result.put(entries.get(index).getKey(), sum / period);
            }
        }

        return result;
    }


    public static Map<String, Double> RSI(Map<String, Double> pricesAndDates,
        int period)
    {

        List<Map.Entry<String, Double>> entries =
                preparePrices(pricesAndDates, period);

        Map<String, Double> result = new LinkedHashMap<>();

        if (entries.size() <= period) {
            return result;
        }

        double averageGain = 0.0;
        double averageLoss = 0.0;

        for (int index = 1; index <= period; index++) {
            double change = entries.get(index).getValue()
                    - entries.get(index - 1).getValue();

            averageGain += Math.max(change, 0.0);
            averageLoss += Math.max(-change, 0.0);
        }

        averageGain /= period;
        averageLoss /= period;

        result.put(
                entries.get(period).getKey(),
                rsiValue(averageGain, averageLoss)
        );

        for (int index = period + 1; index < entries.size(); index++) {
            double change = entries.get(index).getValue()
                    - entries.get(index - 1).getValue();

            averageGain = (
                    averageGain * (period - 1) + Math.max(change, 0.0)
            ) / period;

            averageLoss = (
                    averageLoss * (period - 1) + Math.max(-change, 0.0)
            ) / period;

            result.put(
                    entries.get(index).getKey(),
                    rsiValue(averageGain, averageLoss)
            );
        }

        return result;
    }

    public static Map<String, Double> EMA(Map<String, Double> pricesAndDates,
        int period)
    {

        List<Map.Entry<String, Double>> entries =
                preparePrices(pricesAndDates, period);

        Map<String, Double> result = new LinkedHashMap<>();

        if (entries.size() < period) {
            return result;
        }

        double sum = 0.0;
        for (int index = 0; index < period; index++) {
            sum += entries.get(index).getValue();
        }

        double ema = sum / period;
        double multiplier = 2.0 / (period + 1.0);

        result.put(entries.get(period - 1).getKey(), ema);

        for (int index = period; index < entries.size(); index++) {
            double price = entries.get(index).getValue();
            ema += multiplier * (price - ema);

            result.put(entries.get(index).getKey(), ema);
        }

        return result;
    }

    public static MacdResult MACD(Map<String, Double> pricesAndDates,
        int shortPeriod,
        int longPeriod,
        int signalPeriod)
    {

        if (shortPeriod < 1
                || longPeriod <= shortPeriod
                || signalPeriod < 1) {
            throw new IllegalArgumentException(
                    "Periods must be positive and longPeriod must exceed shortPeriod."
            );
        }

        Map<String, Double> shortEma = EMA(pricesAndDates, shortPeriod);
        Map<String, Double> longEma = EMA(pricesAndDates, longPeriod);

        Map<String, Double> macd = new LinkedHashMap<>();

        longEma.forEach((date, value) ->
                macd.put(date, shortEma.get(date) - value)
        );

        Map<String, Double> signal = EMA(macd, signalPeriod);
        Map<String, Double> histogram = new LinkedHashMap<>();

        signal.forEach((date, value) ->
                histogram.put(date, macd.get(date) - value)
        );

        return new MacdResult(macd, signal, histogram);
    }

    private static List<Map.Entry<String, Double>> preparePrices(
        Map<String, Double> pricesAndDates,
        int period)
    {

        if (period < 1) {
            throw new IllegalArgumentException("Period must be positive.");
        }

        Objects.requireNonNull(pricesAndDates, "Prices must not be null.");

        List<Map.Entry<String, Double>> entries =
                new ArrayList<>(new TreeMap<>(pricesAndDates).entrySet());

        for (Map.Entry<String, Double> entry : entries) {
            Double price = entry.getValue();

            if (price == null || !Double.isFinite(price)) {
                throw new IllegalArgumentException(
                        "Price must be finite for date: " + entry.getKey()
                );
            }
        }

        return entries;
    }

    private static double rsiValue(double averageGain, double averageLoss) {
        if (averageGain == 0.0 && averageLoss == 0.0) {
            return 50.0;
        }

        if (averageLoss == 0.0) {
            return 100.0;
        }

        if (averageGain == 0.0) {
            return 0.0;
        }

        double relativeStrength = averageGain / averageLoss;
        return 100.0 - 100.0 / (1.0 + relativeStrength);
    }

    public record MacdResult(
        Map<String, Double> macd,
        Map<String, Double> signal,
        Map<String, Double> histogram) {}
}
