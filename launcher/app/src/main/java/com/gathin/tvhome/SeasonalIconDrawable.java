package com.gathin.tvhome;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The shared part of the seasonal top-bar icons (HalloweenIconDrawable, ChristmasIconDrawable). An icon is flat, one
 * colour like the launcher's own icons, and takes the pill's tint (white, dark when focused); details lit in the
 * accent colour while the pill is focused. At rest nothing moves: when the pill gets focus the icon plays one short
 * animation, drawn at 60 fps only while it lasts. Subclasses draw in a 24 x 24 box scaled into the bounds and build
 * their shapes once.
 */
abstract class SeasonalIconDrawable extends Drawable {

    static final float BOX = 24f;
    private static final float SIZE_DP = 22f;
    private static final long FRAME_MS = 16;
    private static final int REST_COLOR = 0xFFFFFFFF;

    /** Which icon of the set this is; the subclass defines the values. */
    final int kind;
    private final float density;
    /** The shape in the pill's colour, the lit details, and strokes in the pill's colour. */
    final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG), lit = new Paint(Paint.ANTI_ALIAS_FLAG),
            line = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ColorStateList tint;
    /** The pill's colour for the current state. */
    int color = REST_COLOR;
    int accent;
    int alpha = 255;
    boolean focused;
    private boolean reduced;
    private long animStart = -1;
    /** Wi-Fi level: 0 offline, 1 to 4; only the Wi-Fi icon of a set uses it. */
    int level = 4;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidateSelf();
            if (animating(SystemClock.uptimeMillis())) scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_MS);
        }
    };

    SeasonalIconDrawable(int kind, float density, int accent) {
        this.kind = kind;
        this.density = density;
        this.accent = accent;
        ink.setStyle(Paint.Style.FILL);
        lit.setStyle(Paint.Style.FILL);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.9f);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    /** How long this icon's focus animation lasts. */
    abstract long animMs();

    /** Draws the icon in the 24 x 24 box; {@code p} is the focus animation's progress 0..1, or -1 when none plays. */
    abstract void drawIcon(Canvas c, float p);

    // ---- state -------------------------------------------------------------------------------------

    /** The accent the lit details take while focused. */
    void setAccent(int argb) {
        if (accent == argb) return;
        accent = argb;
        if (focused) invalidateSelf();
    }

    /** reduced_motion: no focus animations, the details just light. */
    void setReducedMotion(boolean reduced) {
        this.reduced = reduced;
    }

    /** The Wi-Fi level to show: 0 offline, 1 to 4. */
    void setSignal(int level) {
        int l = Math.max(0, Math.min(4, level));
        if (this.level == l) return;
        this.level = l;
        invalidateSelf();
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean f = false;
        for (int s : state) if (s == android.R.attr.state_focused) f = true;
        int c = tint != null ? tint.getColorForState(state, REST_COLOR) : REST_COLOR;
        boolean changed = f != focused || c != color;
        color = c;
        if (f && !focused && !reduced) {
            animStart = SystemClock.uptimeMillis();
            unscheduleSelf(tick);
            scheduleSelf(tick, animStart + FRAME_MS);
        }
        if (!f) animStart = -1;
        focused = f;
        return changed;
    }

    @Override
    public void setTintList(@Nullable ColorStateList tint) {
        this.tint = tint;
        onStateChange(getState());
        invalidateSelf();
    }

    private boolean animating(long now) {
        return animStart >= 0 && now - animStart <= animMs();
    }

    static float ease(float p) {
        float q = 1 - p;
        return 1 - q * q * q;
    }

    /** The pill colour's alpha times the drawable's, times {@code f}, for a paint. */
    int inkAlpha(float f) {
        return Math.round(Color.alpha(color) * alpha / 255f * f);
    }

    // ---- drawing -----------------------------------------------------------------------------------

    @Override
    public void draw(@NonNull Canvas c) {
        Rect b = getBounds();
        int save = c.save();
        c.translate(b.left, b.top);
        c.scale(b.width() / BOX, b.height() / BOX);
        ink.setColor(color);
        ink.setAlpha(inkAlpha(1f));
        line.setColor(color);
        line.setAlpha(inkAlpha(1f));
        long now = SystemClock.uptimeMillis();
        drawIcon(c, animating(now) ? (now - animStart) / (float) animMs() : -1f);
        c.restoreToCount(save);
    }

    // ---- Drawable plumbing ---------------------------------------------------------------------------

    @Override
    public int getIntrinsicWidth() {
        return Math.round(SIZE_DP * density);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(SIZE_DP * density);
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha == alpha) return;
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter filter) {
        ink.setColorFilter(filter);
        line.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
