package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads the {@code "seasonal"} block of a theme's config.json into a {@link SeasonalCalendar}:
 * <pre>
 * "seasonal": {
 *   "hemisphere": "north",
 *   "seasons": true,
 *   "events": ["halloween"],
 *   "custom_events": [
 *     { "id": "birthday", "from": "05-14", "to": "05-14" },
 *     { "id": "carnival", "easter": [-52, -47] },
 *     { "id": "mothers_day", "nth_weekday": { "month": 5, "weekday": "sun", "n": 2 } },
 *     { "id": "diwali", "dates": ["2026-11-08", "2027-10-29"] }
 *   ]
 * }
 * </pre>
 * Like the rest of the theme, everything is optional and nothing can break Home: a missing or malformed block
 * turns the feature off, a missing key keeps its default (seasons on, northern hemisphere, no events), and an
 * unknown event name or a malformed custom event is skipped on its own. Seasons are on as soon as the block is
 * there, so a seasonal theme always has something to show; {@code "seasons": false} switches them off.
 * <p>
 * The JSON walking stays in {@link #fromJson}; {@link #build} takes plain values so it can be unit-tested on the
 * JVM, where Android's org.json is only a stub.
 */
final class SeasonalConfig {

    static final String KEY = "seasonal";
    /** Most events one theme may watch, built-in and custom together. */
    static final int MAX_EVENTS = 64;
    /** Event ids double as wallpaper prefixes ({@code halloween-01.jpg}), so no hyphen, dot or upper case. */
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,40}");

    private SeasonalConfig() {
    }

    /** A theme's own event before validation: its id and its rule, null when the rule was malformed. */
    static final class Custom {
        final String id;
        final SeasonalRule rule;

        Custom(String id, SeasonalRule rule) {
            this.id = id;
            this.rule = rule;
        }
    }

    /**
     * The calendar for these settings; {@code seasons} null means the default, on. Built-in events are switched on by name in {@code enabled}; custom events
     * are on by being defined and come after them, and a custom event with a built-in event's id replaces that
     * built-in definition in place.
     */
    static SeasonalCalendar build(Boolean seasons, String hemisphere, List<String> enabled, List<Custom> custom) {
        Map<String, SeasonalRule> chosen = new LinkedHashMap<>();
        if (enabled != null) {
            for (String name : enabled) {
                String id = normalize(name);
                SeasonalRule rule = id == null ? null : SeasonalCalendar.BUILT_IN.get(id);
                if (rule != null && !chosen.containsKey(id)) chosen.put(id, rule);
            }
        }
        if (custom != null) {
            for (Custom c : custom) {
                String id = c == null ? null : normalize(c.id);
                if (id == null || c.rule == null) continue;
                chosen.put(id, c.rule);   // a built-in id keeps its place, with the theme's rule
            }
        }
        List<SeasonalCalendar.Event> events = new ArrayList<>();
        for (Map.Entry<String, SeasonalRule> e : chosen.entrySet()) {
            if (events.size() == MAX_EVENTS) break;
            events.add(new SeasonalCalendar.Event(e.getKey(), e.getValue()));
        }
        SeasonalCalendar.Hemisphere h = "south".equals(hemisphere == null ? "" : hemisphere.trim().toLowerCase(Locale.ROOT))
                ? SeasonalCalendar.Hemisphere.SOUTH : SeasonalCalendar.Hemisphere.NORTH;
        SeasonalCalendar calendar = new SeasonalCalendar(seasons == null || seasons, h, events);
        return calendar.isOff() ? SeasonalCalendar.OFF : calendar;
    }

    /** The calendar from a whole config.json, or {@link SeasonalCalendar#OFF} without a usable block. */
    static SeasonalCalendar fromJson(JSONObject root) {
        JSONObject block = root == null ? null : root.optJSONObject(KEY);
        if (block == null) return SeasonalCalendar.OFF;
        Object seasons = block.opt("seasons");
        Object hemisphere = block.opt("hemisphere");
        return build(seasons instanceof Boolean ? (Boolean) seasons : null,
                hemisphere instanceof String ? (String) hemisphere : null,
                strings(block.optJSONArray("events")),
                customEvents(block.optJSONArray("custom_events")));
    }

    private static List<Custom> customEvents(JSONArray list) {
        List<Custom> out = new ArrayList<>();
        if (list == null) return out;
        for (int i = 0; i < list.length() && out.size() < MAX_EVENTS; i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            Object id = o.opt("id");
            out.add(new Custom(id instanceof String ? (String) id : null, rule(o)));
        }
        return out;
    }

    /** The one rule an event object describes, or null if it describes none or more than one. */
    private static SeasonalRule rule(JSONObject o) {
        boolean fixed = o.has("from") || o.has("to"), easter = o.has("easter"), nth = o.has("nth_weekday"),
                dates = o.has("dates");
        if ((fixed ? 1 : 0) + (easter ? 1 : 0) + (nth ? 1 : 0) + (dates ? 1 : 0) != 1) return null;
        if (fixed) {
            Object from = o.opt("from"), to = o.opt("to");
            return from instanceof String && to instanceof String ? SeasonalRule.fixed((String) from, (String) to) : null;
        }
        if (easter) {
            JSONArray a = o.optJSONArray("easter");
            if (a == null || a.length() != 2 || !(a.opt(0) instanceof Integer) || !(a.opt(1) instanceof Integer)) {
                return null;
            }
            return SeasonalRule.easter((Integer) a.opt(0), (Integer) a.opt(1));
        }
        if (nth) {
            JSONObject w = o.optJSONObject("nth_weekday");
            if (w == null || !(w.opt("month") instanceof Integer) || !(w.opt("weekday") instanceof String)
                    || !(w.opt("n") instanceof Integer)) {
                return null;
            }
            return SeasonalRule.nthWeekday((Integer) w.opt("month"), (String) w.opt("weekday"), (Integer) w.opt("n"));
        }
        JSONArray a = o.optJSONArray("dates");
        if (a == null) return null;
        List<String> days = strings(a);
        return days.size() == a.length() ? SeasonalRule.dates(days) : null;   // a non-string entry spoils the list
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            Object v = a.opt(i);
            if (v instanceof String) out.add((String) v);
        }
        return out;
    }

    /** The id in canonical form, or null if it is not a valid id. */
    static String normalize(String id) {
        if (id == null) return null;
        String s = id.trim().toLowerCase(Locale.ROOT);
        return ID.matcher(s).matches() ? s : null;
    }
}
