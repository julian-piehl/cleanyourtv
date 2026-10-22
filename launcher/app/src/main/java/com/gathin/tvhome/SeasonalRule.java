package com.gathin.tvhome;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * When a seasonal event takes place, as one of four kinds of rule:
 * <ul>
 *   <li>a fixed range of days every year, {@code "from": "10-24", "to": "11-01"}, which may run over New Year;</li>
 *   <li>a range around Western (Gregorian) Easter Sunday, {@code "easter": [-2, 1]} for Good Friday to Easter
 *       Monday;</li>
 *   <li>the n-th weekday of a month, {@code "nth_weekday": {"month": 5, "weekday": "sun", "n": 2}}, with
 *       {@code n} 1 to 5 or -1 for the last one;</li>
 *   <li>explicit days, {@code "dates": ["2026-11-08", "2027-10-29"]}, for festivals that follow a lunar calendar.</li>
 * </ul>
 * The factory methods return null for anything malformed, so a broken rule switches off its event and nothing
 * else. A rule answers one question: the occurrence that contains a given day, if any.
 */
final class SeasonalRule {

    /** Bounds for Easter offsets, a year either way; keeps the arithmetic far from overflow. */
    static final int MAX_EASTER_OFFSET = 366;
    /** Most explicit days one rule may list. */
    static final int MAX_DATES = 400;

    private enum Kind { FIXED, EASTER, NTH_WEEKDAY, DATES }

    private final Kind kind;
    // FIXED
    private final MonthDay from, to;
    // EASTER
    private final int startOffset, endOffset;
    // NTH_WEEKDAY
    private final int month, n;
    private final DayOfWeek weekday;
    // DATES
    private final List<LocalDate> dates;

    private SeasonalRule(Kind kind, MonthDay from, MonthDay to, int startOffset, int endOffset, int month, int n,
                         DayOfWeek weekday, List<LocalDate> dates) {
        this.kind = kind;
        this.from = from;
        this.to = to;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.month = month;
        this.n = n;
        this.weekday = weekday;
        this.dates = dates;
    }

    // ---- factories ---------------------------------------------------------------------------------

    /** Every year from {@code from} to {@code to} inclusive, both as {@code MM-DD}; null if either is malformed. */
    static SeasonalRule fixed(String from, String to) {
        MonthDay f = monthDay(from), t = monthDay(to);
        if (f == null || t == null) return null;
        return new SeasonalRule(Kind.FIXED, f, t, 0, 0, 0, 0, null, null);
    }

    /** From {@code start} to {@code end} days after Easter Sunday (negative is before); null if out of range. */
    static SeasonalRule easter(int start, int end) {
        if (start > end || Math.abs(start) > MAX_EASTER_OFFSET || Math.abs(end) > MAX_EASTER_OFFSET) return null;
        return new SeasonalRule(Kind.EASTER, null, null, start, end, 0, 0, null, null);
    }

    /**
     * The {@code n}-th {@code weekday} ("mon" to "sun") of {@code month} (1 to 12), {@code n} 1 to 5 or -1 for the
     * last; null if anything is out of range.
     */
    static SeasonalRule nthWeekday(int month, String weekday, int n) {
        DayOfWeek d = dayOfWeek(weekday);
        if (month < 1 || month > 12 || d == null || n == 0 || n < -1 || n > 5) return null;
        return new SeasonalRule(Kind.NTH_WEEKDAY, null, null, 0, 0, month, n, d, null);
    }

    /** Exactly these days, each as {@code YYYY-MM-DD}; null if the list is empty, too long or has a bad entry. */
    static SeasonalRule dates(List<String> days) {
        if (days == null || days.isEmpty() || days.size() > MAX_DATES) return null;
        List<LocalDate> parsed = new ArrayList<>(days.size());
        for (String s : days) {
            try {
                parsed.add(LocalDate.parse(s == null ? "" : s.trim()));
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        Collections.sort(parsed);
        return new SeasonalRule(Kind.DATES, null, null, 0, 0, 0, 0, null, Collections.unmodifiableList(parsed));
    }

    // ---- matching ----------------------------------------------------------------------------------

    /** The occurrence of this rule that contains {@code day}, or null if the event is not on that day. */
    Occurrence occurrenceOn(LocalDate day) {
        switch (kind) {
            case FIXED:
                return fixedOn(day);
            case EASTER:
                // An offset of up to a year either way can put the occurrence in a neighbouring Easter's year.
                for (int y = day.getYear() - 1; y <= day.getYear() + 1; y++) {
                    LocalDate sunday = easterSunday(y);
                    Occurrence o = new Occurrence(sunday.plusDays(startOffset), sunday.plusDays(endOffset));
                    if (o.contains(day)) return o;
                }
                return null;
            case NTH_WEEKDAY:
                if (day.getMonthValue() != month) return null;
                LocalDate first = day.withDayOfMonth(1);
                LocalDate hit = n == -1 ? first.with(TemporalAdjusters.lastInMonth(weekday))
                        : first.with(TemporalAdjusters.dayOfWeekInMonth(n, weekday));
                // A fifth weekday the month does not have rolls into the next month: no occurrence that year.
                return hit.getMonthValue() == month && hit.equals(day) ? new Occurrence(day, day) : null;
            case DATES:
                return Collections.binarySearch(dates, day) >= 0 ? new Occurrence(day, day) : null;
            default:
                return null;
        }
    }

    private Occurrence fixedOn(LocalDate day) {
        int y = day.getYear();
        if (!from.isAfter(to)) {
            Occurrence o = new Occurrence(from.atYear(y), to.atYear(y));
            return o.contains(day) ? o : null;
        }
        // Runs over New Year, e.g. 12-31 to 01-01: either the one that started this year or the one from last year.
        Occurrence thisYear = new Occurrence(from.atYear(y), to.atYear(y + 1));
        if (thisYear.contains(day)) return thisYear;
        Occurrence lastYear = new Occurrence(from.atYear(y - 1), to.atYear(y));
        return lastYear.contains(day) ? lastYear : null;
    }

    /** Western Easter Sunday of {@code year}: the anonymous Gregorian algorithm (Meeus/Jones/Butcher). */
    static LocalDate easterSunday(int year) {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, dayOfMonth = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, dayOfMonth);
    }

    private static MonthDay monthDay(String s) {
        if (s == null) return null;
        try {
            return MonthDay.parse("--" + s.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static DayOfWeek dayOfWeek(String s) {
        if (s == null) return null;
        switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "mon": return DayOfWeek.MONDAY;
            case "tue": return DayOfWeek.TUESDAY;
            case "wed": return DayOfWeek.WEDNESDAY;
            case "thu": return DayOfWeek.THURSDAY;
            case "fri": return DayOfWeek.FRIDAY;
            case "sat": return DayOfWeek.SATURDAY;
            case "sun": return DayOfWeek.SUNDAY;
            default: return null;
        }
    }

    /** One occurrence of an event, first to last day inclusive. */
    static final class Occurrence {
        final LocalDate start, end;

        Occurrence(LocalDate start, LocalDate end) {
            this.start = start;
            this.end = end;
        }

        boolean contains(LocalDate day) {
            return !day.isBefore(start) && !day.isAfter(end);
        }

        /** Length in days, counting both ends. */
        long days() {
            return ChronoUnit.DAYS.between(start, end) + 1;
        }
    }
}
