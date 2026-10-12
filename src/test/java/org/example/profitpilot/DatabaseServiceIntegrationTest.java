package org.example.profitpilot;

import org.example.profitpilot.database.DatabaseService;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(
        named = "PROFITPILOT_DB_TEST",
        matches = "true"
)
class DatabaseServiceIntegrationTest {

    private HikariDataSource dataSource;
    private JdbcTemplate adminJdbc;
    private JdbcTemplate jdbc;
    private DatabaseService database;
    private String testSchema;

    @BeforeEach
    void setUp() {
        String url = requiredEnv("PROFITPILOT_TEST_DB_URL");
        String username = requiredEnv("PROFITPILOT_TEST_DB_USER");
        String password = requiredEnv("PROFITPILOT_TEST_DB_PASSWORD");

        var adminDataSource =
                new DriverManagerDataSource(url, username, password);

        adminJdbc = new JdbcTemplate(adminDataSource);

        testSchema = "profitpilot_test_"
                + UUID.randomUUID().toString().replace("-", "");

        adminJdbc.execute("CREATE SCHEMA " + testSchema);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setSchema(testSchema);
        config.setMaximumPoolSize(6);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(10000);

        dataSource = new HikariDataSource(config);
        jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("""
                CREATE TABLE aktienkurse (
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
                CREATE TABLE stock_sync_status (
                    symbol VARCHAR(10) PRIMARY KEY,
                    last_attempt_at TIMESTAMPTZ,
                    last_success_at TIMESTAMPTZ
                )
                """);

        new ResourceDatabasePopulator(
                new ClassPathResource("db/api-budget.sql"),
                new ClassPathResource("db/price-import-retry.sql")
        ).execute(dataSource);

        database = new DatabaseService(dataSource);
    }

    private void ageLastAttempt(String symbol) {
        jdbc.update("""
                UPDATE stock_sync_status
                SET last_attempt_at =
                    clock_timestamp() - INTERVAL '2 hours'
                WHERE symbol = ?
                """, symbol);
    }

