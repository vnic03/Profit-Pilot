package io.github.vnic03.profitpilot.api.alphavantage;

import java.util.Map;

public record ApiResponse(Map<String, DailyData> timeSeries) {

    public record DailyData(double open, double high, double low, double close, long volume) {}
}
