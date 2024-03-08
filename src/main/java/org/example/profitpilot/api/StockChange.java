package org.example.profitpilot.api;

import java.time.LocalDate;

public class StockChange {

    private String symbol;
    private double percentageRate;
    private LocalDate date;
    private String name;
    private String industry;

    public StockChange(String symbol, double percentageRate, LocalDate date, String name, String industry) {
        this.symbol = symbol;
        this.percentageRate = percentageRate;
        this.date = date;
        this.name = name;
        this.industry = industry;
    }

    public StockChange(String symbol, double percentageRate, LocalDate date) {
        this.symbol = symbol;
        this.percentageRate = percentageRate;
        this.date = date;
    }

    public String getName() {
        return name;
    }

    public double getPercentageRate() {
        return percentageRate;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getIndustry() {
        return industry;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public void setPercentageRate(double percentageRate) {
        this.percentageRate = percentageRate;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
}

