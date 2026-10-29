package io.github.vnic03.profitpilot.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.vnic03.profitpilot.api.marketaux.MarketauxCache;
import io.github.vnic03.profitpilot.api.marketaux.NewsApiResponse;
import io.github.vnic03.profitpilot.api.marketaux.NewsBudgetExceededException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NewsDataControllerTest {

    private MarketauxCache cache;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        cache = mock(MarketauxCache.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new NewsDataController(cache))
                        .setControllerAdvice(new ApiSupport())
                        .build();
    }

    @Test
    void disabledNewsDoesNotFetchData() throws Exception {
        when(cache.isExternalApiEnabled()).thenReturn(false);

        mvc.perform(get("/api/news").param("symbol", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("disabled"))
                .andExpect(jsonPath("$.articles").isEmpty());

        verify(cache).isExternalApiEnabled();
        verifyNoMoreInteractions(cache);
    }

    @Test
    void emptyResultsAreSuccessful() throws Exception {
        when(cache.isExternalApiEnabled()).thenReturn(true);
        when(cache.getData("AAPL")).thenReturn(new NewsApiResponse(List.of()));

        mvc.perform(get("/api/news").param("symbol", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.articles").isEmpty());
    }

    @Test
    void providerFailureReturnsBadGateway() throws Exception {
        when(cache.isExternalApiEnabled()).thenReturn(true);
        when(cache.getData("AAPL")).thenReturn(null);

        mvc.perform(get("/api/news").param("symbol", "AAPL"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("news_failed"));
    }

    @Test
    void invalidSymbolDoesNotContactProvider() throws Exception {
        mvc.perform(get("/api/news").param("symbol", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unsupported_symbol"));

        verifyNoInteractions(cache);
    }

    @Test
    void exhaustedBudgetReturnsDistinctResponse() throws Exception {
        when(cache.isExternalApiEnabled()).thenReturn(true);

        when(cache.getData("AAPL")).thenThrow(new NewsBudgetExceededException());

        mvc.perform(get("/api/news").param("symbol", "AAPL"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("budget_exhausted"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.articles").doesNotExist());
    }
}
