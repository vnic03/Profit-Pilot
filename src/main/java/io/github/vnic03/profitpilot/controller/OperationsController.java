package io.github.vnic03.profitpilot.controller;

import java.time.Instant;
import java.util.Map;
import io.github.vnic03.profitpilot.sync.ImportRunStore;
import io.github.vnic03.profitpilot.sync.PriceImportService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OperationsController {
    private final ImportRunStore runs;
    private final PriceImportService imports;
    private final BuildProperties build;
    private final Instant startedAt = Instant.now();

    public OperationsController(
            ImportRunStore runs,
            PriceImportService imports,
            ObjectProvider<BuildProperties> build
    ) {
        this.runs = runs;
        this.imports = imports;
        this.build = build.getIfAvailable();
    }

    @GetMapping("/api/operations/build")
    public Map<String, Object> build() {
        return Map.of(
                "revision", build == null
                        ? "unknown"
                        : java.util.Objects.toString(build.get("revision"), "unknown"),
                "builtAt", build == null ? "unknown" : String.valueOf(build.getTime()),
                "startedAt", startedAt);
    }

    public record Status(boolean running, ImportRunStore.Run lastFinishedRun) { }

    @GetMapping("/api/operations/imports")
    public ResponseEntity<?> imports() {
        try {
            return ResponseEntity.ok(new Status(imports.isRunning(), runs.latest()));
        } catch (RuntimeException failure) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Import status unavailable. Check database and status migration."));
        }
    }
}
