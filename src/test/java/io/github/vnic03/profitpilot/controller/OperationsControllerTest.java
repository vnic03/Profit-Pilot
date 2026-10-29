package io.github.vnic03.profitpilot.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.vnic03.profitpilot.sync.ImportRunStore;
import io.github.vnic03.profitpilot.sync.PriceImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OperationsControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildRemainsReadableWhenDatabaseFailsAndStatusIsNotReportedAsEmpty() throws Exception {
        var store = mock(ImportRunStore.class);
        var imports = mock(PriceImportService.class);
        ObjectProvider<BuildProperties> build = mock(ObjectProvider.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(new OperationsController(store, imports, build))
                        .build();
        mvc.perform(get("/api/operations/build"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value("unknown"));
        mvc.perform(get("/api/operations/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false));
        when(store.latest()).thenThrow(new IllegalStateException("private database details"));
        mvc.perform(get("/api/operations/imports"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("import_status_unavailable"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        "private database details"))));
    }
}
