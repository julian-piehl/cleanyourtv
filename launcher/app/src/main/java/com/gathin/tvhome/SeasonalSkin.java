package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.function.IntConsumer;

/**
 * Applies a {@link SeasonalLook} to Home: the event's icons in the top bar, the accent on the focus glow and on the
 * icons' lit details, the greeting in a colour taken from the wallpaper, and the bat swarm when Home opens.
 * <p>
 * Colours follow the wallpaper. Each time a picture is shown, a small sample of it decides two colours: the accent,
 * which has to make the focus easy to find, contrasts with the picture (purple on a warm one, orange on a cool one);
 * the greeting, which only has to sit well, takes the picture's own strongest colour. Both blend over to the new
 * values. MainActivity keeps the wiring: it hands the skin its views and calls it at the few moments that matter.
 */
final class SeasonalSkin {

    /** A Home that has nothing seasonal on it. */
    static final SeasonalSkin NONE = new SeasonalSkin(null, SeasonalLook.NONE, true);

    private static final long BLEND_MS = 700;
    private static final int SWARM_BATS = 22;
    private static final long SWARM_MS = 2600;
    private static final String PREFS = "seasonal", SWARM_DAY = "swarm_day";
    /** When Home last went to the background; 0 since the process started, which counts as a break. */
    private static long hiddenAt;

    private final Context context;
    private final SeasonalLook look;
    private final boolean reducedMotion;
    private final ArgbEvaluator argb = new ArgbEvaluator();

    private HalloweenIconDrawable inputsIcon, wifiIcon, settingsIcon;
    private TextView greeting;
    private IntConsumer accentSink;
    private int accent, greetingColour;
    private ValueAnimator blend;
    /** The first picture's colours apply at once; later ones blend. */
    private boolean coloured;
    private final int[] sample = new int[64 * 24];

    private SeasonalSkin(Context context, SeasonalLook look, boolean reducedMotion) {
        this.context = context;
        this.look = look;
        this.reducedMotion = reducedMotion;
        accent = look.accentKind == SeasonalLook.ACCENT_FIXED ? look.accent
                : look.accentKind == SeasonalLook.ACCENT_AUTO ? SeasonalLook.PUMPKIN_ORANGE : 0;
        greetingColour = accent != 0 ? lift(accent) : 0xFFFFFFFF;
    }

    static SeasonalSkin create(Context context, SeasonalLook look, boolean reducedMotion) {
        return look == null || look.isNone() ? NONE : new SeasonalSkin(context, look, reducedMotion);
    }

    boolean active() {
        return !look.isNone();
    }

    /** Whether the skin colours the focus glow, so per-app glow colours stand aside as they do for festivals. */
    boolean recolours() {
        return active() && look.accentKind != SeasonalLook.ACCENT_OFF;
    }

    /** The focus glow colour now, or 0 for the plain glow. */
    int accent() {
        return recolours() ? accent : 0;
    }

    // ---- icons ---------------------------------------------------------------------------------------

    /** Puts the event's icons on the three pills, if the look wants them and the event has a set. */
    void applyIcons(TextView inputs, TextView wifi, TextView settings) {
        if (!active() || !look.icons || !"halloween".equals(look.event)) return;
        float density = context.getResources().getDisplayMetrics().density;
        inputsIcon = icon(inputs, HalloweenIconDrawable.CAULDRON, density);
        wifiIcon = icon(wifi, HalloweenIconDrawable.BAT, density);
        settingsIcon = icon(settings, HalloweenIconDrawable.PUMPKIN, density);
    }

