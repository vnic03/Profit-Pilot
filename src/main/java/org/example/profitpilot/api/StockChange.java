package org.example.profitpilot.api;

import java.time.LocalDate;

public record StockChange(String symbol, double percentageRate, LocalDate date) {
}
