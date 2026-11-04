package com.gathin.tvhome;

import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * How Home looks during one seasonal event, from the theme's {@code "seasonal" → "looks" → "<event id>"} object:
 * <pre>
 * "looks": {
 *   "halloween": { "accent": "auto", "greeting": "Happy Halloween", "icons": true, "effect": "always" }
 * }
 * </pre>
 * <ul>
 *   <li>{@code accent}: {@code "auto"} picks, for each wallpaper, whichever of the event's two colours stands out from
 *       it (Halloween: witch purple on a warm picture, pumpkin orange on a cool one; Christmas: ice blue on a warm
 *       one, red on a cool one); {@code "#RRGGBB"} is one fixed colour; {@code "off"} keeps the plain white glow.</li>
 *   <li>{@code greeting}: text in place of the time-of-day greeting, or {@code false} to keep that greeting.</li>
 *   <li>{@code icons}: the event's own icons for Inputs, Wi-Fi and Settings, if it has a set.</li>
 *   <li>{@code effect}: how often the event's effect plays when Home opens (Halloween's bats): {@code "off"},
 *       {@code "daily"} (the first Home of the day), {@code "after_break"} (Home was away for an hour or more) or
 *       {@code "always"}.</li>
 *   <li>{@code snow}: quiet snow falling behind the tiles, with a few large flakes in front; {@code snow_flakes} how
 *       many (0 to {@link #MAX_FLAKES}); {@code snow_piles}: the snow settles on the top edges of the buttons, the
 *       dock and the tiles, from bare at each switch-on. See SnowLayer.</li>
 * </ul>
 * Built-in events have their own defaults (Halloween: all on, the bats on every Home; Christmas: accent, greeting and
 * icons, snow that piles up, no effect); a theme changes single keys and the rest keep them. Events without built-in defaults start with
 * everything off. A key of the wrong type or with an unknown value keeps its default.
 */
final class SeasonalLook {

    /** The built-in events, by the id a theme names them with. */
    static final String HALLOWEEN = "halloween", CHRISTMAS = "christmas";

    enum Effect { OFF, DAILY, AFTER_BREAK, ALWAYS }

    /** Accent kinds: none, one fixed colour, or chosen per wallpaper. */
    static final int ACCENT_OFF = 0, ACCENT_FIXED = 1, ACCENT_AUTO = 2;

    static final int PUMPKIN_ORANGE = 0xFFFF8A1F;
    static final int WITCH_PURPLE = 0xFFA96BFF;
    static final int CHRISTMAS_RED = 0xFFFF4F5E;
    static final int ICE_BLUE = 0xFF8FD8FF;

    /** Flakes behind the tiles by default, and the most a theme may ask for. */
    static final int DEFAULT_FLAKES = 90, MAX_FLAKES = 300;

    /** Nothing seasonal: plain Home. */
    static final SeasonalLook NONE = new SeasonalLook(null, ACCENT_OFF, 0, 0, 0, null, false, Effect.OFF, false,
            DEFAULT_FLAKES, false);

    /** The event this look belongs to, or null for {@link #NONE}. */
    final String event;
    final int accentKind;
    /** The colour for {@link #ACCENT_FIXED}, as 0xAARRGGBB. */
    final int accent;
    /** For {@link #ACCENT_AUTO}: the accent on a mostly warm picture, and on a mostly cool one. */
    final int onWarm, onCool;
    /** The greeting; null keeps the time-of-day greeting, "" uses the event's built-in greeting text. */
    final String greeting;
    final boolean icons;
    final Effect effect;
    final boolean snow;
    final int snowFlakes;
    final boolean snowPiles;

    SeasonalLook(String event, int accentKind, int accent, int onWarm, int onCool, String greeting, boolean icons,
                 Effect effect, boolean snow, int snowFlakes, boolean snowPiles) {
        this.event = event;
        this.accentKind = accentKind;
        this.accent = accent;
        this.onWarm = onWarm;
        this.onCool = onCool;
        this.greeting = greeting;
        this.icons = icons;
        this.effect = effect;
        this.snow = snow;
        this.snowFlakes = snowFlakes;
        this.snowPiles = snowPiles;
    }

    /** The look an event has without any theme settings. */
    static SeasonalLook defaults(String event) {
        if (event == null) return NONE;
        if (HALLOWEEN.equals(event)) {
            return new SeasonalLook(event, ACCENT_AUTO, 0, WITCH_PURPLE, PUMPKIN_ORANGE, "", true, Effect.ALWAYS,
                    false, DEFAULT_FLAKES, false);
        }
        if (CHRISTMAS.equals(event)) {
            return new SeasonalLook(event, ACCENT_AUTO, 0, ICE_BLUE, CHRISTMAS_RED, "", true, Effect.OFF,
                    true, DEFAULT_FLAKES, true);
        }
        return new SeasonalLook(event, ACCENT_OFF, 0, PUMPKIN_ORANGE, WITCH_PURPLE, null, false, Effect.OFF,
                false, DEFAULT_FLAKES, false);
    }

    /**
     * The event's defaults with the theme's values over them. Each argument is the raw JSON value, or null when the
     * key is missing.
     */
    static SeasonalLook build(String event, Object accent, Object greeting, Object icons, Object effect, Object snow,
                              Object snowFlakes, Object snowPiles) {
        SeasonalLook d = defaults(event);
        if (d == NONE) return NONE;
        int kind = d.accentKind, colour = d.accent;
        if (accent instanceof String) {
            String a = ((String) accent).trim().toLowerCase(Locale.ROOT);
            if ("auto".equals(a)) {
                kind = ACCENT_AUTO;
            } else if ("off".equals(a)) {
                kind = ACCENT_OFF;
            } else if (Festival.parseColor(a) != 0) {
                kind = ACCENT_FIXED;
                colour = Festival.parseColor(a);
            }
        }
        String text = d.greeting;
        if (Boolean.FALSE.equals(greeting)) text = null;
        else if (Boolean.TRUE.equals(greeting)) text = "";
        else if (greeting instanceof String) text = Cards.cleanLabel((String) greeting, "");
        boolean ic = icons instanceof Boolean ? (Boolean) icons : d.icons;
        Effect ef = d.effect;
        if (effect instanceof String) {
            switch (((String) effect).trim().toLowerCase(Locale.ROOT)) {
                case "off": ef = Effect.OFF; break;
                case "daily": ef = Effect.DAILY; break;
                case "after_break": ef = Effect.AFTER_BREAK; break;
                case "always": ef = Effect.ALWAYS; break;
                default: break;
            }
        }
        boolean sn = snow instanceof Boolean ? (Boolean) snow : d.snow;
        int flakes = snowFlakes instanceof Integer && (Integer) snowFlakes >= 0 && (Integer) snowFlakes <= MAX_FLAKES
                ? (Integer) snowFlakes : d.snowFlakes;
        boolean piles = snowPiles instanceof Boolean ? (Boolean) snowPiles : d.snowPiles;
        return new SeasonalLook(event, kind, colour, d.onWarm, d.onCool, text, ic, ef, sn, flakes, piles);
    }

    /** The look for {@code event} from config.json's {@code "seasonal"} block; {@link #NONE} when there is no event. */
    static SeasonalLook fromJson(JSONObject block, String event) {
        if (event == null) return NONE;
        JSONObject looks = block == null ? null : block.optJSONObject("looks");
        JSONObject o = looks == null ? null : looks.optJSONObject(event);
        if (o == null) return defaults(event);
        return build(event, o.opt("accent"), o.opt("greeting"), o.opt("icons"), o.opt("effect"), o.opt("snow"),
                o.opt("snow_flakes"), o.opt("snow_piles"));
    }

    boolean isNone() {
        return event == null;
    }

    // ---- Christmas greetings by date ----------------------------------------------------------------

    /** Which Christmas greeting a day gets: the Advent season's, a Sunday of Advent's, or Christmas itself. */
    static final int GREET_SEASON = 0, GREET_CHRISTMAS = 5;

    /**
     * The Christmas greeting for {@code day}: {@link #GREET_CHRISTMAS} from 24 to 26 December, 1 to 4 on the four
     * Sundays of Advent (the 4th to the 1st Sunday before Christmas Day), {@link #GREET_SEASON} on every other day.
     */
    static int christmasGreeting(LocalDate day) {
        int m = day.getMonthValue(), d = day.getDayOfMonth();
        if (m == 12 && d >= 24 && d <= 26) return GREET_CHRISTMAS;
        if (day.getDayOfWeek() != DayOfWeek.SUNDAY) return GREET_SEASON;
        LocalDate fourth = LocalDate.of(day.getYear(), 12, 24).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        long weeks = ChronoUnit.WEEKS.between(day, fourth);
        return weeks >= 0 && weeks <= 3 ? (int) (4 - weeks) : GREET_SEASON;
    }

    /** Home away this long counts as a break for {@link Effect#AFTER_BREAK}. */
    static final long BREAK_MS = 60 * 60_000L;

    /**
     * Whether the effect plays now: never when off; daily on the first Home of a day ({@code lastDay} is the day it
     * last played, "" for never); after a break when Home was away at least {@link #BREAK_MS}, or nothing is known
     * of its last visit ({@code hiddenAt} 0, a fresh start); always on every Home.
     */
    static boolean effectDue(Effect mode, LocalDate today, String lastDay, long now, long hiddenAt) {
        switch (mode) {
            case DAILY:
                return !today.toString().equals(lastDay);
            case AFTER_BREAK:
                return hiddenAt == 0 || now - hiddenAt >= BREAK_MS;
            case ALWAYS:
                return true;
            default:
                return false;
        }
    }

    // ---- automatic accent ---------------------------------------------------------------------------

    /**
     * The contrasting accent for a picture, from a sample of its pixels (0xRRGGBB, any alpha): {@code onWarm} when
     * the picture's saturated, brighter pixels are mostly warm (orange, red), {@code onCool} when they are mostly cool.
     */
    static int contrastAccent(int[] pixels, int count, int onWarm, int onCool) {
        float warm = 0, cool = 0;
        float[] hs = new float[2];
        for (int i = 0; i < count; i++) {
            int p = pixels[i];
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            hueSat(r, g, b, hs);
            float w = hs[1] * Math.max(r, Math.max(g, b)) / 255f;
            if (hs[0] < 70 || hs[0] >= 330) warm += w;
            else if (hs[0] >= 200) cool += w;
        }
        return warm > cool ? onWarm : onCool;
    }

    /**
     * The picture's strongest colour, lifted to a bright, saturated one (0xAARRGGBB): the 20-degree hue band that
     * the saturated, brighter pixels fall in most, at 92 % saturation and 63 % lightness. Null-safe for an empty
     * sample, which gives the plain orange.
     */
    static int matchingColour(int[] pixels, int count) {
        float[] bins = new float[18];
        float[] hs = new float[2];
        boolean any = false;
        for (int i = 0; i < count; i++) {
            int p = pixels[i];
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            hueSat(r, g, b, hs);
            float w = hs[1] * hs[1] * Math.max(r, Math.max(g, b)) / 255f;
            if (w > 0) any = true;
            bins[((int) (hs[0] / 20)) % 18] += w;
        }
        if (!any) return PUMPKIN_ORANGE;
        int best = 0;
        for (int i = 1; i < bins.length; i++) if (bins[i] > bins[best]) best = i;
        return hsl(best * 20 + 10, 0.92f, 0.63f);
    }

    /** Hue in degrees (0 to 360) and saturation (0 to 1) into {@code out}. */
    static void hueSat(int r, int g, int b, float[] out) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float mx = Math.max(rf, Math.max(gf, bf)), mn = Math.min(rf, Math.min(gf, bf)), d = mx - mn;
        float h = 0;
        if (d > 0) {
            if (mx == rf) h = ((gf - bf) / d) % 6;
            else if (mx == gf) h = (bf - rf) / d + 2;
            else h = (rf - gf) / d + 4;
        }
        h *= 60;
        if (h < 0) h += 360;
        out[0] = h;
        out[1] = mx > 0 ? d / mx : 0;
    }

    static int hsl(float h, float s, float l) {
        float a = s * Math.min(l, 1 - l);
        return 0xFF000000 | (channel(0, h, l, a) << 16) | (channel(8, h, l, a) << 8) | channel(4, h, l, a);
    }

    private static int channel(int n, float h, float l, float a) {
        float k = (n + h / 30) % 12;
        return Math.round(255 * (l - a * Math.max(-1, Math.min(k - 3, Math.min(9 - k, 1)))));
    }
}
