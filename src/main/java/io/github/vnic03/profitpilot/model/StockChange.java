package io.github.vnic03.profitpilot.model;

import java.time.LocalDate;

public record StockChange(
        String symbol, double percentageRate, LocalDate date, String name, String industry) {}
