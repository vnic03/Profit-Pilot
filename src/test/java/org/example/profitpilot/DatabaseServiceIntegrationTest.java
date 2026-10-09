package org.example.profitpilot.database;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(
        named = "PROFITPILOT_DB_TEST",
        matches = "true"
)
class DatabaseServiceIntegrationTest {

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private DatabaseService database;

    @BeforeEach
    void setUp() {
        dataSource = new SingleConnectionDataSource(
                requiredEnv("PROFITPILOT_TEST_DB_URL"),
                requiredEnv("PROFITPILOT_TEST_DB_USER"),
                requiredEnv("PROFITPILOT_TEST_DB_PASSWORD"),
                true
        );

        jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("""
                CREATE TEMP TABLE aktienkurse (
                    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    symbol VARCHAR(10) NOT NULL,
                    date DATE NOT NULL,
                    open NUMERIC,
                    high NUMERIC,
                    low NUMERIC,
                    close NUMERIC,
                    volume BIGINT,
                    UNIQUE (symbol, date)
                )
                """);

        jdbc.execute("""
                CREATE TEMP TABLE stock_sync_status (
                    symbol VARCHAR(10) PRIMARY KEY,
                    last_attempt_at TIMESTAMPTZ,
                    last_success_at TIMESTAMPTZ
                )
                """);

        database = new DatabaseService(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void dailyAttemptIsReservedOnceAndSuccessIsRecordedSeparately()
            throws Exception {

        assertTrue(database.tryStartDailySync("IBM"));
        assertFalse(database.tryStartDailySync("IBM"));

        assertEquals(
                0,
                jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM stock_sync_status
                        WHERE last_success_at IS NOT NULL
                        """, Integer.class)
        );

        database.markDailySyncSuccessful("IBM");

        assertEquals(
                1,
                jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM stock_sync_status
                        WHERE symbol = 'IBM'
                          AND last_success_at IS NOT NULL
                        """, Integer.class)
        );

        jdbc.update("""
                UPDATE stock_sync_status
                SET last_attempt_at = (
                    (CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Berlin')::date - 1
                )::timestamp AT TIME ZONE 'Europe/Berlin'
                WHERE symbol = 'IBM'
                """);

        assertTrue(database.tryStartDailySync("IBM"));
        assertFalse(database.tryStartDailySync("IBM"));
    }

    @Test
    void repeatedImportUpdatesExistingPriceWithoutCreatingDuplicate() {
        database.insertSharePrice(List.of(price("IBM", 105.0)));
        database.insertSharePrice(List.of(price("IBM", 107.0)));

        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM aktienkurse",
                        Integer.class
                )
        );

        assertEquals(
                107.0,
                jdbc.queryForObject(
                        "SELECT close FROM aktienkurse WHERE symbol = 'IBM'",
                        Double.class
                ),
                0.0001
        );
    }

    @Test
    void failedBatchRollsBackChangesAndPreservesExistingPrice() {
        database.insertSharePrice(List.of(price("IBM", 105.0)));

        assertThrows(
                IllegalStateException.class,
                () -> database.insertSharePrice(List.of(
                        price("IBM", 109.0),
                        price("SYMBOL_TOO_LONG", 106.0)
                ))
        );

        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM aktienkurse",
                        Integer.class
                )
        );

        assertEquals(
                105.0,
                jdbc.queryForObject(
                        "SELECT close FROM aktienkurse WHERE symbol = 'IBM'",
                        Double.class
                ),
                0.0001
        );
    }

    private DatabaseService.SharePrice price(String symbol, double close) {
        return new DatabaseService.SharePrice(
                symbol,
                "2026-10-05",
                100.0,
                110.0,
                95.0,
                close,
                1000L
        );
    }

    private String requiredEnv(String name) {
        return Objects.requireNonNull(
                System.getenv(name),
                "Missing environment variable: " + name
        );
    }
}
