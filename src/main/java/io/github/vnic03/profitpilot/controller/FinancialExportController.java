package io.github.vnic03.profitpilot.controller;

import io.github.vnic03.profitpilot.database.ARFFConverter;
import io.github.vnic03.profitpilot.database.DatabaseService;
import io.github.vnic03.profitpilot.database.MarketIndicators;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance")
public class FinancialExportController {

    private static final Logger logger = LoggerFactory.getLogger(FinancialExportController.class);

    private final DatabaseService database;

    public FinancialExportController(DatabaseService database) {
        this.database = database;
    }

    @GetMapping("/export/{symbol}")
    public ResponseEntity<?> downloadArff(@PathVariable("symbol") String symbol) {

        String normalizedSymbol = ApiSupport.requireSymbol(symbol);

        try {
            List<MarketIndicators> rows = database.queryFinancialDataForSymbol(normalizedSymbol);

            if (rows.isEmpty()) {
                return ApiSupport.error(
                        422, "insufficient_data", "At least 34 stored daily prices are required.");
            }

            byte[] content = ARFFConverter.toBytes(rows);

            String disposition =
                    ContentDisposition.attachment()
                            .filename(normalizedSymbol + "-indicators.arff")
                            .build()
                            .toString();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(content.length)
                    .body(content);

        } catch (IllegalStateException e) {
            logger.error("Could not export indicators for {}.", normalizedSymbol, e);

            return ApiSupport.error(
                    500, "export_failed", "Could not prepare the export. Please try again.");
        }
    }
}
