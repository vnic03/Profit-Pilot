package io.github.vnic03.profitpilot.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PriceFreshnessTest {

    @ParameterizedTest
    @CsvSource({
        "2026-10-14T20:10:00Z, 2026-10-13",
        "2026-10-15T09:59:59Z, 2026-10-13",
        "2026-10-15T10:00:00Z, 2026-10-14",
        "2026-10-11T16:00:00Z, 2026-10-09",
        "2026-10-12T09:59:00Z, 2026-10-09",
        "2026-10-13T09:59:00Z, 2026-10-09",
        "2026-10-13T10:00:00Z, 2026-10-12",
        "2026-11-03T10:59:00Z, 2026-10-30",
        "2026-11-03T11:00:00Z, 2026-11-02"
    })
    void referenceRespectsWeekendsCutoffAndDaylightSaving(String now, String expectedReference) {

        var result = PriceFreshness.assess(null, Instant.parse(now));

        assertEquals(LocalDate.parse(expectedReference), result.referenceDate());
    }

    @Test
    void missingPricesAreReported() {
        var result = PriceFreshness.assess(null, Instant.parse("2026-10-13T12:00:00Z"));

        assertEquals(PriceFreshness.Status.MISSING, result.status());
    }

    @Test
    void olderPricesNeedChecking() {
        var result =
                PriceFreshness.assess(
                        LocalDate.parse("2026-10-09"), Instant.parse("2026-10-13T12:00:00Z"));

        assertEquals(PriceFreshness.Status.BEHIND_REFERENCE, result.status());
    }

    @Test
    void MatchingReferenceIsCurrent() {
        var result =
                PriceFreshness.assess(
                        LocalDate.parse("2026-10-12"), Instant.parse("2026-10-13T12:00:00Z"));

        assertEquals(PriceFreshness.Status.CURRENT, result.status());
    }

    @Test
    void futurePricesAreNotConsideredCurrent() {
        var result =
                PriceFreshness.assess(
                        LocalDate.parse("2026-10-14"), Instant.parse("2026-10-13T12:00:00Z"));

        assertEquals(PriceFreshness.Status.FUTURE_DATE, result.status());
    }

    @ParameterizedTest
    @CsvSource({
        // Good Friday: Thursday remains the latest session.
        "2026-04-04T12:00:00Z, 2026-04-02",

        // Independence Day observed on Friday.
        "2026-07-04T12:00:00Z, 2026-07-02",

        // Tuesday after Labor Day.
        "2026-09-08T12:00:00Z, 2026-09-04",

        // Friday after Thanksgiving.
        "2026-11-27T12:00:00Z, 2026-11-25",

        // Friday's early-close session is due Saturday.
        "2026-11-28T12:00:00Z, 2026-11-27",

        // Christmas Eve is a trading session; Christmas is not.
        "2026-12-26T12:00:00Z, 2026-12-24",

        // New Year crosses the calendar-year boundary.
        "2027-01-02T12:00:00Z, 2026-12-31",

        // December 31, 2027 is not an observed New Year's closure.
        "2028-01-01T12:00:00Z, 2027-12-31",

        // Columbus Day remains a stock-market trading day.
        "2026-10-13T12:00:00Z, 2026-10-12"
    })
    void referenceUsesTradingSessions(String now, String expectedReference) {

        var result = PriceFreshness.assess(null, Instant.parse(now));

        assertEquals(LocalDate.parse(expectedReference), result.referenceDate());
    }

    @Test
    void holidayDoesNotMakePreviousSessionStale() {
        var result =
                PriceFreshness.assess(
                        LocalDate.parse("2026-11-25"), Instant.parse("2026-11-27T12:00:00Z"));

        assertEquals(PriceFreshness.Status.CURRENT, result.status());
    }

    @Test
    void unsupportedYearIsExplicitlyReported() {
        var result =
                PriceFreshness.assess(
                        LocalDate.parse("2028-12-29"), Instant.parse("2029-01-02T12:00:00Z"));

        assertEquals(PriceFreshness.Status.CALENDAR_UNAVAILABLE, result.status());
        assertNull(result.referenceDate());
    }

    @Test
    void referenceBeforeCalendarCoverageIsUnavailable() {
        var result = PriceFreshness.assess(null, Instant.parse("2026-01-02T12:00:00Z"));

        assertEquals(PriceFreshness.Status.CALENDAR_UNAVAILABLE, result.status());
        assertNull(result.referenceDate());
    }
}
