package io.github.vnic03.profitpilot.indicators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarketIndicatorsCalculatorTest {

    @Test
    void smaSortsDatesAndUsesRollingWindow() {
        Map<String, Double> prices = new LinkedHashMap<>();
        prices.put("2026-10-05", 40.0);
        prices.put("2026-10-01", 20.0);
        prices.put("2026-09-30", 10.0);
        prices.put("2026-10-02", 30.0);

        Map<String, Double> result =
                MarketIndicatorsCalculator.SMA(prices, 3);

        assertEquals(
                List.of("2026-10-02", "2026-10-05"),
                new ArrayList<>(result.keySet())
        );

        assertEquals(20.0, result.get("2026-10-02"), 0.000001);
        assertEquals(30.0, result.get("2026-10-05"), 0.000001);
    }

    @Test
    void smaReturnsNoValuesBeforeEnoughPricesExist() {
        Map<String, Double> result =
                MarketIndicatorsCalculator.SMA(
                        Map.of("2026-10-05", 40.0),
                        3
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void smaWithPeriodOneReturnsEachPrice() {
        Map<String, Double> prices = Map.of(
                "2026-10-02", 30.0,
                "2026-10-05", 40.0
        );

        assertEquals(
                prices,
                MarketIndicatorsCalculator.SMA(prices, 1)
        );
    }

    @Test
    void smaRejectsInvalidPeriods() {
        for (int period : List.of(0, -1)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> MarketIndicatorsCalculator.SMA(Map.of(), period)
            );
        }
    }

    @Test
    void smaRejectsNonFinitePrices() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketIndicatorsCalculator.SMA(
                        Map.of("2026-10-05", Double.NaN),
                        1
                )
        );
    }
    @Test
    void emaUsesSmaSeedAndThenExponentialSmoothing() {
        Map<String, Double> prices = new LinkedHashMap<>();
        prices.put("2026-10-05", 10.0);
        prices.put("2026-10-02", 30.0);
        prices.put("2026-09-30", 10.0);
        prices.put("2026-10-01", 20.0);

        Map<String, Double> result =
                MarketIndicatorsCalculator.EMA(prices, 3);

        assertEquals(
                List.of("2026-10-02", "2026-10-05"),
                new ArrayList<>(result.keySet())
        );

        assertEquals(20.0, result.get("2026-10-02"), 0.000001);
        assertEquals(15.0, result.get("2026-10-05"), 0.000001);
    }

    @Test
    void emaHandlesInsufficientDataAndPeriodOne() {
        Map<String, Double> prices = Map.of("2026-10-05", 40.0);

        assertTrue(MarketIndicatorsCalculator.EMA(prices, 3).isEmpty());
        assertEquals(prices, MarketIndicatorsCalculator.EMA(prices, 1));
    }

    @Test
    void rsiUsesWilderSmoothing() {
        Map<String, Double> prices = new LinkedHashMap<>();
        prices.put("2026-10-05", 11.0);
        prices.put("2026-09-29", 10.0);
        prices.put("2026-10-01", 10.0);
        prices.put("2026-09-30", 11.0);
        prices.put("2026-10-02", 12.0);

        Map<String, Double> result =
                MarketIndicatorsCalculator.RSI(prices, 3);

        assertEquals(
                List.of("2026-10-02", "2026-10-05"),
                new ArrayList<>(result.keySet())
        );

        assertEquals(75.0, result.get("2026-10-02"), 0.000001);
        assertEquals(54.545454545, result.get("2026-10-05"), 0.000001);
    }

    @Test
    void rsiRequiresOneMorePriceThanItsPeriod() {
        Map<String, Double> prices = Map.of(
                "2026-09-30", 10.0,
                "2026-10-01", 11.0,
                "2026-10-02", 12.0
        );

        assertTrue(MarketIndicatorsCalculator.RSI(prices, 3).isEmpty());
    }

    @Test
    void rsiHandlesRisingFallingAndFlatPrices() {
        double[][] scenarios = {
                {10.0, 11.0, 12.0, 100.0},
                {12.0, 11.0, 10.0, 0.0},
                {10.0, 10.0, 10.0, 50.0}
        };

        for (double[] scenario : scenarios) {
            Map<String, Double> prices = Map.of(
                    "2026-09-30", scenario[0],
                    "2026-10-01", scenario[1],
                    "2026-10-02", scenario[2]
            );

            Map<String, Double> result =
                    MarketIndicatorsCalculator.RSI(prices, 2);

            assertEquals(
                    scenario[3],
                    result.get("2026-10-02"),
                    0.000001
            );
        }
    }

    @Test
    void emaAndRsiRejectInvalidInputs() {
        for (int period : List.of(0, -1)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> MarketIndicatorsCalculator.EMA(Map.of(), period)
            );

            assertThrows(
                    IllegalArgumentException.class,
                    () -> MarketIndicatorsCalculator.RSI(Map.of(), period)
            );
        }

        Map<String, Double> invalidPrices =
                Map.of("2026-10-05", Double.NaN);

        assertThrows(
                IllegalArgumentException.class,
                () -> MarketIndicatorsCalculator.EMA(invalidPrices, 1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> MarketIndicatorsCalculator.RSI(invalidPrices, 1)
        );
    }

    @Test
    void macdAlignsDatesAndCalculatesSignalAndHistogram() {
        Map<String, Double> prices = new LinkedHashMap<>();
        prices.put("2026-10-05", 20.0);
        prices.put("2026-10-01", 30.0);
        prices.put("2026-09-29", 10.0);
        prices.put("2026-10-02", 10.0);
        prices.put("2026-09-30", 20.0);

        var result = MarketIndicatorsCalculator.MACD(prices, 2, 3, 2);

        assertEquals(
                List.of("2026-10-01", "2026-10-02", "2026-10-05"),
                new ArrayList<>(result.macd().keySet())
        );

        assertEquals(
                List.of("2026-10-02", "2026-10-05"),
                new ArrayList<>(result.signal().keySet())
        );

        assertEquals(
                new ArrayList<>(result.signal().keySet()),
                new ArrayList<>(result.histogram().keySet())
        );

        assertEquals(5.0, result.macd().get("2026-10-01"), 0.000001);
        assertEquals(0.0, result.macd().get("2026-10-02"), 0.000001);
        assertEquals(5.0 / 6.0, result.macd().get("2026-10-05"), 0.000001);

        assertEquals(2.5, result.signal().get("2026-10-02"), 0.000001);
        assertEquals(25.0 / 18.0, result.signal().get("2026-10-05"), 0.000001);

        assertEquals(-2.5, result.histogram().get("2026-10-02"), 0.000001);
        assertEquals(-5.0 / 9.0, result.histogram().get("2026-10-05"), 0.000001);
    }

    @Test
    void macdWaitsForEnoughPricesAndSignalValues() {
        Map<String, Double> prices = Map.of(
                "2026-09-29", 10.0,
                "2026-09-30", 20.0
        );

        var insufficient = MarketIndicatorsCalculator.MACD(prices, 2, 3, 2);

        assertTrue(insufficient.macd().isEmpty());
        assertTrue(insufficient.signal().isEmpty());
        assertTrue(insufficient.histogram().isEmpty());

        Map<String, Double> threePrices = new LinkedHashMap<>(prices);
        threePrices.put("2026-10-01", 30.0);

        var firstMacd = MarketIndicatorsCalculator.MACD(threePrices, 2, 3, 2);

        assertEquals(1, firstMacd.macd().size());
        assertTrue(firstMacd.signal().isEmpty());
        assertTrue(firstMacd.histogram().isEmpty());
    }

    @Test
    void standardMacdStartsSignalAtThirtyFourthPrice() {
        Map<String, Double> prices = new LinkedHashMap<>();
        java.time.LocalDate start = java.time.LocalDate.of(2026, 1, 1);

        for (int index = 0; index < 34; index++) {
            prices.put(start.plusDays(index).toString(), 100.0);
        }

        var result = MarketIndicatorsCalculator.MACD(prices, 12, 26, 9);

        assertEquals(9, result.macd().size());
        assertEquals(
                start.plusDays(25).toString(),
                result.macd().keySet().iterator().next()
        );

        String signalDate = start.plusDays(33).toString();

        assertEquals(List.of(signalDate), new ArrayList<>(result.signal().keySet()));
        assertEquals(0.0, result.signal().get(signalDate), 0.000001);
        assertEquals(0.0, result.histogram().get(signalDate), 0.000001);
    }

    @Test
    void macdRejectsInvalidPeriods() {
        int[][] invalidPeriods = {
                {0, 26, 9},
                {12, 12, 9},
                {26, 12, 9},
                {12, 26, 0}
        };

        for (int[] periods : invalidPeriods) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> MarketIndicatorsCalculator.MACD(
                            Map.of(),
                            periods[0],
                            periods[1],
                            periods[2]
                    )
            );
        }
    }
}
