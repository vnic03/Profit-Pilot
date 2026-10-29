package io.github.vnic03.profitpilot.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.vnic03.profitpilot.database.DatabaseService;
import io.github.vnic03.profitpilot.model.StockChange;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FinancialDataControllerTest {

    private DatabaseService database;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        database = mock(DatabaseService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new FinancialDataController(database))
                        .setControllerAdvice(new ApiSupport())
                        .build();
    }

    @Test
    void unsupportedSymbolsAreRejectedBeforeDatabaseAccess() throws Exception {
        mvc.perform(get("/api/finance/chart/UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unsupported_symbol"))
                .andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(database);
    }

    @Test
    void symbolsAreNormalizedBeforeDatabaseAccess() throws Exception {
        mvc.perform(get("/api/finance/chart/ibm")).andExpect(status().isOk());
        verify(database).getChartData("IBM");
        verifyNoMoreInteractions(database);
    }

    @Test
    void rankingSerializesRecordWithExistingJsonFields() throws Exception {
        var change = new StockChange("IBM", 2.5, LocalDate.of(2026, 10, 12), "IBM", "");
        when(database.getWinnersAndLosers(1))
                .thenReturn(Map.of("winners", List.of(change), "losers", List.of()));

        mvc.perform(get("/api/finance/winnersAndLosers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winners[0].symbol").value("IBM"))
                .andExpect(jsonPath("$.winners[0].percentageRate").value(2.5))
                .andExpect(jsonPath("$.winners[0].date").value("2026-10-12"))
                .andExpect(jsonPath("$.winners[0].name").value("IBM"))
                .andExpect(jsonPath("$.winners[0].industry").value(""))
                .andExpect(jsonPath("$.losers").isEmpty());
    }
}
