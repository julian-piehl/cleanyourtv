package com.gathin.tvhome;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The Halloween icons for the top bar: a cauldron for Inputs, a bat under Wi-Fi arcs, a jack-o'-lantern for Settings.
 * They are flat, one colour like the launcher's own icons, and take the pill's tint (white, dark when focused). The
 * carved face and the brew line are cut out of the shape; while the pill is focused they light in the accent colour.
 * At rest nothing moves. When the pill gets focus each plays one short animation: the bubbles rise and pop, the bat
 * beats its wings twice, the face flickers alight like a candle. Drawn in a 24 x 24 box scaled into the bounds;
 * shapes are built once, and only the bat's path is rebuilt (into the same Path) while it flaps.
 */
public final class HalloweenIconDrawable extends Drawable {

    static final int CAULDRON = 0, BAT = 1, PUMPKIN = 2;

    private static final float BOX = 24f;
    private static final float SIZE_DP = 22f;
    private static final long FRAME_MS = 16;
    private static final long[] ANIM_MS = {900, 650, 1100};
    private static final int REST_COLOR = 0xFFFFFFFF;

    private final int kind;
    private final float density;
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path(), detail = new Path(), bat = new Path();
    private final RectF oval = new RectF();

    private ColorStateList tint;
    private int color = REST_COLOR;
    private int accent = SeasonalLook.PUMPKIN_ORANGE;
    private int alpha = 255;
    private boolean focused, reduced;
    private long animStart = -1;
    /** Wi-Fi level for the bat: 0 offline, 1 to 4 lights 0 to 3 arcs. */
    private int level = 4;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidateSelf();
            if (animating(SystemClock.uptimeMillis())) scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_MS);
        }
    };

    HalloweenIconDrawable(int kind, float density) {
        this.kind = kind;
        this.density = density;
        ink.setStyle(Paint.Style.FILL);
        lit.setStyle(Paint.Style.FILL);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(1.9f);
        arc.setStrokeCap(Paint.Cap.ROUND);
        if (kind == CAULDRON) buildCauldron();
        if (kind == PUMPKIN) buildPumpkin();
    }

    // ---- shapes, in box units ----------------------------------------------------------------------

    private void buildCauldron() {
        Path p = new Path();
        p.addRoundRect(new RectF(3f, 9f, 21f, 11.6f), 1.3f, 1.3f, Path.Direction.CW);   // rim
        Path pot = new Path();
        pot.moveTo(4.6f, 11f);
        pot.cubicTo(3.6f, 16.6f, 7f, 20.6f, 12f, 20.6f);
        pot.cubicTo(17f, 20.6f, 20.4f, 16.6f, 19.4f, 11f);
        pot.close();
        pot.moveTo(6.4f, 19.2f);   // legs
        pot.lineTo(5.2f, 22.4f);
        pot.lineTo(7.4f, 22.4f);
        pot.lineTo(8.2f, 20.2f);
        pot.close();
        pot.moveTo(17.6f, 19.2f);
        pot.lineTo(18.8f, 22.4f);
        pot.lineTo(16.6f, 22.4f);
        pot.lineTo(15.8f, 20.2f);
        pot.close();
        p.op(pot, Path.Op.UNION);
        detail.addOval(new RectF(5.4f, 12.15f, 18.6f, 13.65f), Path.Direction.CW);       // the brew line
        p.op(detail, Path.Op.DIFFERENCE);
        shape.set(p);
    }

    private void buildPumpkin() {
        Path p = new Path();
        p.addOval(new RectF(12f - 4.9f, 7.7f, 12f + 4.9f, 21.5f), Path.Direction.CW);
        Path lobe = new Path();
        lobe.addOval(new RectF(8f - 4.5f, 7.7f, 8f + 4.5f, 21.5f), Path.Direction.CW);
        p.op(lobe, Path.Op.UNION);
        lobe.reset();
        lobe.addOval(new RectF(16f - 4.5f, 7.7f, 16f + 4.5f, 21.5f), Path.Direction.CW);
        p.op(lobe, Path.Op.UNION);
        lobe.reset();   // stem
        lobe.moveTo(11.1f, 8f);
        lobe.quadTo(11f, 4.6f, 13.8f, 3.4f);
        lobe.lineTo(14.4f, 4.8f);
        lobe.quadTo(12.9f, 5.6f, 12.9f, 8f);
        lobe.close();
        p.op(lobe, Path.Op.UNION);
        // The carved face: two eyes and a jagged grin.
        detail.moveTo(7.2f, 13.6f);
        detail.lineTo(9f, 10.8f);
        detail.lineTo(10.8f, 13.6f);
        detail.close();
        detail.moveTo(13.2f, 13.6f);
        detail.lineTo(15f, 10.8f);
        detail.lineTo(16.8f, 13.6f);
        detail.close();
        detail.moveTo(6.8f, 15.8f);
        detail.quadTo(12f, 19f, 17.2f, 15.8f);
        detail.lineTo(16f, 18.5f);
        detail.lineTo(14.6f, 17.4f);
        detail.lineTo(13.2f, 19.1f);
        detail.lineTo(12f, 17.8f);
        detail.lineTo(10.8f, 19.1f);
        detail.lineTo(9.4f, 17.4f);
        detail.lineTo(8f, 18.5f);
        detail.close();
        p.op(detail, Path.Op.DIFFERENCE);
        shape.set(p);
    }

    /** One half of the emblem bat into {@link #bat}: half-span 1, side +1 or -1, flap -1 (down) to 1 (up). */
    private void batHalf(float s, float flap) {
        float t = -0.55f - 0.35f * flap, o = 0.06f - 0.2f * flap;
        bat.lineTo(s * 0.08f, -0.36f);
        bat.lineTo(s * 0.15f, -0.14f);
        bat.quadTo(s * 0.5f, -0.2f + t * 0.3f, s, t);
        bat.quadTo(s * 0.92f, (t + o) / 2, s * 0.86f, o);
        bat.quadTo(s * 0.74f, o - 0.14f, s * 0.6f, (o + 0.08f) / 2 + 0.04f);
        bat.quadTo(s * 0.46f, -0.03f, s * 0.32f, 0.15f);
        bat.quadTo(s * 0.2f, 0.05f, s * 0.1f, 0.3f);
        bat.lineTo(0f, 0.4f);
    }

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

    /** The Wi-Fi level the bat shows: 0 offline, 1 to 4. Only the bat icon uses it. */
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
        return animStart >= 0 && now - animStart <= ANIM_MS[kind];
    }

    /** Progress 0..1 of the focus animation, or -1 when none is playing. */
    private float progress(long now) {
        return animating(now) ? (now - animStart) / (float) ANIM_MS[kind] : -1f;
    }

    private static float ease(float p) {
        float q = 1 - p;
        return 1 - q * q * q;
    }

    // ---- drawing -----------------------------------------------------------------------------------

    @Override
    public void draw(@NonNull Canvas c) {
        Rect b = getBounds();
        int save = c.save();
        c.translate(b.left, b.top);
        c.scale(b.width() / BOX, b.height() / BOX);
        ink.setColor(color);
        ink.setAlpha(Color.alpha(color) * alpha / 255);
        float p = progress(SystemClock.uptimeMillis());
        if (kind == CAULDRON) drawCauldron(c, p);
        else if (kind == BAT) drawBat(c, p);
        else drawPumpkin(c, p);
        c.restoreToCount(save);
    }

    private void drawCauldron(Canvas c, float p) {
        // Three bubbles over the rim; on focus they rise and pop one after another.
        for (float[] bb : BUBBLES) {
            float dy = 0, a = 1, r = bb[2];
            if (p >= 0) {
                float q = Math.min(1, Math.max(0, (p - bb[3]) / 0.7f));
                dy = -3.2f * ease(q);
                a = q < 0.85f ? 1 : (1 - q) / 0.15f;
                r = bb[2] * (1 + 0.35f * q);
            }
            int base = ink.getAlpha();
            ink.setAlpha(Math.round(base * a));
            c.drawCircle(bb[0], bb[1] + dy, r, ink);
            ink.setAlpha(base);
        }
        c.drawPath(shape, ink);
        if (focused) {
            lit.setColor(accent);
            lit.setAlpha(Math.round(alpha * (p < 0 ? 1f : Math.min(1f, p * 3))));
            c.drawPath(detail, lit);
        }
    }

    private static final float[] ARC_RADII = {3.4f, 6.4f, 9.4f};

    /** x, y, radius and start delay (fraction of the animation) of each bubble. */
    private static final float[][] BUBBLES = {{9.2f, 6.6f, 1.15f, 0f}, {13.2f, 5f, 1.5f, 0.15f}, {16f, 7.4f, 0.85f, 0.3f}};

    private void drawBat(Canvas c, float p) {
        // Arcs over the bat: level 4 lights all three, level 1 none.
        arc.setColor(color);
        for (int i = 0; i < ARC_RADII.length; i++) {
            arc.setAlpha(Math.round(Color.alpha(color) * alpha / 255f * (level > i + 1 ? 1f : 0.28f)));
            float r = ARC_RADII[i];
            oval.set(12 - r, 11 - r, 12 + r, 11 + r);
            c.drawArc(oval, 220f, 100f, false, arc);
        }
        if (level == 0) {
            // Offline: hanging asleep from a twig, wings wrapped round.
            arc.setAlpha(Math.round(Color.alpha(color) * alpha / 255f * 0.6f));
            arc.setStrokeWidth(1.2f);
            c.drawLine(7.5f, 13.4f, 16.5f, 13.4f, arc);
            arc.setStrokeWidth(1.9f);
            bat.rewind();
            bat.moveTo(11f, 13.6f);
            bat.lineTo(13f, 13.6f);
            bat.lineTo(12.8f, 14.8f);
            bat.cubicTo(15.4f, 15.8f, 15.2f, 20.8f, 12f, 22.4f);
            bat.cubicTo(8.8f, 20.8f, 8.6f, 15.8f, 11.2f, 14.8f);
            bat.close();
            bat.moveTo(10.6f, 21.4f);
            bat.lineTo(10.2f, 23.4f);
            bat.lineTo(12f, 22.4f);
            bat.lineTo(13.8f, 23.4f);
            bat.lineTo(13.4f, 21.4f);
            bat.close();
            c.drawPath(bat, ink);
            return;
        }
        // The bat sits where the Wi-Fi dot would: still at rest, two wing beats when the pill gets focus.
        float flap = p < 0 ? 0 : (float) Math.sin(ease(p) * Math.PI * 4) * (1 - p);
        float hop = p < 0 ? 0 : (float) Math.sin(p * Math.PI) * 1.2f;
        bat.rewind();
        bat.moveTo(0f, -0.16f);
        batHalf(1f, flap);
        bat.lineTo(0f, 0.4f);
        batHalfReverse(flap);
        bat.close();
        int save = c.save();
        c.translate(12f, 19f - hop);
        c.scale(10.5f, 10.5f);
        c.drawPath(bat, ink);
        c.restoreToCount(save);
    }

    /** The left half, traced from the tail back up to the head so the outline closes as one shape. */
    private void batHalfReverse(float flap) {
        float t = -0.55f - 0.35f * flap, o = 0.06f - 0.2f * flap;
        bat.lineTo(-0.1f, 0.3f);
        bat.quadTo(-0.2f, 0.05f, -0.32f, 0.15f);
        bat.quadTo(-0.46f, -0.03f, -0.6f, (o + 0.08f) / 2 + 0.04f);
        bat.quadTo(-0.74f, o - 0.14f, -0.86f, o);
        bat.quadTo(-0.92f, (t + o) / 2, -1f, t);
        bat.quadTo(-0.5f, -0.2f + t * 0.3f, -0.15f, -0.14f);
        bat.lineTo(-0.08f, -0.36f);
        bat.lineTo(0f, -0.16f);
    }

    private void drawPumpkin(Canvas c, float p) {
        c.drawPath(shape, ink);
        if (!focused) return;
        // While focused the face is lit in the accent; when the focus arrives it flickers like a candle catching.
        float on;
        if (p < 0) on = 1f;
        else if (p < 0.6f) on = ((float) Math.sin(p * 95) * 0.5f + 0.5f) * ease(p / 0.6f);
        else on = 0.7f + 0.3f * ease((p - 0.6f) / 0.4f);
        lit.setColor(accent);
        lit.setAlpha(Math.round(alpha * on));
        c.drawPath(detail, lit);
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
        arc.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
