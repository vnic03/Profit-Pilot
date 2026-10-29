package io.github.vnic03.profitpilot.controller;

import io.github.vnic03.profitpilot.catalog.StockCatalog;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "io.github.vnic03.profitpilot.controller")
public class ApiSupport {

    public record ErrorResponse(String code, String message) {}

    public static ResponseEntity<ErrorResponse> error(int status, String code, String message) {

        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ErrorResponse(code, message));
    }

    public static String requireSymbol(String symbol) {
        String normalized = StockCatalog.normalize(symbol);

        if (!StockCatalog.supports(normalized)) {
            throw new UnsupportedSymbolException();
        }

        return normalized;
    }

    @ExceptionHandler(UnsupportedSymbolException.class)
    public ResponseEntity<ErrorResponse> unsupportedSymbol() {
        return error(400, "unsupported_symbol", "This stock is not supported.");
    }

    private static final class UnsupportedSymbolException extends RuntimeException {}
}
