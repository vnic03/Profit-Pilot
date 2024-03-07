package org.example.profitpilot.api;

import java.util.Map;

public record ApiResponse(Map<String, DailyData> timeSeries) {

    public record DailyData
            (double open, double high, double low, double close, long volume) { }

}
