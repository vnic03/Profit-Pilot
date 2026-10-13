package org.example.profitpilot.api.alpha_vantage;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.example.profitpilot.database.DatabaseService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.example.profitpilot.database.MarketIndicators;
import org.junit.jupiter.api.io.TempDir;


class ApiClientTest {

    private DatabaseService database;
    private AlphaVantageCache cache;
    private ApiClient client;

    @BeforeEach
    void setUp() {
        database = mock(DatabaseService.class);
        cache = mock(AlphaVantageCache.class);
        client = new ApiClient(database, cache, Clock.fixed(
                    Instant.parse("2026-10-13T12:00:00Z"),
                    ZoneOffset.UTC
                    ));
    }

    @TempDir
    Path temporaryDirectory;

    @Test
    void disabledApiDoesNotRecordAttemptOrFetchData() throws Exception {
        when(cache.isExternalApiEnabled()).thenReturn(false);

        client.syncDailyPrices("IBM");

        verifyNoInteractions(database);
        verify(cache, never()).fetchData(anyString());
    }

    @Test
    void existingDailyAttemptPreventsFetch() throws Exception {
        when(cache.isExternalApiEnabled()).thenReturn(true);
        when(database.tryStartDailySync("IBM")).thenReturn(false);

        client.syncDailyPrices("IBM");

        verify(database).tryStartDailySync("IBM");
        verify(database).getDataStatus(List.of("IBM"));
        verify(database, never()).markDailySyncFailed(anyString());
        verifyNoMoreInteractions(database);
        verify(cache, never()).fetchData(anyString());
    }

    @Test
    void successfulImportSavesPricesBeforeMarkingSuccess() throws Exception {
        allowImport();
        when(cache.fetchData("IBM")).thenReturn(validResponse());

        client.syncDailyPrices(" ibm ");

        var expectedPrices = List.of(
                new DatabaseService.SharePrice(
                        "IBM", "2026-10-12",
                        100.0, 110.0, 95.0, 105.0, 1000L
                )
        );

        var order = inOrder(database, cache);

        order.verify(cache).isExternalApiEnabled();
        order.verify(database).getDataStatus(List.of("IBM"));
        order.verify(database).tryStartDailySync("IBM");
        order.verify(cache).fetchData("IBM");
        order.verify(database).insertSharePrice(expectedPrices);
        order.verify(database).markDailySyncSuccessful("IBM");

        verify(cache, times(1)).fetchData("IBM");
        verify(database, never()).markDailySyncFailed(anyString());
    }

    @Test
    void missingApiDataDoesNotMarkSuccess() throws Exception {
        allowImport();
        when(cache.fetchData("IBM")).thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> client.syncDailyPrices("IBM")
        );

