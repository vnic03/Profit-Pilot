package io.github.vnic03.profitpilot.api.marketaux;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.vnic03.profitpilot.database.DatabaseService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketauxCacheTest {

    private MarketauxCache cache;

    @BeforeEach
    void setUp() {
        cache = spy(new MarketauxCache(mock(DatabaseService.class)));
        // Stub both network entry points: these tests never contact a provider.
        doReturn(true).when(cache).isExternalApiEnabled();
        doReturn(null).when(cache).fetchData(anyString());
    }

    @Test
    void normalizedSymbolsShareOneCachedResult() {
        var response = new NewsApiResponse(List.of());
        doReturn(response).when(cache).fetchData("IBM");

        assertSame(response, cache.getData(" ibm "));
        assertSame(response, cache.getData("IBM"));
        verify(cache, times(1)).fetchData("IBM");
    }

    @Test
    void missingResponseReturnsNullAndCanBeRetried() {
        var response = new NewsApiResponse(List.of());
        doReturn(null, response).when(cache).fetchData("IBM");

        assertNull(cache.getData("IBM"));
        assertSame(response, cache.getData("IBM"));
        verify(cache, times(2)).fetchData("IBM");
    }

    @Test
    void budgetFailureIsPropagatedAndNotCached() {
        var exhausted = new NewsBudgetExceededException();
        var response = new NewsApiResponse(List.of());
        doThrow(exhausted).doReturn(response).when(cache).fetchData("IBM");

        assertSame(
                exhausted,
                assertThrows(NewsBudgetExceededException.class, () -> cache.getData("IBM")));
        assertSame(response, cache.getData("IBM"));
    }

    @Test
    void disabledOrUnsupportedRequestsDoNotLoadData() {
        assertNull(cache.getData("UNKNOWN"));
        doReturn(false).when(cache).isExternalApiEnabled();
        assertNull(cache.getData("IBM"));
        verify(cache, never()).fetchData(anyString());
    }
}
