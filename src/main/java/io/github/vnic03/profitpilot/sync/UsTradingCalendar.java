package io.github.vnic03.profitpilot.sync;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

public final class UsTradingCalendar {

    private static final LocalDate SUPPORTED_FROM =
            LocalDate.of(2026, 1, 1);

    private static final LocalDate SUPPORTED_THROUGH =
            LocalDate.of(2028, 12, 31);

    // Full-day closures only. Early-close sessions remain trading days.
    // Source: https://www.nyse.com/trade/hours-calendars
    // Reviewed: 2026-10-12
    private static final Set<String> HOLIDAYS = Set.of(
            "2026-01-01",
            "2026-01-19",
            "2026-02-16",
            "2026-04-03",
            "2026-05-25",
            "2026-06-19",
            "2026-07-03",
            "2026-09-07",
            "2026-11-26",
            "2026-12-25",

            "2027-01-01",
            "2027-01-18",
            "2027-02-15",
            "2027-03-26",
            "2027-05-31",
            "2027-06-18",
            "2027-07-05",
            "2027-09-06",
            "2027-11-25",
            "2027-12-24",

            "2028-01-17",
            "2028-02-21",
            "2028-04-14",
            "2028-05-29",
            "2028-06-19",
            "2028-07-04",
            "2028-09-04",
            "2028-11-23",
            "2028-12-25"
    );

    private UsTradingCalendar() {
    }

    public static boolean supports(LocalDate date) {
        return !date.isBefore(SUPPORTED_FROM)
                && !date.isAfter(SUPPORTED_THROUGH);
    }

    public static Optional<LocalDate> latestSessionOnOrBefore(
            LocalDate date) {

        if (!supports(date)) {
            return Optional.empty();
        }

        LocalDate candidate = date;

        while (supports(candidate)) {
            DayOfWeek weekday = candidate.getDayOfWeek();

            boolean weekend = weekday == DayOfWeek.SATURDAY
                    || weekday == DayOfWeek.SUNDAY;

            if (!weekend && !HOLIDAYS.contains(candidate.toString())) {
                return Optional.of(candidate);
            }

            candidate = candidate.minusDays(1);
        }

        return Optional.empty();
    }
}
