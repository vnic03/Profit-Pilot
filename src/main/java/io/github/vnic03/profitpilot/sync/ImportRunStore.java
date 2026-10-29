package io.github.vnic03.profitpilot.sync;

import java.time.Instant;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Stores only structured outcomes, never provider URLs, keys or exception messages. */
@Component
public class ImportRunStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private volatile boolean lastWriteFailed;

    public ImportRunStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record Run(
            Instant startedAt,
            Instant finishedAt,
            String status,
            PriceImportService.ImportReport report,
            Map<String, String> outcomes) {}

    public void record(
            Instant start, PriceImportService.ImportReport report, Map<String, String> outcomes) {
        try {
            var run =
                    new Run(
                            start,
                            Instant.now(),
                            report == null ? "FAILED" : report.status().name(),
                            report,
                            Map.copyOf(outcomes));
            // Keep one latest report; the service journal holds older run details.
            jdbc.update(
                    """
                    INSERT INTO import_run_status (id, payload) VALUES (1, ?)
                    ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload
                    """,
                    mapper.writeValueAsString(run));
            lastWriteFailed = false;
        } catch (Exception failure) {
            lastWriteFailed = true;
            LoggerFactory.getLogger(getClass())
                    .error(
                            "Could not persist import run status; check database and migration.",
                            failure);
        }
    }

    public Run latest() {
        // Do not present an older report as current after a failed write.
        if (lastWriteFailed) {
            throw new IllegalStateException("Latest import status could not be persisted.");
        }
        return jdbc.query(
                "SELECT payload FROM import_run_status WHERE id = 1",
                result -> {
                    if (!result.next()) {
                        return null;
                    }
                    try {
                        return mapper.readValue(result.getString(1), Run.class);
                    } catch (Exception invalid) {
                        throw new IllegalStateException(
                                "Stored import status is unreadable.", invalid);
                    }
                });
    }
}
