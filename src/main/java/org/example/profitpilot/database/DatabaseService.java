package org.example.profitpilot.database;

import org.example.profitpilot.api.alpha_vantage.StockChange;
import org.example.profitpilot.feature_engineering.MarketIndicatorsCalculator;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;

import java.sql.*;
import java.sql.Date;
import java.util.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;


@Service
public class DatabaseService {

    private final DataSource dataSource;

    public DatabaseService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Connection connect() throws SQLException {
        return dataSource.getConnection();
    }

    public void insertSharePrice(List<SharePrice> sharePrices) {
        final String SQL = """
                INSERT INTO aktienkurse
                  (symbol, date, open, high, low, close, volume)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (symbol, date)
                DO UPDATE SET
                  open = EXCLUDED.open,
                  high = EXCLUDED.high,
                  low = EXCLUDED.low,
                  close = EXCLUDED.close,
                  volume = EXCLUDED.volume
              """;

        if (sharePrices == null || sharePrices.isEmpty()) {
          throw new IllegalArgumentException("Nothing to save (insertSharePrice)");
        }

        try (Connection connection = connect()) {
            connection.setAutoCommit(false);

            try (PreparedStatement pstmt = connection.prepareStatement(SQL)) {
                for (SharePrice sp : sharePrices) {
                    if (!validateData(
                            sp.symbol(), sp.date(), sp.open(), sp.high(),
                            sp.low(), sp.close(), sp.volume())) {
                        throw new IllegalArgumentException(
                                "Invalid data for: " + sp.symbol());
                    }

                    pstmt.setString(1, sp.symbol());
                    pstmt.setDate(2, java.sql.Date.valueOf(sp.date()));
                    pstmt.setDouble(3, sp.open());
                    pstmt.setDouble(4, sp.high());
                    pstmt.setDouble(5, sp.low());
                    pstmt.setDouble(6, sp.close());
                    pstmt.setLong(7, sp.volume());
                    pstmt.addBatch();
                }

                pstmt.executeBatch();
                connection.commit();

            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Could not save shareprices", e);
        }
    }

    public void insertFinancialIndicator
            (String symbol, String date, Double sma, Double rsi,
             Double ema, Double macd, Double signalLine)
    {
        final String SQL =
                "INSERT INTO finanzindikatoren " +
                        "(symbol, datum, sma, rsi, ema, macd, signal_line) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = connect();
             PreparedStatement pstmt = connection.prepareStatement(SQL)) {

            pstmt.setString(1, symbol);
            pstmt.setDate(2, Date.valueOf(date));
            pstmt.setDouble(3, sma);
            pstmt.setDouble(4, rsi);
            pstmt.setDouble(5, ema);
            pstmt.setDouble(6, macd);
            pstmt.setDouble(7, signalLine);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

   public Map<String, Double> getHigh(String symbol) {
        return getMarketData(symbol, "high");
   }

    public Map<String, Double> getLow(String symbol) {
        return getMarketData(symbol, "low");
    }

    public Map<String, Double> getOpen(String symbol) {
        return getMarketData(symbol, "open");
    }

    public Map<String, Double> getClosePrices(String symbol) {
        return getMarketData(symbol, "close");
    }

    public Map<String, Long> getVolume(String symbol) {
        Map<String, Long> data = new HashMap<>();
        String SQL =
            "SELECT date, volume FROM aktienkurse WHERE symbol = ? ORDER BY date ASC";

        try (Connection connection = connect();
             PreparedStatement pstmt = connection.prepareStatement(SQL)) {
            pstmt.setString(1, symbol);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String date = rs.getString("date");
                    long volume = rs.getLong("volume");
                    data.put(date, volume);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load stored volume.", e);
        }
        return data;
    }

   private Map<String, Double> getMarketData(String symbol, String column) {
       Map<String, Double> data = new HashMap<>();
       final String SQL = "SELECT date, " + column +
           " FROM aktienkurse WHERE symbol = ? ORDER BY date ASC";

       try (Connection connection = connect();
            PreparedStatement pstmt = connection.prepareStatement(SQL)) {
           pstmt.setString(1, symbol);
           try (ResultSet rs = pstmt.executeQuery()) {
               while (rs.next()) {
                   String date = rs.getString("date");
                   double value = rs.getDouble(column);
                   data.put(date, value);
               }
           }
       } catch (SQLException e) {
           throw new IllegalStateException("Could not load stored market data.", e);
       }
       return data;
   }

   private Map<String, Double> getClosingPrices(String symbol) {
       Map<String, Double> prices = new LinkedHashMap<>();
       final String SQL =
           "SELECT date, close FROM aktienkurse WHERE symbol = ? ORDER BY date ASC";

       try (Connection connection = connect();
            PreparedStatement pstmt = connection.prepareStatement(SQL))
       {
           pstmt.setString(1, symbol);
           try (ResultSet rs = pstmt.executeQuery()) {
               while (rs.next()) {
                   String date = rs.getString("date");
                   double close = rs.getDouble("close");
                   prices.put(date, close);
               }
           }
       } catch (SQLException e) {
           throw new IllegalStateException("Could not load stored closing prices.", e);
       }
       return prices;
   }

   public Map<String, Double> getSMA(String symbol, int period) {
        Map<String, Double> prices = getClosingPrices(symbol);
        return MarketIndicatorsCalculator.SMA(prices, period);
   }

    public Map<String, Double> getRSI(String symbol, int period) {
        Map<String, Double> prices = getClosingPrices(symbol);
        return MarketIndicatorsCalculator.RSI(prices, period);
    }

    public Map<String, Double> getEMA(String symbol, int period) {
        Map<String, Double> prices = getClosingPrices(symbol);
        return MarketIndicatorsCalculator.EMA(prices, period);
    }

    public MarketIndicatorsCalculator.MacdResult getMacdSeries(String symbol) {
        return MarketIndicatorsCalculator.MACD(
                getClosingPrices(symbol),
                12,
                26,
                9
        );
    }

    public Map<String, Double> getMACD(String symbol) {
        return getMacdSeries(symbol).macd();
    }

    private Map<String, Double> getFinancialIndicator(String symbol, String indicator) {
        final String SQL = "SELECT datum, " + indicator +
            " FROM finanzindikatoren WHERE symbol = ? ORDER BY datum ASC";
        Map<String, Double> data = new HashMap<>();

        try (Connection connection = connect();
             PreparedStatement pstmt = connection.prepareStatement(SQL)) {

            pstmt.setString(1, symbol);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String date = rs.getString("datum");
                    double value = rs.getDouble(indicator);
                    data.put(date, value);
                }
            }
        } catch (SQLException e) {
            System.out.println(
                    "Error fetching " + indicator + " data: " + e.getMessage()
            );
        }
        return data;
    }

