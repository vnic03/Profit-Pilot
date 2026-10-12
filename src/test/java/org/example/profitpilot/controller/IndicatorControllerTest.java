package org.example.profitpilot.controller;

import java.util.Map;

import org.example.profitpilot.database.DatabaseService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IndicatorControllerTest {

    private DatabaseService database;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        database = mock(DatabaseService.class);

        mvc = MockMvcBuilders.standaloneSetup(
                new IndicatorController(database)
        ).build();
    }

    @ParameterizedTest
    @CsvSource({
            "dynamicSMA, 0",
            "dynamicEMA, -1",
            "dynamicRSI, 51",
            "dynamicSMA, 1.5",
            "dynamicEMA, abc",
            "dynamicRSI, 99999999999999999999"
    })
    void invalidPeriodsNeverReachDatabase(
            String indicator,
            String period) throws Exception {

        mvc.perform(get(
                        "/api/finance/{indicator}/IBM/{period}",
                        indicator,
                        period
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_period"));

        verifyNoInteractions(database);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "dynamicSMA", "dynamicEMA", "dynamicRSI"
    })
    void routesToCorrectCalculationAndNormalizesSymbol(
            String indicator) throws Exception {

        Map<String, Double> values = Map.of("2026-10-05", 42.5);

        switch (indicator) {
            case "dynamicSMA" ->
                    when(database.getSMA("IBM", 14)).thenReturn(values);
            case "dynamicEMA" ->
                    when(database.getEMA("IBM", 14)).thenReturn(values);
            case "dynamicRSI" ->
                    when(database.getRSI("IBM", 14)).thenReturn(values);
        }

        mvc.perform(get("/api/finance/" + indicator + "/ibm/14"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"2026-10-05\":42.5}"));

        switch (indicator) {
            case "dynamicSMA" -> verify(database).getSMA("IBM", 14);
            case "dynamicEMA" -> verify(database).getEMA("IBM", 14);
            case "dynamicRSI" -> verify(database).getRSI("IBM", 14);
        }

        verifyNoMoreInteractions(database);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 50})
    void boundaryPeriodsAreAccepted(int period) throws Exception {
        when(database.getSMA("IBM", period)).thenReturn(Map.of());

        mvc.perform(get("/api/finance/dynamicSMA/IBM/" + period))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));

        verify(database).getSMA("IBM", period);
    }

    @Test
    void unsupportedSymbolNeverReachesDatabase() throws Exception {
        mvc.perform(get("/api/finance/dynamicSMA/UNKNOWN/14"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unsupported_symbol"));

        verifyNoInteractions(database);
    }

    @Test
    void databaseFailureReturnsGenericMessage() throws Exception {
        when(database.getRSI("IBM", 14))
                .thenThrow(new IllegalStateException("Internal details"));

        mvc.perform(get("/api/finance/dynamicRSI/IBM/14"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("indicator_failed"))
                .andExpect(jsonPath("$.message").value(
                        "Could not load indicator data. Please try again."
                ));
    }
}
