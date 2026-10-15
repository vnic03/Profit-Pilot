package org.example.profitpilot.sync;

import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class ImportRunStoreTest {

    @Test
    void finishedRunSurvivesNewStoreInstanceAndFailureDoesNotReturnStaleSuccess() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:runstore;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE import_run_status (id INTEGER PRIMARY KEY, payload TEXT)");
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var first = new ImportRunStore(jdbc, mapper);
        // H2 does not implement PostgreSQL ON CONFLICT; verify read persistence with the exact payload shape.
        var run = new ImportRunStore.Run(Instant.now(), Instant.now(), "COMPLETED",
                new PriceImportService.ImportReport(PriceImportService.RunStatus.COMPLETED, 1, 0, 0, 0),
                Map.of("IBM", "IMPORTED"));
        try {
            jdbc.update("INSERT INTO import_run_status VALUES (1, ?)", mapper.writeValueAsString(run));
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
        assertEquals(run, new ImportRunStore(jdbc, mapper).latest());
        jdbc.execute("DROP TABLE import_run_status");
        assertDoesNotThrow(() -> first.record(Instant.now(), run.report(), run.outcomes()));
        assertThrows(IllegalStateException.class, first::latest);
    }
}
