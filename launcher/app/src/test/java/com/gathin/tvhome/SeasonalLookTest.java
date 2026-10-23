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
        assertEquals(SeasonalLook.Swarm.DAILY, l.swarm);
    }

    @Test
    public void otherEventsStartWithEverythingOff() {
        SeasonalLook l = SeasonalLook.defaults("birthday");
        assertEquals(SeasonalLook.ACCENT_OFF, l.accentKind);
        assertNull(l.greeting);
        assertFalse(l.icons);
        assertEquals(SeasonalLook.Swarm.OFF, l.swarm);
    }

    @Test
    public void noEventIsNone() {
        assertSame(SeasonalLook.NONE, SeasonalLook.defaults(null));
        assertSame(SeasonalLook.NONE, SeasonalLook.build(null, "auto", "Hi", true, "always"));
        assertTrue(SeasonalLook.NONE.isNone());
    }

    @Test
    public void themeValuesOverrideSingleKeys() {
        SeasonalLook l = SeasonalLook.build("halloween", "#102030", "  Boo! ", false, "after_break");
        assertEquals(SeasonalLook.ACCENT_FIXED, l.accentKind);
        assertEquals(0xFF102030, l.accent);
        assertEquals("Boo!", l.greeting);
        assertFalse(l.icons);
        assertEquals(SeasonalLook.Swarm.AFTER_BREAK, l.swarm);
    }

    @Test
    public void missingAndBrokenValuesKeepTheDefaults() {
        SeasonalLook l = SeasonalLook.build("halloween", "orange", 42, "yes", "sometimes");
        assertEquals(SeasonalLook.ACCENT_AUTO, l.accentKind);
        assertEquals("", l.greeting);
        assertTrue(l.icons);
        assertEquals(SeasonalLook.Swarm.DAILY, l.swarm);
        SeasonalLook m = SeasonalLook.build("halloween", null, null, null, null);
        assertEquals(SeasonalLook.ACCENT_AUTO, m.accentKind);
    }

    @Test
    public void greetingFalseKeepsTheTimeOfDayGreeting() {
        assertNull(SeasonalLook.build("halloween", null, false, null, null).greeting);
        assertEquals("", SeasonalLook.build("birthday", null, true, null, null).greeting);
    }

    @Test
    public void accentOffAndSwarmOff() {
        SeasonalLook l = SeasonalLook.build("halloween", " OFF ", null, null, "off");
        assertEquals(SeasonalLook.ACCENT_OFF, l.accentKind);
        assertEquals(SeasonalLook.Swarm.OFF, l.swarm);
    }

    // ---- the swarm's schedule ------------------------------------------------------------------------

    @Test
    public void dailyPlaysOnceADay() {
        assertTrue(SeasonalLook.swarmDue(SeasonalLook.Swarm.DAILY, DAY, "", 0, 0));
        assertTrue(SeasonalLook.swarmDue(SeasonalLook.Swarm.DAILY, DAY, "2026-10-30", 0, 0));
        assertFalse(SeasonalLook.swarmDue(SeasonalLook.Swarm.DAILY, DAY, "2026-10-31", 0, 0));
    }

    @Test
    public void afterABreakNeedsAnHourAwayOrAFreshStart() {
        long now = 10 * SeasonalLook.BREAK_MS;
        assertTrue(SeasonalLook.swarmDue(SeasonalLook.Swarm.AFTER_BREAK, DAY, "", now, 0));
        assertFalse(SeasonalLook.swarmDue(SeasonalLook.Swarm.AFTER_BREAK, DAY, "", now, now - 60_000));
        assertTrue(SeasonalLook.swarmDue(SeasonalLook.Swarm.AFTER_BREAK, DAY, "", now, now - SeasonalLook.BREAK_MS));
    }

    @Test
    public void alwaysAndOff() {
        assertTrue(SeasonalLook.swarmDue(SeasonalLook.Swarm.ALWAYS, DAY, "2026-10-31", 5, 4));
        assertFalse(SeasonalLook.swarmDue(SeasonalLook.Swarm.OFF, DAY, "", 0, 0));
    }

    // ---- colours from a picture ----------------------------------------------------------------------

    private static int[] filled(int rgb, int n) {
        int[] p = new int[n];
        java.util.Arrays.fill(p, rgb);
        return p;
    }

    @Test
    public void aWarmPictureGetsPurpleACoolOneOrange() {
        assertEquals(SeasonalLook.WITCH_PURPLE, SeasonalLook.contrastAccent(filled(0xC8602A, 100), 100));
        assertEquals(SeasonalLook.PUMPKIN_ORANGE, SeasonalLook.contrastAccent(filled(0x1C2A6A, 100), 100));
    }

    @Test
    public void blackCountsForNothing() {
        int[] p = filled(0x000000, 100);
        p[0] = 0x2040A0;   // one cool pixel among black ones
        assertEquals(SeasonalLook.PUMPKIN_ORANGE, SeasonalLook.contrastAccent(p, 100));
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