        verify(database).tryStartDailySync("IBM");
        verify(database).markDailySyncFailed("IBM");
        verify(database, never()).insertSharePrice(anyList());
        verify(database, never()).markDailySyncSuccessful(anyString());
    }

    @Test
    void storageFailureDoesNotMarkSuccess() throws Exception {
        allowImport();
        when(cache.fetchData("IBM")).thenReturn(validResponse());

        doThrow(new IllegalStateException("Storage failed."))
                .when(database).insertSharePrice(anyList());

        assertThrows(
                IllegalStateException.class,
                () -> client.syncDailyPrices("IBM")
        );

        verify(database).markDailySyncFailed("IBM");
        verify(database, never()).markDailySyncSuccessful(anyString());
    }

    @Test
    void databaseFailurePreventsApiRequest() throws Exception {
        when(cache.isExternalApiEnabled()).thenReturn(true);
        when(database.tryStartDailySync("IBM"))
                .thenThrow(new SQLException("Database unavailable."));

        assertThrows(
                SQLException.class,
                () -> client.syncDailyPrices("IBM")
        );

        verify(cache, never()).fetchData(anyString());
        verify(database, never()).markDailySyncSuccessful(anyString());
    }

    @Test
    void exportUsesStoredDataWithoutCallingExternalApi() throws Exception {
        List<MarketIndicators> rows = List.of(
                new MarketIndicators(
                        "2026-10-05",
                        105.5,
                        60.0,
                        104.5,
                        2.0,
                        1.5
                )
        );

        when(database.queryFinancialDataForSymbol("IBM"))
                .thenReturn(rows);

        Path output = temporaryDirectory.resolve("ibm.arff");

        client.exportFinancialDataToArff("IBM", output.toString());

        assertTrue(Files.isRegularFile(output));
        assertTrue(Files.readString(output).contains("@DATA"));

        verify(database).queryFinancialDataForSymbol("IBM");
        verifyNoMoreInteractions(database);
        verifyNoInteractions(cache);
    }

    @Test
    void exportWithoutCompleteRowsDoesNotCreateFile() {
        when(database.queryFinancialDataForSymbol("IBM"))
                .thenReturn(List.of());

        Path output = temporaryDirectory.resolve("empty.arff");

        assertThrows(
                IllegalArgumentException.class,
                () -> client.exportFinancialDataToArff(
                        "IBM",
                        output.toString()
                )
        );

        assertFalse(Files.exists(output));
        verifyNoInteractions(cache);
    }

    @Test
    void exportPropagatesDatabaseFailureWithoutCreatingFile() {
        when(database.queryFinancialDataForSymbol("IBM"))
                .thenThrow(
                        new IllegalStateException("Database unavailable.")
                );

        Path output = temporaryDirectory.resolve("failed.arff");

        assertThrows(
                IllegalStateException.class,
                () -> client.exportFinancialDataToArff(
                        "IBM",
                        output.toString()
                )
        );

        assertFalse(Files.exists(output));
        verifyNoInteractions(cache);
    }

    private void allowImport() throws SQLException {
        when(cache.isExternalApiEnabled()).thenReturn(true);
        when(database.tryStartDailySync("IBM")).thenReturn(true);
    }

    private ApiResponse validResponse() {
        return new ApiResponse(Map.of(
                "2026-10-12",
                new ApiResponse.DailyData(
                        100.0, 110.0, 95.0, 105.0, 1000L
                )
        ));
    }

    @Test
    void currentPricesDoNotReserveBudgetOrCallProvider() throws Exception {
        when(cache.isExternalApiEnabled()).thenReturn(true);

        when(database.getDataStatus(List.of("IBM"))).thenReturn(List.of(
                new DatabaseService.DataStatus(
                        "IBM",
                        100L,
                        LocalDate.parse("2026-10-12"),
                        null,
                        null
                )
        ));

        assertEquals(
                ApiClient.SyncResult.SKIPPED,
                client.syncDailyPrices("IBM")
        );

        verify(database, never()).tryStartDailySync(anyString());
        verify(cache, never()).fetchData(anyString());
        verify(database, never()).insertSharePrice(anyList());
    }

    @Test
    void olderPricesContinueThroughBudgetProtectedImport() throws Exception {
        allowImport();

        when(database.getDataStatus(List.of("IBM"))).thenReturn(List.of(
                new DatabaseService.DataStatus(
                        "IBM",
                        100L,
                        LocalDate.parse("2026-10-08"),
                        null,
                        null
                )
        ));

        when(cache.fetchData("IBM")).thenReturn(validResponse());

        assertEquals(
                ApiClient.SyncResult.IMPORTED,
                client.syncDailyPrices("IBM")
        );

        verify(database).tryStartDailySync("IBM");
        verify(cache).fetchData("IBM");
        verify(database).markDailySyncSuccessful("IBM");
    }

    @Test
    void holidayDoesNotConsumeRequestBudget() throws Exception {
        client = new ApiClient(
                database,
                cache,
                Clock.fixed(
                        Instant.parse("2026-11-27T12:00:00Z"),
                        ZoneOffset.UTC
                )
        );

        when(cache.isExternalApiEnabled()).thenReturn(true);

        when(database.getDataStatus(List.of("IBM"))).thenReturn(List.of(
                new DatabaseService.DataStatus(
                        "IBM",
                        100L,
                        LocalDate.parse("2026-11-25"),
                        null,
                        null
                )
        ));

        assertEquals(
                ApiClient.SyncResult.SKIPPED,
                client.syncDailyPrices("IBM")
        );

        verify(database, never()).tryStartDailySync(anyString());
        verify(cache, never()).fetchData(anyString());
    }

    @Test
    void unknownCalendarPreventsReservationAndProviderRequest()
            throws Exception {

        client = new ApiClient(
                database,
                cache,
                Clock.fixed(
                        Instant.parse("2029-01-02T12:00:00Z"),
                        ZoneOffset.UTC
                )
        );

        when(cache.isExternalApiEnabled()).thenReturn(true);

        assertThrows(
                IllegalStateException.class,
                () -> client.syncDailyPrices("IBM")
        );

        verify(database, never()).tryStartDailySync(anyString());
        verify(cache, never()).fetchData(anyString());
    }

    @Test
    void failureRecordingDoesNotHideOriginalImportError() throws Exception {
        allowImport();

        var originalFailure =
                new IllegalStateException("Provider unavailable.");

        var recordingFailure =
                new SQLException("Could not persist failure.");

        when(cache.fetchData("IBM")).thenThrow(originalFailure);

        doThrow(recordingFailure)
                .when(database)
                .markDailySyncFailed("IBM");

        var thrown = assertThrows(
                IllegalStateException.class,
                () -> client.syncDailyPrices("IBM")
        );

        assertSame(originalFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(recordingFailure, thrown.getSuppressed()[0]);

        verify(database, never()).markDailySyncSuccessful(anyString());
    }

    @Test
    void outdatedResponseIsSavedButAllowsALaterRetry() throws Exception {
        allowImport();

        when(cache.fetchData("IBM")).thenReturn(new ApiResponse(Map.of(
                "2026-10-09",
                new ApiResponse.DailyData(
                        100.0, 110.0, 95.0, 105.0, 1000L
                )
        )));

        assertEquals(
                ApiClient.SyncResult.WAITING_FOR_DATA,
                client.syncDailyPrices("IBM")
        );

        var order = inOrder(database);

        order.verify(database).getDataStatus(List.of("IBM"));
        order.verify(database).tryStartDailySync("IBM");
        order.verify(database).insertSharePrice(List.of(
                new DatabaseService.SharePrice(
                        "IBM", "2026-10-09",
                        100.0, 110.0, 95.0, 105.0, 1000L
                )
        ));
        order.verify(database).markDailySyncFailed("IBM");

        verify(database, never()).markDailySyncSuccessful(anyString());
        verify(cache, times(1)).fetchData("IBM");
    }

    @Test
    void futureResponseIsRejectedBeforeSaving() throws Exception {
        allowImport();

        when(cache.fetchData("IBM")).thenReturn(new ApiResponse(Map.of(
                "2026-10-14",
                new ApiResponse.DailyData(
                        100.0, 110.0, 95.0, 105.0, 1000L
                )
        )));

        assertThrows(
                IllegalStateException.class,
                () -> client.syncDailyPrices("IBM")
        );

        verify(database, never()).insertSharePrice(anyList());
        verify(database, never()).markDailySyncSuccessful(anyString());
        verify(database).markDailySyncFailed("IBM");
    }
}
