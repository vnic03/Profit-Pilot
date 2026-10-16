package io.github.vnic03.profitpilot.sync;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.vnic03.profitpilot.api.alphavantage.ApiClient;
import io.github.vnic03.profitpilot.catalog.StockCatalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PriceImportService {

    private static final Logger logger =
            LoggerFactory.getLogger(PriceImportService.class);

    private final ImportRunStore runStore;
    private final ApiClient apiClient;
    private final ImportPause pause;
    private final long requestDelayMs;
    private final String configuredSymbols;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public PriceImportService(
            ApiClient apiClient,
            ImportPause pause,
            ImportRunStore runStore,
            @Value("${profitpilot.sync.request-delay-ms:15000}")
            long requestDelayMs,
            @Value("${profitpilot.sync.symbols:${profitpilot.sync.symbol:}}")
            String configuredSymbols) {

        if (requestDelayMs < 15000) {
            throw new IllegalArgumentException(
                    "Import delay must be at least 15000 milliseconds."
            );
        }

        this.runStore = runStore;
        this.apiClient = apiClient;
        this.pause = pause;
        this.requestDelayMs = requestDelayMs;
        this.configuredSymbols = configuredSymbols;
    }

    public enum RunStatus {
        COMPLETED,
        DISABLED,
        ALREADY_RUNNING,
        INTERRUPTED
    }

    public record ImportReport(
            RunStatus status,
            int imported,
            int skipped,
            int failed,
            int waiting) {
    }

    public ImportReport runConfiguredImport() {
        // Prevent overlapping runs in this process; database locks protect the budget.
        if (!running.compareAndSet(false, true)) {
            return new ImportReport(
                    RunStatus.ALREADY_RUNNING, 0, 0, 0, 0
            );
        }

        java.time.Instant startedAt = java.time.Instant.now();
        var outcomes = new java.util.LinkedHashMap<String, String>();
        try {
            ImportReport report = executeImport(outcomes);
            runStore.record(startedAt, report, outcomes);
            return report;
        } catch (RuntimeException failure) {
            runStore.record(startedAt, null, outcomes);
            throw failure;
        } finally {
            running.set(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private ImportReport executeImport(java.util.Map<String, String> outcomes) {
        if (!apiClient.isImportEnabled()) {
            return new ImportReport(
                    RunStatus.DISABLED, 0, 0, 0, 0
            );
        }

        List<String> symbols = requestedSymbols();

        int imported = 0;
        int skipped = 0;
        int failed = 0;
        int waiting = 0;

        logger.info("Starting price import for {}.", symbols);

        for (int index = 0; index < symbols.size(); index++) {
            if (Thread.currentThread().isInterrupted()) {
                return new ImportReport(
                        RunStatus.INTERRUPTED,
                        imported, skipped, failed, waiting
                );
            }

            // Space every symbol check, even when the previous stock was skipped.
            if (index > 0) {
                try {
                    pause.await(requestDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();

                    return new ImportReport(
                            RunStatus.INTERRUPTED,
                            imported, skipped, failed, waiting
                    );
                }
            }

            String symbol = symbols.get(index);

            try {
                ApiClient.SyncResult result =
                        apiClient.syncDailyPrices(symbol);

                outcomes.put(symbol, result.name());
                switch (result) {
                    case IMPORTED -> imported++;
                    case WAITING_FOR_DATA -> waiting++;
                    case SKIPPED, BUDGET_OR_COOLDOWN -> skipped++;
                    case DISABLED -> {
                        return new ImportReport(
                                RunStatus.DISABLED,
                                imported, skipped, failed, waiting
                        );
                    }
                }
            } catch (Exception e) {
                failed++;
                outcomes.put(symbol, "FAILED");

                logger.error(
                        "Price import failed for {}. "
                                + "Continuing with remaining stocks.",
                        symbol,
                        e
                );
            }
        }

        RunStatus status = Thread.currentThread().isInterrupted()
                ? RunStatus.INTERRUPTED
                : RunStatus.COMPLETED;

        return new ImportReport(
                status, imported, skipped, failed, waiting
        );

    }

    private List<String> requestedSymbols() {
        List<String> symbols = Arrays.stream(
                        configuredSymbols.split(",", -1)
                )
                .map(StockCatalog::normalize)
                .distinct()
                .toList();

        for (String symbol : symbols) {
            if (!StockCatalog.supports(symbol)) {
                throw new IllegalArgumentException(
                        "Unsupported sync symbol: " + symbol
                                + ". Supported symbols: "
                                + StockCatalog.symbols()
                );
            }
        }

        return symbols;
    }
}
