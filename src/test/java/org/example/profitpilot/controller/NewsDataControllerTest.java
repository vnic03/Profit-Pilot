package org.example.profitpilot.controller;

import java.util.List;

import org.example.profitpilot.api.marketaux.MarketauxCache;
import org.example.profitpilot.api.marketaux.NewsApiResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NewsDataControllerTest {

    private MarketauxCache cache;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        cache = mock(MarketauxCache.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new NewsDataController(cache)
        ).build();
    }

    @Test
    void disabledNewsDoesNotFetchData() throws Exception {
        when(cache.isExternalApiEnabled()).thenReturn(false);

        mvc.perform(get("/api/news").param("query", "Apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("disabled"))
                .andExpect(jsonPath("$.articles").isEmpty());

        verify(cache).isExternalApiEnabled();
        verifyNoMoreInteractions(cache);
    }

    @Test
    void emptyResultsAreSuccessful() throws Exception {
        when(cache.isExternalApiEnabled()).thenReturn(true);
        when(cache.getData("Apple"))
                .thenReturn(new NewsApiResponse(List.of()));

        mvc.perform(get("/api/news").param("query", "Apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.articles").isEmpty());
    }

    @Test
    void providerFailureReturnsBadGateway() throws Exception {
        when(cache.isExternalApiEnabled()).thenReturn(true);
        when(cache.getData("Apple")).thenReturn(null);

        mvc.perform(get("/api/news").param("query", "Apple"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void blankQueryDoesNotFetchData() throws Exception {
        when(cache.isExternalApiEnabled()).thenReturn(true);

        mvc.perform(get("/api/news").param("query", " "))
                .andExpect(status().isBadRequest());

        verify(cache).isExternalApiEnabled();
        verifyNoMoreInteractions(cache);
    }
}
