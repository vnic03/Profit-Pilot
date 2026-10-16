package org.example.profitpilot.sync;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public final class PriceFreshness {

    private static final ZoneId MARKET_ZONE =
            ZoneId.of("America/New_York");

    // Allow overnight publication time; this is our policy, not a provider guarantee.
    private static final LocalTime CHECK_TIME =
            LocalTime.of(6, 0);

    private PriceFreshness() { }

    public enum Status {
            CURRENT,
            BEHIND_REFERENCE,
            MISSING,
            FUTURE_DATE,
            CALENDAR_UNAVAILABLE
    }

    public record Assessment(
            Status status,
            LocalDate referenceDate) {
    }

    public static Assessment assess(LocalDate latestPriceDate, Instant now) {
        var marketTime = now.atZone(MARKET_ZONE);
        LocalDate today = marketTime.toLocalDate();

        if (latestPriceDate != null && latestPriceDate.isAfter(today)) {
            return new Assessment(Status.FUTURE_DATE, null);
        }

        // Do not guess freshness beyond the dates covered by our holiday calendar.
        if (!UsTradingCalendar.supports(today)) {
            return new Assessment(Status.CALENDAR_UNAVAILABLE, null);
        }

        LocalDate candidate = today.minusDays(1);

        if (marketTime.toLocalTime().isBefore(CHECK_TIME)) {
            candidate = candidate.minusDays(1);
        }

        var reference = UsTradingCalendar.latestSessionOnOrBefore(candidate);

        if (reference.isEmpty()) {
            return new Assessment(Status.CALENDAR_UNAVAILABLE, null);
        }

        LocalDate referenceDate = reference.get();

        if (latestPriceDate == null) {
            return new Assessment(Status.MISSING, referenceDate);
        }

        Status status = latestPriceDate.isBefore(referenceDate)
                ? Status.BEHIND_REFERENCE
                : Status.CURRENT;

        return new Assessment(status, referenceDate);
    }
}
