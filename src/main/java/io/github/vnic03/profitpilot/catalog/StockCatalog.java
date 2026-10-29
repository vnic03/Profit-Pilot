package io.github.vnic03.profitpilot.catalog;

import java.util.List;
import java.util.Locale;

public final class StockCatalog {

    public record Stock(String symbol, String name, String exchange, String currency) {}

    private static final List<Stock> STOCKS =
            List.of(
                    new Stock("IBM", "International Business Machines", "NYSE", "USD"),
                    new Stock("AAPL", "Apple", "NASDAQ", "USD"),
                    new Stock("GOOGL", "Alphabet", "NASDAQ", "USD"),
                    new Stock("MSFT", "Microsoft", "NASDAQ", "USD"),
                    new Stock("AMZN", "Amazon", "NASDAQ", "USD"),
                    new Stock("NVDA", "NVIDIA", "NASDAQ", "USD"),
                    new Stock("JPM", "JPMorgan Chase", "NYSE", "USD"),
                    new Stock("JNJ", "Johnson & Johnson", "NYSE", "USD"),
                    new Stock("KO", "Coca-Cola", "NYSE", "USD"),
                    new Stock("XOM", "ExxonMobil", "NYSE", "USD"));

    private static final List<String> SYMBOLS = STOCKS.stream().map(Stock::symbol).toList();

    private StockCatalog() {}

    public static List<Stock> stocks() {
        return STOCKS;
    }

    public static List<String> symbols() {
        return SYMBOLS;
    }

    public static String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean supports(String symbol) {
        return SYMBOLS.contains(normalize(symbol));
    }
}
