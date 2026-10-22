package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/** {@link SeasonalConfig#build}; the JSON walking in fromJson needs Android's org.json and is not run here. */
public class SeasonalConfigTest {

    private static final LocalDate HALLOWEEN = LocalDate.of(2026, 10, 31);

    private static List<String> ids(SeasonalCalendar c) {
        List<String> out = new ArrayList<>();
        for (SeasonalCalendar.Event e : c.events()) out.add(e.id);
        return out;
    }

    @Test
    public void nothingSetIsOff() {
        assertSame(SeasonalCalendar.OFF, SeasonalConfig.build(false, null, null, null));
        assertSame(SeasonalCalendar.OFF, SeasonalConfig.build(false, "north", Collections.<String>emptyList(),
                Collections.<SeasonalConfig.Custom>emptyList()));
    }

    @Test
    public void builtInEventsAreSwitchedOnByName() {
        SeasonalCalendar c = SeasonalConfig.build(false, null, Arrays.asList(" Halloween "), null);
        assertEquals(Collections.singletonList("halloween"), ids(c));
        assertEquals("halloween", c.on(HALLOWEEN).event);
        assertNull(c.on(HALLOWEEN).season);
    }

    @Test
    public void unknownAndRepeatedNamesAreSkipped() {
        SeasonalCalendar c = SeasonalConfig.build(false, null,
                Arrays.asList("halloween", "no_such_event", "halloween", "bad-id", null), null);
        assertEquals(Collections.singletonList("halloween"), ids(c));
    }

    @Test
    public void customEventsAreOnByBeingDefined() {
        SeasonalCalendar c = SeasonalConfig.build(false, null, null, Arrays.asList(
                new SeasonalConfig.Custom("birthday", SeasonalRule.fixed("05-14", "05-14"))));
        assertEquals("birthday", c.on(LocalDate.of(2026, 5, 14)).event);
    }

    @Test
    public void aCustomEventWithABuiltInIdReplacesItsDates() {
        SeasonalCalendar c = SeasonalConfig.build(false, null, Arrays.asList("halloween"), Arrays.asList(
                new SeasonalConfig.Custom("halloween", SeasonalRule.fixed("10-01", "10-31"))));
        assertEquals(Collections.singletonList("halloween"), ids(c));
        assertEquals("halloween", c.on(LocalDate.of(2026, 10, 5)).event);
        assertNull(c.on(LocalDate.of(2026, 11, 1)).event);
    }

    @Test
    public void malformedCustomEventsAreSkippedOnTheirOwn() {
        SeasonalCalendar c = SeasonalConfig.build(false, null, null, Arrays.asList(
                new SeasonalConfig.Custom("broken", null),
                new SeasonalConfig.Custom("Has-Hyphen", SeasonalRule.fixed("05-14", "05-14")),
                new SeasonalConfig.Custom(null, SeasonalRule.fixed("05-14", "05-14")),
                null,
                new SeasonalConfig.Custom("kept", SeasonalRule.fixed("05-15", "05-15"))));
        assertEquals(Collections.singletonList("kept"), ids(c));
    }

    @Test
    public void seasonsFollowTheHemisphere() {
        assertEquals(SeasonalCalendar.Season.AUTUMN, SeasonalConfig.build(true, null, null, null).on(HALLOWEEN).season);
        assertEquals(SeasonalCalendar.Season.AUTUMN,
                SeasonalConfig.build(true, "sideways", null, null).on(HALLOWEEN).season);
        assertEquals(SeasonalCalendar.Season.SPRING,
                SeasonalConfig.build(true, " South ", null, null).on(HALLOWEEN).season);
    }

    @Test
    public void theNumberOfEventsIsCapped() {
        List<SeasonalConfig.Custom> many = new ArrayList<>();
        for (int i = 0; i < SeasonalConfig.MAX_EVENTS + 10; i++) {
            many.add(new SeasonalConfig.Custom("e" + i, SeasonalRule.fixed("01-01", "01-01")));
        }
        assertEquals(SeasonalConfig.MAX_EVENTS, SeasonalConfig.build(false, null, null, many).events().size());
    }
}
