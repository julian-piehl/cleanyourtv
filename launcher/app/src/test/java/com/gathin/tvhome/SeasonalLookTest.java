package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

public class SeasonalLookTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 31);

    @Test
    public void halloweenHasEverythingOnByDefault() {
        SeasonalLook l = SeasonalLook.defaults("halloween");
        assertEquals(SeasonalLook.ACCENT_AUTO, l.accentKind);
        assertEquals("", l.greeting);             // the built-in "Happy Halloween"
        assertTrue(l.icons);
        assertEquals(SeasonalLook.Effect.ALWAYS, l.effect);
    }

    @Test
    public void otherEventsStartWithEverythingOff() {
        SeasonalLook l = SeasonalLook.defaults("birthday");
        assertEquals(SeasonalLook.ACCENT_OFF, l.accentKind);
        assertNull(l.greeting);
        assertFalse(l.icons);
        assertEquals(SeasonalLook.Effect.OFF, l.effect);
    }

    @Test
    public void noEventIsNone() {
        assertSame(SeasonalLook.NONE, SeasonalLook.defaults(null));
        assertSame(SeasonalLook.NONE, SeasonalLook.build(null, "auto", "Hi", true, "always", null, null, null));
        assertTrue(SeasonalLook.NONE.isNone());
    }

    @Test
    public void themeValuesOverrideSingleKeys() {
        SeasonalLook l = SeasonalLook.build("halloween", "#102030", "  Boo! ", false, "after_break", null, null, null);
        assertEquals(SeasonalLook.ACCENT_FIXED, l.accentKind);
        assertEquals(0xFF102030, l.accent);
        assertEquals("Boo!", l.greeting);
        assertFalse(l.icons);
        assertEquals(SeasonalLook.Effect.AFTER_BREAK, l.effect);
    }

    @Test
    public void missingAndBrokenValuesKeepTheDefaults() {
        SeasonalLook l = SeasonalLook.build("halloween", "orange", 42, "yes", "sometimes", null, null, null);
        assertEquals(SeasonalLook.ACCENT_AUTO, l.accentKind);
        assertEquals("", l.greeting);
        assertTrue(l.icons);
        assertEquals(SeasonalLook.Effect.ALWAYS, l.effect);
        SeasonalLook m = SeasonalLook.build("halloween", null, null, null, null, null, null, null);
        assertEquals(SeasonalLook.ACCENT_AUTO, m.accentKind);
    }

    @Test
    public void greetingFalseKeepsTheTimeOfDayGreeting() {
        assertNull(SeasonalLook.build("halloween", null, false, null, null, null, null, null).greeting);
        assertEquals("", SeasonalLook.build("birthday", null, true, null, null, null, null, null).greeting);
    }

    @Test
    public void accentOffAndSwarmOff() {
        SeasonalLook l = SeasonalLook.build("halloween", " OFF ", null, null, "off", null, null, null);
        assertEquals(SeasonalLook.ACCENT_OFF, l.accentKind);
        assertEquals(SeasonalLook.Effect.OFF, l.effect);
    }

    @Test
    public void christmasHasItsColoursAndNoEffect() {
        SeasonalLook l = SeasonalLook.defaults(SeasonalLook.CHRISTMAS);
        assertEquals(SeasonalLook.ACCENT_AUTO, l.accentKind);
        assertEquals(SeasonalLook.ICE_BLUE, l.onWarm);
        assertEquals(SeasonalLook.CHRISTMAS_RED, l.onCool);
        assertEquals("", l.greeting);
        assertEquals(SeasonalLook.Effect.OFF, l.effect);
        assertEquals(SeasonalLook.CHRISTMAS_RED,
                SeasonalLook.contrastAccent(filled(0x1C2A6A, 10), 10, l.onWarm, l.onCool));
    }

    @Test
    public void snowIsChristmasOnlyAndCanBeChanged() {
        SeasonalLook x = SeasonalLook.defaults(SeasonalLook.CHRISTMAS);
        assertTrue(x.snow);
        assertTrue(x.snowPiles);
        assertEquals(SeasonalLook.DEFAULT_FLAKES, x.snowFlakes);
        assertFalse(SeasonalLook.defaults(SeasonalLook.HALLOWEEN).snow);
        SeasonalLook y = SeasonalLook.build(SeasonalLook.CHRISTMAS, null, null, null, null, false, 140, false);
        assertFalse(y.snow);
        assertFalse(y.snowPiles);
        assertEquals(140, y.snowFlakes);
        SeasonalLook z = SeasonalLook.build(SeasonalLook.CHRISTMAS, null, null, null, null, "yes", 9000, null);
        assertTrue(z.snow);
        assertEquals(SeasonalLook.DEFAULT_FLAKES, z.snowFlakes);
    }

    @Test
    public void christmasGreetingFollowsTheDate() {
        // 2026: Christmas Day is a Friday, the Sundays of Advent are 29 Nov, 6, 13 and 20 Dec.
        assertEquals(SeasonalLook.GREET_SEASON, SeasonalLook.christmasGreeting(LocalDate.of(2026, 11, 23)));
        assertEquals(SeasonalLook.GREET_SEASON, SeasonalLook.christmasGreeting(LocalDate.of(2026, 11, 22)));
        assertEquals(1, SeasonalLook.christmasGreeting(LocalDate.of(2026, 11, 29)));
        assertEquals(2, SeasonalLook.christmasGreeting(LocalDate.of(2026, 12, 6)));
        assertEquals(3, SeasonalLook.christmasGreeting(LocalDate.of(2026, 12, 13)));
        assertEquals(4, SeasonalLook.christmasGreeting(LocalDate.of(2026, 12, 20)));
        assertEquals(SeasonalLook.GREET_SEASON, SeasonalLook.christmasGreeting(LocalDate.of(2026, 12, 21)));
        assertEquals(SeasonalLook.GREET_CHRISTMAS, SeasonalLook.christmasGreeting(LocalDate.of(2026, 12, 24)));
        assertEquals(SeasonalLook.GREET_CHRISTMAS, SeasonalLook.christmasGreeting(LocalDate.of(2026, 12, 26)));
        // 2022: Christmas Day was a Sunday, so the 4th Sunday of Advent was 18 Dec, not Christmas Day itself.
        assertEquals(4, SeasonalLook.christmasGreeting(LocalDate.of(2022, 12, 18)));
        assertEquals(1, SeasonalLook.christmasGreeting(LocalDate.of(2022, 11, 27)));
        assertEquals(SeasonalLook.GREET_CHRISTMAS, SeasonalLook.christmasGreeting(LocalDate.of(2022, 12, 25)));
    }

    // ---- the effect's schedule ------------------------------------------------------------------------

    @Test
    public void dailyPlaysOnceADay() {
        assertTrue(SeasonalLook.effectDue(SeasonalLook.Effect.DAILY, DAY, "", 0, 0));
        assertTrue(SeasonalLook.effectDue(SeasonalLook.Effect.DAILY, DAY, "2026-10-30", 0, 0));
        assertFalse(SeasonalLook.effectDue(SeasonalLook.Effect.DAILY, DAY, "2026-10-31", 0, 0));
    }

    @Test
    public void afterABreakNeedsAnHourAwayOrAFreshStart() {
        long now = 10 * SeasonalLook.BREAK_MS;
        assertTrue(SeasonalLook.effectDue(SeasonalLook.Effect.AFTER_BREAK, DAY, "", now, 0));
        assertFalse(SeasonalLook.effectDue(SeasonalLook.Effect.AFTER_BREAK, DAY, "", now, now - 60_000));
        assertTrue(SeasonalLook.effectDue(SeasonalLook.Effect.AFTER_BREAK, DAY, "", now, now - SeasonalLook.BREAK_MS));
    }

    @Test
    public void alwaysAndOff() {
        assertTrue(SeasonalLook.effectDue(SeasonalLook.Effect.ALWAYS, DAY, "2026-10-31", 5, 4));
        assertFalse(SeasonalLook.effectDue(SeasonalLook.Effect.OFF, DAY, "", 0, 0));
    }

    // ---- colours from a picture ----------------------------------------------------------------------

    private static int[] filled(int rgb, int n) {
        int[] p = new int[n];
        java.util.Arrays.fill(p, rgb);
        return p;
    }

    @Test
    public void aWarmPictureGetsPurpleACoolOneOrange() {
        assertEquals(SeasonalLook.WITCH_PURPLE, SeasonalLook.contrastAccent(filled(0xC8602A, 100), 100, SeasonalLook.WITCH_PURPLE, SeasonalLook.PUMPKIN_ORANGE));
        assertEquals(SeasonalLook.PUMPKIN_ORANGE, SeasonalLook.contrastAccent(filled(0x1C2A6A, 100), 100, SeasonalLook.WITCH_PURPLE, SeasonalLook.PUMPKIN_ORANGE));
    }

    @Test
    public void blackCountsForNothing() {
        int[] p = filled(0x000000, 100);
        p[0] = 0x2040A0;   // one cool pixel among black ones
        assertEquals(SeasonalLook.PUMPKIN_ORANGE, SeasonalLook.contrastAccent(p, 100, SeasonalLook.WITCH_PURPLE, SeasonalLook.PUMPKIN_ORANGE));
    }

    @Test
    public void matchingTakesTheStrongestHueBrightened() {
        int c = SeasonalLook.matchingColour(filled(0x3A1F6A, 50), 50);   // a dark violet
        float[] hs = new float[2];
        SeasonalLook.hueSat((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, hs);
        assertTrue(hs[0] > 260 && hs[0] < 280);
        assertTrue(Math.max((c >> 16) & 0xFF, Math.max((c >> 8) & 0xFF, c & 0xFF)) > 200);
        assertEquals(SeasonalLook.PUMPKIN_ORANGE, SeasonalLook.matchingColour(filled(0x808080, 10), 10));
    }

    @Test
    public void hslRoundTrips() {
        assertEquals(0xFFFF0000, SeasonalLook.hsl(0, 1f, 0.5f));
        assertEquals(0xFF00FF00, SeasonalLook.hsl(120, 1f, 0.5f));
        assertEquals(0xFF0000FF, SeasonalLook.hsl(240, 1f, 0.5f));
    }
}
