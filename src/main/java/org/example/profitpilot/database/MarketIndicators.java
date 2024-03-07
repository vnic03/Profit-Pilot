package org.example.profitpilot.database;

public record MarketIndicators
        (String date, double sma, double rsi, double ema, double macd,double signalLine)
{

}