    private HalloweenIconDrawable icon(TextView pill, int kind, float density) {
        if (pill == null) return null;
        HalloweenIconDrawable d = new HalloweenIconDrawable(kind, density);
        d.setReducedMotion(reducedMotion);
        d.setAccent(accent != 0 ? accent : SeasonalLook.PUMPKIN_ORANGE);
        // The pill's tint (white, dark when focused) still applies: these icons are one colour like the stock ones.
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null);
        return d;
    }

    /** Whether the Wi-Fi pill shows the bat, which needs the signal level instead of the stock icons. */
    boolean ownsWifiIcon() {
        return wifiIcon != null;
    }

    /** 0 offline, 1 to 4 the signal; Ethernet counts as 4. */
    void setWifiLevel(int level) {
        if (wifiIcon != null) wifiIcon.setSignal(level);
    }

    // ---- greeting ------------------------------------------------------------------------------------

    /** The greeting to show instead of the time-of-day one, or null to keep that. */
    String greetingText(Resources res) {
        if (!active() || look.greeting == null) return null;
        if (!look.greeting.isEmpty()) return look.greeting;
        return "halloween".equals(look.event) ? res.getString(R.string.seasonal_halloween_greeting) : null;
    }

    /** The view the greeting is in; the skin keeps its colour in step with the wallpaper. */
    void bindGreeting(TextView view) {
        greeting = view;
        if (greeting != null && greetingText(view.getResources()) != null) greeting.setTextColor(greetingColour);
    }

    int greetingColour() {
        return greetingColour;
    }

    // ---- colours from the wallpaper ------------------------------------------------------------------

    /** Receives the focus glow colour as it changes, blends included. */
    void setAccentSink(IntConsumer sink) {
        accentSink = sink;
        if (sink != null && recolours()) sink.accept(accent);
    }

    /** A new wallpaper picture is on screen: work out its colours and blend over to them. */
    void onPicture(Bitmap picture) {
        if (!active() || picture == null || picture.isRecycled()) return;
        int w = picture.getWidth(), h = picture.getHeight() * 2 / 3, n = 0;
        if (w <= 0 || h <= 0) return;
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 64; x++) {
                sample[n++] = picture.getPixel(Math.min(w - 1, x * w / 64 + w / 128), Math.min(h - 1, y * h / 24 + h / 48));
            }
        }
        int toAccent = look.accentKind == SeasonalLook.ACCENT_AUTO ? SeasonalLook.contrastAccent(sample, n) : accent;
        int toGreeting = SeasonalLook.matchingColour(sample, n);
        blendTo(toAccent, toGreeting);
    }

    private void blendTo(int toAccent, int toGreeting) {
        if (blend != null) blend.cancel();
        int fromAccent = accent, fromGreeting = greetingColour;
        if (!coloured || reducedMotion || (fromAccent == toAccent && fromGreeting == toGreeting)) {
            coloured = true;
            setColours(toAccent, toGreeting);
            return;
        }
        blend = ValueAnimator.ofFloat(0f, 1f);
        blend.setDuration(BLEND_MS);
        blend.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            setColours((int) argb.evaluate(f, fromAccent, toAccent), (int) argb.evaluate(f, fromGreeting, toGreeting));
        });
        blend.start();
    }

    private void setColours(int newAccent, int newGreeting) {
        accent = newAccent;
        greetingColour = newGreeting;
        if (recolours()) {
            if (accentSink != null) accentSink.accept(accent);
            if (inputsIcon != null) inputsIcon.setAccent(accent);
            if (wifiIcon != null) wifiIcon.setAccent(accent);
            if (settingsIcon != null) settingsIcon.setAccent(accent);
        }
        if (greeting != null && greetingText(greeting.getResources()) != null) greeting.setTextColor(greetingColour);
    }

    private static int lift(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r += (255 - r) / 4;
        g += (255 - g) / 4;
        b += (255 - b) / 4;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ---- the swarm -----------------------------------------------------------------------------------

    /** Home came to the front: plays the swarm over {@code root} if the look's schedule says it is due. */
    void onHomeShown(FrameLayout root) {
        if (!active() || reducedMotion || root == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        LocalDate today = LocalDate.now();
        String last = prefs.getString(SWARM_DAY, "");
        if (!SeasonalLook.swarmDue(look.swarm, today, last, SystemClock.elapsedRealtime(), hiddenAt)) return;
        prefs.edit().putString(SWARM_DAY, today.toString()).apply();
        root.postDelayed(() -> {
            if (!root.isAttachedToWindow()) return;
            BatSwarmView swarm = new BatSwarmView(context);
            root.addView(swarm, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            swarm.play(SWARM_BATS, SWARM_MS, () -> root.removeView(swarm));
        }, 350);
    }

    /** Home went to the background. */
    void onHomeHidden() {
        hiddenAt = SystemClock.elapsedRealtime();
        if (blend != null) blend.cancel();
    }
}