    public List<MarketIndicators> queryFinancialDataForSymbol(String symbol) {

        Map<String, Double> prices = getClosingPrices(symbol);
        Map<String, Double> sma = MarketIndicatorsCalculator.SMA(prices, 20);
        Map<String, Double> ema = MarketIndicatorsCalculator.EMA(prices, 20);
        Map<String, Double> rsi = MarketIndicatorsCalculator.RSI(prices, 14);
        MarketIndicatorsCalculator.MacdResult macd =
                MarketIndicatorsCalculator.MACD(prices, 12, 26, 9);

        List<MarketIndicators> rows = new ArrayList<>();

        for (String date : prices.keySet().stream().sorted().toList()) {
            if (!sma.containsKey(date)
                    || !ema.containsKey(date)
                    || !rsi.containsKey(date)
                    || !macd.macd().containsKey(date)
                    || !macd.signal().containsKey(date)) {
                continue;
            }

            rows.add(new MarketIndicators(
                    date,
                    sma.get(date),
                    rsi.get(date),
                    ema.get(date),
                    macd.macd().get(date),
                    macd.signal().get(date)
            ));
        }

        return rows;
    }

    public boolean tryStartDailySync(String symbol) throws SQLException {
        final String sql = """
                INSERT INTO stock_sync_status (symbol, last_attempt_at)
                VALUES (?, CURRENT_TIMESTAMP)
                ON CONFLICT (symbol) DO UPDATE
                SET last_attempt_at = EXCLUDED.last_attempt_at
                WHERE stock_sync_status.last_attempt_at IS NULL OR (stock_sync_status.last_attempt_at
                      AT TIME ZONE 'Europe/Berlin')::date
                      < (CURRENT_TIMESTAMP
                        AT TIME ZONE 'Europe/Berlin')::date
                RETURNING symbol
                """;

        try (Connection connection = connect();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, symbol);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
          }
    }

    public void markDailySyncSuccessful(String symbol) throws SQLException {
        final String sql = """
                UPDATE stock_sync_status
                SET last_success_at = CURRENT_TIMESTAMP
                WHERE symbol = ?
                """;

        try (Connection connection = connect();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, symbol);

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "Kein Sync-Status vorhanden für: " + symbol);
            }
        }
    }

    private boolean validateData
            (String symbol,String date, double open, double high,
             double low, double close, long volume)
    {
        if (symbol == null || symbol.isEmpty()) return false;
        if (open < 0 || high < 0 || low < 0 || close < 0) return false;
        return !(high < low);
    }

    public Map<String, List<StockChange>> getWinnersAndLosers(int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException();
        }

        final String sql = """
                WITH ranked_prices AS (
                    SELECT
                        symbol,
                        date,
                        close,
                        LAG(close) OVER (
                            PARTITION BY symbol ORDER BY date
                        ) AS previous_close,
                        ROW_NUMBER() OVER (
                            PARTITION BY symbol ORDER BY date DESC
                        ) AS position
                    FROM aktienkurse
                )
                SELECT
                    symbol,
                    date,
                    100.0 * (close - previous_close)
                        / previous_close AS percentage_rate
                FROM ranked_prices
                WHERE position = 1
                  AND previous_close > 0
                  AND close >= 0
                """;

        List<StockChange> winners = new ArrayList<>();
        List<StockChange> losers = new ArrayList<>();

        try (Connection connection = connect();
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                String symbol = result.getString("symbol");
                double percentageRate = result.getDouble("percentage_rate");

                if (!Double.isFinite(percentageRate)) {
                    continue;
                }

                StockChange change = new StockChange(
                        symbol,
                        percentageRate,
                        result.getDate("date").toLocalDate(),
                        symbol,
                        ""
                );

                if (percentageRate > 0) {
                    winners.add(change);
                } else if (percentageRate < 0) {
                    losers.add(change);
                }
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to load winners and loosers", e
            );
        }

        winners.sort(
                Comparator.comparingDouble(StockChange::getPercentageRate)
                        .reversed()
                        .thenComparing(StockChange::getSymbol)
        );

        losers.sort(
                Comparator.comparingDouble(StockChange::getPercentageRate)
                        .thenComparing(StockChange::getSymbol)
        );

        return Map.of(
                "winners", winners.stream().limit(amount).toList(),
                "losers", losers.stream().limit(amount).toList()
        );
    }

    public record DataStatus(
            String symbol,
            long priceCount,
            LocalDate latestPriceDate,
            OffsetDateTime lastAttemptAt,
            OffsetDateTime lastSuccessAt) {
    }

    public List<DataStatus> getDataStatus(List<String> symbols) {
        if (symbols.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(
                ", ",
                Collections.nCopies(symbols.size(), "(?)")
        );

        String sql = """
                WITH tracked(symbol) AS (
                    VALUES %s
                ),
                prices AS (
                    SELECT symbol, COUNT(*) AS price_count,
                          MAX(date) AS latest_price_date
                    FROM aktienkurse
                    GROUP BY symbol
                )
                SELECT tracked.symbol,
                      COALESCE(prices.price_count, 0) AS price_count,
                      prices.latest_price_date,
                      sync.last_attempt_at,
                      sync.last_success_at
                FROM tracked
                LEFT JOIN prices ON prices.symbol = tracked.symbol
                LEFT JOIN stock_sync_status sync
                    ON sync.symbol = tracked.symbol
                ORDER BY tracked.symbol
                """.formatted(placeholders);

        List<DataStatus> result = new ArrayList<>();

        try (Connection connection = connect();
            PreparedStatement statement = connection.prepareStatement(sql)) {

            for (int index = 0; index < symbols.size(); index++) {
                statement.setString(index + 1, symbols.get(index));
            }

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new DataStatus(
                            rows.getString("symbol"),
                            rows.getLong("price_count"),
                            rows.getObject("latest_price_date", LocalDate.class),
                            rows.getObject("last_attempt_at", OffsetDateTime.class),
                            rows.getObject("last_success_at", OffsetDateTime.class)
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load data status.", e);
        }

        return result;
    }

    public record SharePrice
            (String symbol, String date, Double open, Double high, Double low, Double close, Long volume) { }
}
