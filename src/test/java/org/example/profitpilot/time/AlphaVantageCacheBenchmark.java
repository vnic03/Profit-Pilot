package org.example.profitpilot.time;

import org.example.profitpilot.api.alpha_vantage.AlphaVantageCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AlphaVantageCacheBenchmark {

        private AlphaVantageCache cache;

        @BeforeEach
        public void setup() {
            cache = new AlphaVantageCache();
        }

        @Test
        public void benchmarkGetData() {
            String symbol = "AAPL"; // Use a real symbol for your tests

            // Measure time before improvements = 691202067 ns
           // 766059402 ns

            long start = System.nanoTime();
            cache.getData(symbol);
            long end = System.nanoTime();
            System.out.println("Time before improvements = " + (end - start) + " ns");
        }
    }
