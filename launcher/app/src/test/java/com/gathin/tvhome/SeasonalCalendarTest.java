package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;
import java.util.Arrays;

import org.junit.Test;

public class SeasonalCalendarTest {

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    // ---- rules -------------------------------------------------------------------------------------

    @Test
    public void easterSundayMatchesKnownDates() {
        assertEquals(d("2024-03-31"), SeasonalRule.easterSunday(2024));
        assertEquals(d("2025-04-20"), SeasonalRule.easterSunday(2025));
        assertEquals(d("2026-04-05"), SeasonalRule.easterSunday(2026));
        assertEquals(d("2027-03-28"), SeasonalRule.easterSunday(2027));
        assertEquals(d("2038-04-25"), SeasonalRule.easterSunday(2038));   // the latest possible date
        assertEquals(d("2285-03-22"), SeasonalRule.easterSunday(2285));   // the earliest possible date
    }

    @Test
    public void fixedRangeIsInclusive() {
        SeasonalRule halloween = SeasonalRule.fixed("10-24", "11-01");
        assertNull(halloween.occurrenceOn(d("2026-10-23")));
        assertNotNull(halloween.occurrenceOn(d("2026-10-24")));
        assertNotNull(halloween.occurrenceOn(d("2026-11-01")));
        assertNull(halloween.occurrenceOn(d("2026-11-02")));
        assertEquals(9, halloween.occurrenceOn(d("2026-10-31")).days());
    }

    @Test
    public void fixedRangeRunsOverNewYear() {
        SeasonalRule newYear = SeasonalRule.fixed("12-31", "01-01");
        SeasonalRule.Occurrence eve = newYear.occurrenceOn(d("2026-12-31"));
        SeasonalRule.Occurrence day = newYear.occurrenceOn(d("2027-01-01"));
        assertEquals(d("2027-01-01"), eve.end);
        assertEquals(d("2026-12-31"), day.start);
        assertNull(newYear.occurrenceOn(d("2027-01-02")));
        assertNull(newYear.occurrenceOn(d("2026-12-30")));
    }

    @Test
    public void easterOffsetsFollowTheYear() {
        SeasonalRule easter = SeasonalRule.easter(-2, 1);   // Good Friday to Easter Monday
        assertNotNull(easter.occurrenceOn(d("2026-04-03")));
        assertNotNull(easter.occurrenceOn(d("2026-04-06")));
        assertNull(easter.occurrenceOn(d("2026-04-07")));
        assertNotNull(easter.occurrenceOn(d("2027-03-26")));
    }

    @Test
    public void nthWeekdayFindsTheDayAndTheLastOne() {
        SeasonalRule mothersDay = SeasonalRule.nthWeekday(5, "sun", 2);
        assertNotNull(mothersDay.occurrenceOn(d("2026-05-10")));
        assertNull(mothersDay.occurrenceOn(d("2026-05-03")));
        SeasonalRule lastFridayOfNovember = SeasonalRule.nthWeekday(11, "FRI", -1);
        assertNotNull(lastFridayOfNovember.occurrenceOn(d("2026-11-27")));
        assertNull(lastFridayOfNovember.occurrenceOn(d("2026-11-20")));
    }

    @Test
    public void aFifthWeekdayTheMonthLacksDoesNotRollOver() {
        SeasonalRule fifthMonday = SeasonalRule.nthWeekday(2, "mon", 5);   // February 2026 has four Mondays
        for (LocalDate day = d("2026-02-01"); day.isBefore(d("2026-03-08")); day = day.plusDays(1)) {
            assertNull(day.toString(), fifthMonday.occurrenceOn(day));
        }
    }

    @Test
    public void explicitDatesMatchOnlyThoseDays() {
        SeasonalRule diwali = SeasonalRule.dates(Arrays.asList("2027-10-29", " 2026-11-08 "));
        assertNotNull(diwali.occurrenceOn(d("2026-11-08")));
        assertNotNull(diwali.occurrenceOn(d("2027-10-29")));
        assertNull(diwali.occurrenceOn(d("2026-11-09")));
    }

