package io.github.vnic03.profitpilot.database;

import io.github.vnic03.profitpilot.indicators.MarketIndicatorsCalculator;
import io.github.vnic03.profitpilot.model.StockChange;
import java.sql.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

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
        final String SQL =
                """
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
                            sp.symbol(),
                            sp.date(),
                            sp.open(),
                            sp.high(),
                            sp.low(),
                            sp.close(),
                            sp.volume())) {
                        throw new IllegalArgumentException("Invalid data for: " + sp.symbol());
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
            throw new IllegalStateException("Could not save shareprices", e);
        }
    }

    public Map<String, Double> getClosePrices(String symbol) {
        Map<String, Double> prices = new LinkedHashMap<>();

        String sql =
                """
        SELECT date, close
        FROM aktienkurse
        WHERE symbol = ?
        ORDER BY date ASC
        """;

        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, symbol);

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    prices.put(rows.getString("date"), rows.getDouble("close"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load stored closing prices.", e);
        }

        return prices;
    }

    public Map<String, Double> getSMA(String symbol, int period) {
        Map<String, Double> prices = getClosePrices(symbol);
        return MarketIndicatorsCalculator.SMA(prices, period);
    }

    public Map<String, Double> getRSI(String symbol, int period) {
        Map<String, Double> prices = getClosePrices(symbol);
        return MarketIndicatorsCalculator.RSI(prices, period);
    }

    public Map<String, Double> getEMA(String symbol, int period) {
        Map<String, Double> prices = getClosePrices(symbol);
        return MarketIndicatorsCalculator.EMA(prices, period);
    }

    public List<MarketIndicators> queryFinancialDataForSymbol(String symbol) {
        Map<String, Double> prices = getClosePrices(symbol);

        // Export settings are independent of the chart controls.
        Map<String, Double> sma = MarketIndicatorsCalculator.SMA(prices, 20);
        Map<String, Double> ema = MarketIndicatorsCalculator.EMA(prices, 20);
        Map<String, Double> rsi = MarketIndicatorsCalculator.RSI(prices, 14);
        var macd = MarketIndicatorsCalculator.MACD(prices, 12, 26, 9);

        List<MarketIndicators> rows = new ArrayList<>();

        for (String date : prices.keySet()) {
            if (!sma.containsKey(date)
                    || !ema.containsKey(date)
                    || !rsi.containsKey(date)
                    || !macd.macd().containsKey(date)
                    || !macd.signal().containsKey(date)) {
                continue;
            }

            rows.add(
                    new MarketIndicators(
                            date,
                            sma.get(date),
                            rsi.get(date),
                            ema.get(date),
                            macd.macd().get(date),
                            macd.signal().get(date)));
        }

        return rows;
    }

    /** Reserves a price request and its per-stock attempt in one transaction. */
    public boolean tryStartDailySync(String symbol) throws SQLException {
        try (Connection connection = connect()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            int originalIsolation = connection.getTransactionIsolation();

            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);

            try {
                // Serialize reservations across stocks and application instances.
                // Hold the provider row lock until the new reservation is committed.
                int requestLimit;

                try (PreparedStatement statement =
                                connection.prepareStatement(
                                        """
                        SELECT request_limit
                        FROM api_budget_state
                        WHERE provider = 'alpha-vantage'
                        FOR UPDATE
                        """);
                        ResultSet result = statement.executeQuery()) {

                    if (!result.next()) {
                        throw new SQLException("Alpha Vantage budget is not configured.");
                    }

                    requestLimit = result.getInt("request_limit");
                }

                long reservedRequests;
                long symbolAttempts;

                try (PreparedStatement statement =
                        connection.prepareStatement(
                                """
                        SELECT COUNT(*) AS total_requests,
                            COUNT(*) FILTER (WHERE symbol = ?) AS symbol_attempts
                        FROM api_request_reservations
                        WHERE provider = 'alpha-vantage'
                        AND reserved_at >
                            clock_timestamp() - INTERVAL '24 hours'
                        """)) {

                    statement.setString(1, symbol);

                    try (ResultSet result = statement.executeQuery()) {
                        result.next();

                        reservedRequests = result.getLong("total_requests");
                        symbolAttempts = result.getLong("symbol_attempts");
                    }
                }

                if (reservedRequests >= requestLimit || symbolAttempts >= 3) {
                    connection.rollback();
                    return false;
                }

                // An unfinished attempt can retry after an hour, including after a crash.
                // The shared budget and three-attempt limit still apply.
                boolean dailyAttemptReserved;

                try (PreparedStatement statement =
                        connection.prepareStatement(
                                """
                            INSERT INTO stock_sync_status (
                                symbol,
                                last_attempt_at,
                                retry_allowed
                            )
                            VALUES (?, clock_timestamp(), FALSE)
                            ON CONFLICT (symbol) DO UPDATE
                            SET last_attempt_at = EXCLUDED.last_attempt_at,
                                retry_allowed = FALSE
                            WHERE stock_sync_status.last_attempt_at IS NULL
                            OR (
                                stock_sync_status.last_attempt_at
                                    <= clock_timestamp() - INTERVAL '1 hour'
                                AND (
                                    stock_sync_status.retry_allowed
                                    OR stock_sync_status.last_success_at IS NULL
                                    OR stock_sync_status.last_success_at < stock_sync_status.last_attempt_at
                                    OR (
                                        stock_sync_status.last_attempt_at
                                        AT TIME ZONE 'Europe/Berlin'
                                    )::date < (
                                        clock_timestamp()
                                        AT TIME ZONE 'Europe/Berlin'
                                    )::date
                                )
                            )
                            RETURNING symbol
                        """)) {

                    statement.setString(1, symbol);

                    try (ResultSet result = statement.executeQuery()) {
                        dailyAttemptReserved = result.next();
                    }
                }

                if (!dailyAttemptReserved) {
                    connection.rollback();
                    return false;
                }

                try (PreparedStatement statement =
                        connection.prepareStatement(
                                """
                        INSERT INTO api_request_reservations (
                            provider,
                            symbol,
                            reserved_at
                        )
                        VALUES ('alpha-vantage', ?, clock_timestamp())
                        """)) {

                    statement.setString(1, symbol);
                    statement.executeUpdate();
                }

                connection.commit();
                return true;

            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }

                throw e;

            } finally {
                connection.setAutoCommit(originalAutoCommit);
                connection.setTransactionIsolation(originalIsolation);
            }
        }
    }

    public void markDailySyncSuccessful(String symbol) throws SQLException {
        final String sql =
                """
                UPDATE stock_sync_status
                SET last_success_at = clock_timestamp(), retry_allowed = FALSE
                WHERE symbol = ?
                """;

        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, symbol);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Kein Sync-Status vorhanden für: " + symbol);
            }
        }
    }

    private boolean validateData(
            String symbol,
            String date,
            double open,
            double high,
            double low,
            double close,
            long volume) {
        if (symbol == null || symbol.isEmpty()) return false;
        if (open < 0 || high < 0 || low < 0 || close < 0) return false;
        return !(high < low);
    }

    public Map<String, List<StockChange>> getWinnersAndLosers(int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException();
        }

        final String sql =
                """
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

                StockChange change =
                        new StockChange(
                                symbol,
                                percentageRate,
                                result.getDate("date").toLocalDate(),
                                symbol,
                                "");

                if (percentageRate > 0) {
                    winners.add(change);
                } else if (percentageRate < 0) {
                    losers.add(change);
                }
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load winners and loosers", e);
        }

        winners.sort(
                Comparator.comparingDouble(StockChange::percentageRate)
                        .reversed()
                        .thenComparing(StockChange::symbol));

        losers.sort(
                Comparator.comparingDouble(StockChange::percentageRate)
                        .thenComparing(StockChange::symbol));

        return Map.of(
                "winners", winners.stream().limit(amount).toList(),
                "losers", losers.stream().limit(amount).toList());
    }

    public boolean tryReserveNewsRequest(String symbol) throws SQLException {
        try (Connection connection = connect()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            int originalIsolation = connection.getTransactionIsolation();

            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);

            try {
                // Lock before counting so concurrent news requests cannot overspend.
                int limit;

                try (PreparedStatement statement =
                                connection.prepareStatement(
                                        """
                        SELECT request_limit
                        FROM api_budget_state
                        WHERE provider = 'marketaux'
                        FOR UPDATE
                        """);
                        ResultSet result = statement.executeQuery()) {

                    if (!result.next()) {
                        throw new SQLException("Marketaux budget is not configured.");
                    }

                    limit = result.getInt("request_limit");
                }

                long reserved;

                try (PreparedStatement statement =
                                connection.prepareStatement(
                                        """
                        SELECT COUNT(*)
                        FROM api_request_reservations
                        WHERE provider = 'marketaux'
                        AND reserved_at >
                            clock_timestamp() - INTERVAL '24 hours'
                        """);
                        ResultSet result = statement.executeQuery()) {

                    result.next();
                    reserved = result.getLong(1);
                }

                if (reserved >= limit) {
                    connection.rollback();
                    return false;
                }

                try (PreparedStatement statement =
                        connection.prepareStatement(
                                """
                        INSERT INTO api_request_reservations (
                            provider,
                            symbol,
                            reserved_at
                        )
                        VALUES ('marketaux', ?, clock_timestamp())
                        """)) {

                    statement.setString(1, symbol);
                    statement.executeUpdate();
                }

                connection.commit();
                return true;

            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }

                throw e;

            } finally {
                connection.setAutoCommit(originalAutoCommit);
                connection.setTransactionIsolation(originalIsolation);
            }
        }
    }

    // Keep the budget reservation: the provider may still count a failed request.
    public void markDailySyncFailed(String symbol) throws SQLException {
        String sql =
                """
                UPDATE stock_sync_status
                SET retry_allowed = TRUE
                WHERE symbol = ?
                AND last_attempt_at IS NOT NULL
                AND (
                    last_success_at IS NULL
                    OR last_success_at < last_attempt_at
                )
                """;

        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, symbol);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Could not record failed import for: " + symbol);
            }
        }
    }

    public record DataStatus(
            String symbol,
            long priceCount,
            LocalDate latestPriceDate,
            OffsetDateTime lastAttemptAt,
            OffsetDateTime lastSuccessAt) {}

    public List<DataStatus> getDataStatus(List<String> symbols) {
        if (symbols.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(", ", Collections.nCopies(symbols.size(), "(?)"));

        String sql =
                """
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
                """
                        .formatted(placeholders);

        List<DataStatus> result = new ArrayList<>();

        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            for (int index = 0; index < symbols.size(); index++) {
                statement.setString(index + 1, symbols.get(index));
            }

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(
                            new DataStatus(
                                    rows.getString("symbol"),
                                    rows.getLong("price_count"),
                                    rows.getObject("latest_price_date", LocalDate.class),
                                    rows.getObject("last_attempt_at", OffsetDateTime.class),
                                    rows.getObject("last_success_at", OffsetDateTime.class)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load data status.", e);
        }

        return result;
    }

    public record ImportBudgetStatus(
            String provider,
            int requestLimit,
            long reservedRequests,
            long remainingRequests,
            String window) {}

    public ImportBudgetStatus getImportBudgetStatus() {
        return getBudgetStatus("alpha-vantage");
    }

    public ImportBudgetStatus getNewsBudgetStatus() {
        return getBudgetStatus("marketaux");
    }

    private ImportBudgetStatus getBudgetStatus(String provider) {
        final String sql =
                """
                SELECT
                    budget.request_limit,
                    COUNT(reservation.id) AS reserved_requests
                FROM api_budget_state budget
                LEFT JOIN api_request_reservations reservation
                    ON reservation.provider = budget.provider
                AND reservation.reserved_at >
                    statement_timestamp() - INTERVAL '24 hours'
                WHERE budget.provider = ?
                GROUP BY budget.provider, budget.request_limit
                """;

        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, provider);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("Budget is not configured for " + provider);
                }

                int limit = result.getInt("request_limit");
                long reserved = result.getLong("reserved_requests");

                return new ImportBudgetStatus(
                        provider,
                        limit,
                        reserved,
                        Math.max(0L, limit - reserved),
                        "rolling_24_hours");
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Could not load budget for " + provider, e);
        }
    }

    public record SharePrice(
            String symbol,
            String date,
            Double open,
            Double high,
            Double low,
            Double close,
            Long volume) {}

    public record ChartData(
            Map<String, Double> closePrices,
            Map<String, Double> high,
            Map<String, Double> low,
            Map<String, Double> open,
            Map<String, Long> volume,
            Map<String, Double> macd,
            Map<String, Double> macdSignal,
            Map<String, Double> macdHistogram) {}

    public ChartData getChartData(String symbol) {
        Map<String, Double> close = new LinkedHashMap<>();
        Map<String, Double> high = new LinkedHashMap<>();
        Map<String, Double> low = new LinkedHashMap<>();
        Map<String, Double> open = new LinkedHashMap<>();
        Map<String, Long> volume = new LinkedHashMap<>();

        String sql =
                """
        SELECT date, open, high, low, close, volume
        FROM aktienkurse
        WHERE symbol = ?
        ORDER BY date ASC
        """;

        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, symbol);

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String date = rows.getString("date");
                    close.put(date, rows.getDouble("close"));
                    high.put(date, rows.getDouble("high"));
                    low.put(date, rows.getDouble("low"));
                    open.put(date, rows.getDouble("open"));
                    volume.put(date, rows.getLong("volume"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load stored chart data.", e);
        }

        var macd = MarketIndicatorsCalculator.MACD(close, 12, 26, 9);
        return new ChartData(
                close, high, low, open, volume, macd.macd(), macd.signal(), macd.histogram());
    }
}
