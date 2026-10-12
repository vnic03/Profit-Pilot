package org.example.profitpilot.controller;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.example.profitpilot.database.DatabaseService;
import org.example.profitpilot.database.MarketIndicators;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import weka.core.Instances;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FinancialExportControllerTest {

    private DatabaseService database;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        database = mock(DatabaseService.class);

        mvc = MockMvcBuilders.standaloneSetup(
                new FinancialExportController(database)
        ).build();
    }

    @Test
    void downloadNormalizesSymbolAndReturnsReadableAttachment()
            throws Exception {

        when(database.queryFinancialDataForSymbol("IBM"))
                .thenReturn(List.of(
                        new MarketIndicators(
                                "2026-10-05",
                                105.5,
                                60.0,
                                104.5,
                                2.0,
                                1.5
                        )
                ));

        var result = mvc.perform(get("/api/finance/export/ibm"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        MediaType.APPLICATION_OCTET_STREAM
                ))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"IBM-indicators.arff\""
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();

        assertEquals(
                Integer.toString(bytes.length),
                result.getResponse().getHeader(HttpHeaders.CONTENT_LENGTH)
        );

        try (var reader = new StringReader(
                new String(bytes, StandardCharsets.UTF_8))) {

            Instances data = new Instances(reader);

            assertEquals(1, data.numInstances());
            assertEquals(6, data.numAttributes());
            assertEquals(105.5, data.instance(0).value(1), 0.000001);
            assertEquals(60.0, data.instance(0).value(2), 0.000001);
            assertEquals(1.5, data.instance(0).value(5), 0.000001);
        }

        verify(database).queryFinancialDataForSymbol("IBM");
        verifyNoMoreInteractions(database);
    }

    @Test
    void unsupportedSymbolIsRejectedBeforeDatabaseAccess()
            throws Exception {

        mvc.perform(get("/api/finance/export/UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("unsupported_symbol"))
                .andExpect(header().doesNotExist(
                        HttpHeaders.CONTENT_DISPOSITION
                ));

        verifyNoInteractions(database);
    }

    @Test
    void insufficientDataReturnsExplanationInsteadOfFile()
            throws Exception {

        when(database.queryFinancialDataForSymbol("IBM"))
                .thenReturn(List.of());

        mvc.perform(get("/api/finance/export/IBM"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code")
                        .value("insufficient_data"))
                .andExpect(header().doesNotExist(
                        HttpHeaders.CONTENT_DISPOSITION
                ));
    }

    @Test
    void databaseFailureReturnsGenericError()
            throws Exception {

        when(database.queryFinancialDataForSymbol("IBM"))
                .thenThrow(new IllegalStateException(
                        "Internal database connection details"
                ));

        mvc.perform(get("/api/finance/export/IBM"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code")
                        .value("export_failed"))
                .andExpect(jsonPath("$.message").value(
                        "Could not prepare the export. Please try again."
                ))
                .andExpect(header().doesNotExist(
                        HttpHeaders.CONTENT_DISPOSITION
                ));
    }
}
