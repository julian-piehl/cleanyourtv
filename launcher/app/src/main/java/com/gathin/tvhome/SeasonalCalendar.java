package com.gathin.tvhome;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides what a day looks like: the meteorological season and the seasonal event that wins it, if any.
 * <p>
 * Seasons follow the meteorological calendar, whole months: in the northern hemisphere spring is March to May,
 * summer June to August, autumn September to November and winter December to February; the southern hemisphere
 * is half a year apart. When several events fall on the same day, the one whose current occurrence is shorter
 * wins, since it is the more specific one (a birthday inside Advent); on a tie, the one listed first wins.
 * <p>
 * Built-in events are only definitions; a theme switches each one on by listing it. Events a theme defines itself
 * are on by being defined, and one with the id of a built-in event replaces that definition.
 */
final class SeasonalCalendar {

    enum Season {
        SPRING("spring"), SUMMER("summer"), AUTUMN("autumn"), WINTER("winter");

        /** The name used in a theme, e.g. as a wallpaper prefix. */
        final String id;

        Season(String id) {
            this.id = id;
        }
    }

    enum Hemisphere { NORTH, SOUTH }

    /** The events the launcher knows by name, in a stable order. */
    static final Map<String, SeasonalRule> BUILT_IN;

    static {
        Map<String, SeasonalRule> m = new LinkedHashMap<>();
        m.put("halloween", SeasonalRule.fixed("10-24", "11-01"));
        BUILT_IN = Collections.unmodifiableMap(m);
    }

    /** No seasons, no events: what a theme without a seasonal block gets. */
    static final SeasonalCalendar OFF = new SeasonalCalendar(false, Hemisphere.NORTH, Collections.emptyList());

    /** A named event and when it happens. */
    static final class Event {
        final String id;
        final SeasonalRule rule;

        Event(String id, SeasonalRule rule) {
            this.id = id;
            this.rule = rule;
        }
    }

    /** What a day looks like: its season (null when seasons are off) and its event id (null for none). */
    static final class Day {
        static final Day NOTHING = new Day(null, null);

        final Season season;
        final String event;

        Day(Season season, String event) {
            this.season = season;
            this.event = event;
        }
    }

    private final boolean seasons;
    private final Hemisphere hemisphere;
    private final List<Event> events;

    SeasonalCalendar(boolean seasons, Hemisphere hemisphere, List<Event> events) {
        this.seasons = seasons;
        this.hemisphere = hemisphere;
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
    }

    /** The events this calendar watches, in priority order for ties. */
    List<Event> events() {
        return events;
    }

    boolean isOff() {
        return !seasons && events.isEmpty();
    }

    Day on(LocalDate day) {
        if (isOff()) return Day.NOTHING;
        return new Day(seasons ? seasonOf(day, hemisphere) : null, eventOn(day));
    }

    /** The id of the event that wins {@code day}, or null if none takes place. */
    String eventOn(LocalDate day) {
        String best = null;
        long bestDays = Long.MAX_VALUE;
        for (Event e : events) {
            SeasonalRule.Occurrence o = e.rule.occurrenceOn(day);
            // Strictly shorter only, so the earlier event keeps a tie.
            if (o != null && o.days() < bestDays) {
                best = e.id;
                bestDays = o.days();
            }
        }
        return best;
    }

    static Season seasonOf(LocalDate day, Hemisphere hemisphere) {
        int m = day.getMonthValue();
        if (hemisphere == Hemisphere.SOUTH) m = (m + 5) % 12 + 1;   // six months on
        if (m >= 3 && m <= 5) return Season.SPRING;
        if (m >= 6 && m <= 8) return Season.SUMMER;
        if (m >= 9 && m <= 11) return Season.AUTUMN;
        return Season.WINTER;
    }
}
