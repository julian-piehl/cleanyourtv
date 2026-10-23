package com.gathin.tvhome;

import org.json.JSONObject;

import java.time.LocalDate;
import java.util.Locale;

/**
 * How Home looks during one seasonal event, from the theme's {@code "seasonal" → "looks" → "<event id>"} object:
 * <pre>
 * "looks": {
 *   "halloween": { "accent": "auto", "greeting": "Happy Halloween", "icons": true, "swarm": "daily" }
 * }
 * </pre>
 * <ul>
 *   <li>{@code accent}: {@code "auto"} picks, for each wallpaper, whichever of pumpkin orange and witch purple stands
 *       out from it; {@code "#RRGGBB"} is one fixed colour; {@code "off"} keeps the plain white focus glow.</li>
 *   <li>{@code greeting}: text in place of the time-of-day greeting, or {@code false} to keep that greeting.</li>
 *   <li>{@code icons}: the event's own icons for Inputs, Wi-Fi and Settings, if it has a set.</li>
 *   <li>{@code swarm}: how often bats fly out when Home opens: {@code "off"}, {@code "daily"} (the first Home of
 *       the day), {@code "after_break"} (Home was away for an hour or more) or {@code "always"}.</li>
 * </ul>
 * Built-in events have their own defaults (Halloween: all on, the swarm daily); a theme changes single keys and the
 * rest keep them. Events without built-in defaults start with everything off. A key of the wrong type or with an
 * unknown value keeps its default.
 */
final class SeasonalLook {

    enum Swarm { OFF, DAILY, AFTER_BREAK, ALWAYS }

    /** Accent kinds: none, one fixed colour, or chosen per wallpaper. */
    static final int ACCENT_OFF = 0, ACCENT_FIXED = 1, ACCENT_AUTO = 2;

    static final int PUMPKIN_ORANGE = 0xFFFF8A1F;
    static final int WITCH_PURPLE = 0xFFA96BFF;

    /** Nothing seasonal: plain Home. */
    static final SeasonalLook NONE = new SeasonalLook(null, ACCENT_OFF, 0, null, false, Swarm.OFF);

    /** The event this look belongs to, or null for {@link #NONE}. */
    final String event;
    final int accentKind;
    /** The colour for {@link #ACCENT_FIXED}, as 0xAARRGGBB. */
    final int accent;
    /** The greeting; null keeps the time-of-day greeting, "" uses the event's built-in greeting text. */
    final String greeting;
    final boolean icons;
    final Swarm swarm;

    SeasonalLook(String event, int accentKind, int accent, String greeting, boolean icons, Swarm swarm) {
        this.event = event;
        this.accentKind = accentKind;
        this.accent = accent;
        this.greeting = greeting;
        this.icons = icons;
        this.swarm = swarm;
    }

    /** The look an event has without any theme settings. */
    static SeasonalLook defaults(String event) {
        if (event == null) return NONE;
        if ("halloween".equals(event)) return new SeasonalLook(event, ACCENT_AUTO, 0, "", true, Swarm.DAILY);
        return new SeasonalLook(event, ACCENT_OFF, 0, null, false, Swarm.OFF);
    }

    /**
     * The event's defaults with the theme's values over them. Each argument is the raw JSON value, or null when the
     * key is missing.
     */
    static SeasonalLook build(String event, Object accent, Object greeting, Object icons, Object swarm) {
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
        Swarm sw = d.swarm;
        if (swarm instanceof String) {
            switch (((String) swarm).trim().toLowerCase(Locale.ROOT)) {
                case "off": sw = Swarm.OFF; break;
                case "daily": sw = Swarm.DAILY; break;
                case "after_break": sw = Swarm.AFTER_BREAK; break;
                case "always": sw = Swarm.ALWAYS; break;
                default: break;
            }
        }
        return new SeasonalLook(event, kind, colour, text, ic, sw);
    }

    /** The look for {@code event} from a whole config.json; {@link #NONE} when there is no event. */
    static SeasonalLook fromJson(JSONObject root, String event) {
        if (event == null) return NONE;
        JSONObject block = root == null ? null : root.optJSONObject(SeasonalConfig.KEY);
        JSONObject looks = block == null ? null : block.optJSONObject("looks");
        JSONObject o = looks == null ? null : looks.optJSONObject(event);
        if (o == null) return defaults(event);
        return build(event, o.opt("accent"), o.opt("greeting"), o.opt("icons"), o.opt("swarm"));
    }

    boolean isNone() {
        return event == null;
    }

    /** Home away this long counts as a break for {@link Swarm#AFTER_BREAK}. */
    static final long BREAK_MS = 60 * 60_000L;

    /**
     * Whether the swarm plays now: never when off; daily on the first Home of a day ({@code lastDay} is the day it
     * last played, "" for never); after a break when Home was away at least {@link #BREAK_MS}, or nothing is known
     * of its last visit ({@code hiddenAt} 0, a fresh start); always on every Home.
     */
    static boolean swarmDue(SeasonalLook.Swarm mode, LocalDate today, String lastDay, long now, long hiddenAt) {
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
     * The contrasting accent for a picture, from a sample of its pixels (0xRRGGBB, any alpha): purple when the
     * picture's saturated, brighter pixels are mostly warm (orange, red), orange when they are mostly cool.
     */
    static int contrastAccent(int[] pixels, int count) {
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
        return warm > cool ? WITCH_PURPLE : PUMPKIN_ORANGE;
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