    @AfterEach
    void tearDown() {
        try {
            if (dataSource != null) {
                dataSource.close();
            }
        } finally {
            if (adminJdbc != null && testSchema != null) {
                adminJdbc.execute(
                        "DROP SCHEMA IF EXISTS " + testSchema + " CASCADE"
                );
            }
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

    @Test
    void exhaustedBudgetDoesNotRecordAnotherDailyAttempt() throws Exception {
        jdbc.update("""
                UPDATE api_budget_state
                SET request_limit = 2
                WHERE provider = 'alpha-vantage'
                """);

        assertTrue(database.tryStartDailySync("IBM"));
        assertFalse(database.tryStartDailySync("IBM"));
        assertTrue(database.tryStartDailySync("AAPL"));
        assertFalse(database.tryStartDailySync("MSFT"));

        assertEquals(
                2L,
                database.getImportBudgetStatus().reservedRequests()
        );

        assertEquals(
                0,
                jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM stock_sync_status
                        WHERE symbol = 'MSFT'
                        """, Integer.class)
        );

        // Recreating the service must not reset the database budget.
        DatabaseService recreatedService = new DatabaseService(dataSource);

        assertFalse(recreatedService.tryStartDailySync("AMZN"));

        assertEquals(
                0L,
                recreatedService.getImportBudgetStatus().remainingRequests()
        );
    }

    @Test
    void oldReservationsStopConsumingBudget() throws Exception {
        jdbc.update("""
                UPDATE api_budget_state
                SET request_limit = 1
                WHERE provider = 'alpha-vantage'
                """);

        assertTrue(database.tryStartDailySync("IBM"));
        assertFalse(database.tryStartDailySync("AAPL"));

        jdbc.update("""
                UPDATE api_request_reservations
                SET reserved_at = clock_timestamp() - INTERVAL '25 hours'
                """);

        assertTrue(database.tryStartDailySync("AAPL"));

        assertEquals(
                1L,
                database.getImportBudgetStatus().reservedRequests()
        );

        // Expired entries remain available as history.
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM api_request_reservations",
                        Integer.class
                )
        );
    }

    @Test
    void failedReservationRollsBackTheDailyAttempt() {
        jdbc.execute("""
                ALTER TABLE api_request_reservations
                ADD CONSTRAINT reject_test_symbol
                CHECK (symbol <> 'FAIL')
                """);

        assertThrows(
                SQLException.class,
                () -> database.tryStartDailySync("FAIL")
        );

        assertEquals(
                0,
                jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM stock_sync_status
                        WHERE symbol = 'FAIL'
                        """, Integer.class)
        );

        assertEquals(
                0L,
                database.getImportBudgetStatus().reservedRequests()
        );
    }

    @Test
    void concurrentReservationsCannotExceedTheSharedLimit() throws Exception {
        var tasks = new ArrayList<Callable<Boolean>>();

        for (int index = 0; index < 30; index++) {
            String symbol = "T" + index;
            tasks.add(() -> database.tryStartDailySync(symbol));
        }

        int accepted = 0;

        try (var executor = Executors.newFixedThreadPool(6)) {
            var results = executor.invokeAll(
                    tasks,
                    30,
                    TimeUnit.SECONDS
            );

            for (var result : results) {
                if (result.get()) {
                    accepted++;
                }
            }
        }

        assertEquals(25, accepted);

        assertEquals(
                25L,
                database.getImportBudgetStatus().reservedRequests()
        );

        assertEquals(
                0L,
                database.getImportBudgetStatus().remainingRequests()
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

    @Test
    void newsBudgetIsSeparatePersistentAndExpires() throws Exception {
        jdbc.update("""
                UPDATE api_budget_state
                SET request_limit = 1
                WHERE provider = 'marketaux'
                """);

        assertTrue(database.tryReserveNewsRequest("IBM"));

        DatabaseService recreated = new DatabaseService(dataSource);

        assertFalse(recreated.tryReserveNewsRequest("AAPL"));

        assertEquals(
                1L,
                recreated.getNewsBudgetStatus().reservedRequests()
        );

        assertEquals(
                0L,
                recreated.getImportBudgetStatus().reservedRequests()
        );

        assertTrue(database.tryStartDailySync("IBM"));

        assertEquals(
                1L,
                database.getImportBudgetStatus().reservedRequests()
        );

        jdbc.update("""
                UPDATE api_request_reservations
                SET reserved_at = clock_timestamp() - INTERVAL '25 hours'
                WHERE provider = 'marketaux'
                """);

        assertTrue(database.tryReserveNewsRequest("AAPL"));

        assertEquals(
                1L,
                database.getNewsBudgetStatus().reservedRequests()
        );

        assertEquals(
                1L,
                database.getImportBudgetStatus().reservedRequests()
        );
    }

    @Test
    void concurrentNewsReservationsRespectTheirOwnLimit() throws Exception {
        jdbc.update("""
                UPDATE api_budget_state
                SET request_limit = 3
                WHERE provider = 'marketaux'
                """);

        var tasks = new ArrayList<Callable<Boolean>>();

        for (int index = 0; index < 12; index++) {
            tasks.add(() -> database.tryReserveNewsRequest("IBM"));
        }

        int accepted = 0;

        try (var executor = Executors.newFixedThreadPool(6)) {
            var results = executor.invokeAll(
                    tasks,
                    30,
                    TimeUnit.SECONDS
            );

            for (var result : results) {
                if (result.get()) {
                    accepted++;
                }
            }
        }

        assertEquals(3, accepted);
        assertEquals(3L, database.getNewsBudgetStatus().reservedRequests());
        assertEquals(0L, database.getImportBudgetStatus().reservedRequests());
    }

    @Test
    void failedImportCanRetryAfterCooldownAcrossServiceRestart()
            throws Exception {

        assertTrue(database.tryStartDailySync("IBM"));
        database.markDailySyncFailed("IBM");

        // A recorded failure must not bypass the cooldown.
        assertFalse(database.tryStartDailySync("IBM"));

        ageLastAttempt("IBM");

        DatabaseService recreated = new DatabaseService(dataSource);

        assertTrue(recreated.tryStartDailySync("IBM"));
        assertFalse(recreated.tryStartDailySync("IBM"));

        assertEquals(
                2L,
                recreated.getImportBudgetStatus().reservedRequests()
        );

        // Reserving the retry consumes its permission.
        assertEquals(
                Boolean.FALSE,
                jdbc.queryForObject("""
                        SELECT retry_allowed
                        FROM stock_sync_status
                        WHERE symbol = 'IBM'
                        """, Boolean.class)
        );
    }

    @Test
    void repeatedFailuresStopAfterThreeAttemptsInRollingWindow()
            throws Exception {

        for (int attempt = 0; attempt < 3; attempt++) {
            assertTrue(database.tryStartDailySync("IBM"));

            database.markDailySyncFailed("IBM");
            ageLastAttempt("IBM");
        }

        assertFalse(database.tryStartDailySync("IBM"));

        assertEquals(
                3L,
                database.getImportBudgetStatus().reservedRequests()
        );

        // A different stock can still use the remaining shared budget.
        assertTrue(database.tryStartDailySync("AAPL"));

        jdbc.update("""
                UPDATE api_request_reservations
                SET reserved_at =
                    clock_timestamp() - INTERVAL '25 hours'
                WHERE provider = 'alpha-vantage'
                AND symbol = 'IBM'
                """);

        assertTrue(database.tryStartDailySync("IBM"));
    }

    @Test
    void retryStillRequiresSharedBudget() throws Exception {
        jdbc.update("""
                UPDATE api_budget_state
                SET request_limit = 1
                WHERE provider = 'alpha-vantage'
                """);

        assertTrue(database.tryStartDailySync("IBM"));
        database.markDailySyncFailed("IBM");
        ageLastAttempt("IBM");

        assertFalse(database.tryStartDailySync("IBM"));

        assertEquals(
                1L,
                database.getImportBudgetStatus().reservedRequests()
        );
    }

    @Test
    void concurrentCallsCannotConsumeSameRetryPermissionTwice()
            throws Exception {

        assertTrue(database.tryStartDailySync("IBM"));
        database.markDailySyncFailed("IBM");
        ageLastAttempt("IBM");

        var tasks = new ArrayList<Callable<Boolean>>();

        for (int index = 0; index < 12; index++) {
            tasks.add(() -> database.tryStartDailySync("IBM"));
        }

        int accepted = 0;

        try (var executor = Executors.newFixedThreadPool(6)) {
            var results = executor.invokeAll(
                    tasks,
                    30,
                    TimeUnit.SECONDS
            );

            for (var result : results) {
                if (result.get()) {
                    accepted++;
                }
            }
        }

        assertEquals(1, accepted);
        assertEquals(
                2L,
                database.getImportBudgetStatus().reservedRequests()
        );
    }
}
