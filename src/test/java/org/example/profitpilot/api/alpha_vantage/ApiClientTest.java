package org.example.profitpilot.api.alpha_vantage;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.example.profitpilot.database.DatabaseService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApiClientTest {

    private DatabaseService database;
    private AlphaVantageCache cache;
    private ApiClient client;

    @BeforeEach
    void setUp() {
        database = mock(DatabaseService.class);
        cache = mock(AlphaVantageCache.class);
        client = new ApiClient(database, cache);
    }

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
                        "IBM", "2026-10-05",
                        100.0, 110.0, 95.0, 105.0, 1000L
                )
        );

        var order = inOrder(database, cache);

        order.verify(cache).isExternalApiEnabled();
        order.verify(database).tryStartDailySync("IBM");
        order.verify(cache).fetchData("IBM");
        order.verify(database).insertSharePrice(expectedPrices);
        order.verify(database).markDailySyncSuccessful("IBM");

        verify(cache, times(1)).fetchData("IBM");
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

    private void allowImport() throws SQLException {
        when(cache.isExternalApiEnabled()).thenReturn(true);
        when(database.tryStartDailySync("IBM")).thenReturn(true);
    }

    private ApiResponse validResponse() {
        return new ApiResponse(Map.of(
                "2026-10-05",
                new ApiResponse.DailyData(
                        100.0, 110.0, 95.0, 105.0, 1000L
                )
        ));
    }
}