    @Test
    public void malformedRulesAreRefused() {
        assertNull(SeasonalRule.fixed("10-32", "11-01"));
        assertNull(SeasonalRule.fixed("October", "11-01"));
        assertNull(SeasonalRule.fixed(null, "11-01"));
        assertNull(SeasonalRule.easter(2, 1));
        assertNull(SeasonalRule.easter(0, 400));
        assertNull(SeasonalRule.nthWeekday(13, "sun", 1));
        assertNull(SeasonalRule.nthWeekday(5, "sunday", 1));
        assertNull(SeasonalRule.nthWeekday(5, "sun", 0));
        assertNull(SeasonalRule.nthWeekday(5, "sun", 6));
        assertNull(SeasonalRule.dates(Arrays.asList("2026-11-08", "08.11.2026")));
        assertNull(SeasonalRule.dates(Arrays.<String>asList()));
    }

    // ---- seasons -----------------------------------------------------------------------------------

    @Test
    public void meteorologicalSeasonsInTheNorth() {
        SeasonalCalendar.Hemisphere n = SeasonalCalendar.Hemisphere.NORTH;
        assertEquals(SeasonalCalendar.Season.WINTER, SeasonalCalendar.seasonOf(d("2026-02-28"), n));
        assertEquals(SeasonalCalendar.Season.SPRING, SeasonalCalendar.seasonOf(d("2026-03-01"), n));
        assertEquals(SeasonalCalendar.Season.SUMMER, SeasonalCalendar.seasonOf(d("2026-06-01"), n));
        assertEquals(SeasonalCalendar.Season.AUTUMN, SeasonalCalendar.seasonOf(d("2026-11-30"), n));
        assertEquals(SeasonalCalendar.Season.WINTER, SeasonalCalendar.seasonOf(d("2026-12-01"), n));
    }

    @Test
    public void theSouthIsHalfAYearApart() {
        SeasonalCalendar.Hemisphere s = SeasonalCalendar.Hemisphere.SOUTH;
        assertEquals(SeasonalCalendar.Season.SUMMER, SeasonalCalendar.seasonOf(d("2026-01-15"), s));
        assertEquals(SeasonalCalendar.Season.AUTUMN, SeasonalCalendar.seasonOf(d("2026-03-01"), s));
        assertEquals(SeasonalCalendar.Season.WINTER, SeasonalCalendar.seasonOf(d("2026-07-15"), s));
        assertEquals(SeasonalCalendar.Season.SPRING, SeasonalCalendar.seasonOf(d("2026-10-31"), s));
    }

    // ---- events ------------------------------------------------------------------------------------

    @Test
    public void theShorterEventWins() {
        SeasonalCalendar c = new SeasonalCalendar(false, SeasonalCalendar.Hemisphere.NORTH, Arrays.asList(
                new SeasonalCalendar.Event("halloween", SeasonalRule.fixed("10-24", "11-01")),
                new SeasonalCalendar.Event("birthday", SeasonalRule.fixed("10-28", "10-28"))));
        assertEquals("halloween", c.eventOn(d("2026-10-27")));
        assertEquals("birthday", c.eventOn(d("2026-10-28")));
        assertNull(c.eventOn(d("2026-11-02")));
    }

    @Test
    public void onATieTheFirstListedWins() {
        SeasonalCalendar c = new SeasonalCalendar(false, SeasonalCalendar.Hemisphere.NORTH, Arrays.asList(
                new SeasonalCalendar.Event("first", SeasonalRule.fixed("10-28", "10-29")),
                new SeasonalCalendar.Event("second", SeasonalRule.fixed("10-29", "10-30"))));
        assertEquals("first", c.eventOn(d("2026-10-29")));
    }

    @Test
    public void builtInHalloweenRunsFromThe24thToNovember1st() {
        SeasonalRule halloween = SeasonalCalendar.BUILT_IN.get("halloween");
        assertNull(halloween.occurrenceOn(d("2026-10-23")));
        assertNotNull(halloween.occurrenceOn(d("2026-10-24")));
        assertNotNull(halloween.occurrenceOn(d("2026-11-01")));
        assertNull(halloween.occurrenceOn(d("2026-11-02")));
    }

    @Test
    public void offShowsNothing() {
        SeasonalCalendar.Day day = SeasonalCalendar.OFF.on(d("2026-10-31"));
        assertNull(day.season);
        assertNull(day.event);
    }
}
