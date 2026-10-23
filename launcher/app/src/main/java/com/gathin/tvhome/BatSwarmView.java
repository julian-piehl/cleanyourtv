package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * A transparent full-screen overlay that plays one swarm of bats, as if opening Home startled them: they burst out
 * of the dark below the dock and fly up at the viewer, growing as they come, and sweep past the screen's edges.
 * Black silhouettes only. The flight is a small perspective projection: each bat has a position in front of the
 * screen and a depth z that shrinks towards the camera, and is drawn at its position divided by z, so it seems to
 * fly out of the screen. Everything is fixed when the swarm starts; a frame only evaluates it, into one reused Path,
 * so nothing is allocated per frame. The view takes no focus, clicks or accessibility, and uses a hardware layer
 * only while playing.
 */
public final class BatSwarmView extends View {

    private static final int COLOR = 0xFF07050A;
    /** The reference screen the numbers below are tuned on, 1280 x 720 dp. */
    private static final float REF_W = 1280f, REF_H = 720f;
    /** Focal length and the vanishing point, on the reference screen. */
    private static final float F = 700f, CX = 640f, CY = 330f;

    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF body = new RectF();

    private int count;
    private float[] delay = new float[0], life = new float[0], z0 = new float[0], z1 = new float[0],
            x0 = new float[0], y0 = new float[0], vx = new float[0], vy = new float[0], span = new float[0],
            tilt = new float[0], flapHz = new float[0], flapPh = new float[0];
    // per-frame scratch, sized with the swarm
    private float[] z = new float[0], u = new float[0];
    private int[] order = new int[0];

    private boolean playing;
    private long start, duration;
    private Runnable onDone;
    private final Runnable finish = this::finish;

    public BatSwarmView(Context context) {
        this(context, null);
    }

    public BatSwarmView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
        paint.setColor(COLOR);
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * Plays a swarm of {@code bats} bats lasting {@code durationMs}, then runs {@code done} on the main thread.
     * Playing again before the last swarm ends finishes that one first (its callback runs).
     */
    public void play(int bats, long durationMs, Runnable done) {
        if (playing) {
            removeCallbacks(finish);
            finish();
        }
        int n = Math.max(0, bats);
        ensureCapacity(n);
        count = n;
        duration = Math.max(1, durationMs);
        onDone = done;
        for (int i = 0; i < n; i++) {
            delay[i] = rnd.nextFloat() * 0.45f;                  // fractions of the duration
            life[i] = 1f - delay[i] - rnd.nextFloat() * 0.1f;
            flapHz[i] = 6f + rnd.nextFloat() * 2.5f;
            flapPh[i] = rnd.nextFloat() * 6.28f;
            span[i] = 0.32f + rnd.nextFloat() * 0.14f;           // world units
            tilt[i] = (rnd.nextFloat() - 0.5f) * 0.5f;           // radians
            // Startled out of the dark below the dock: a point near the bottom edge, some depth into the picture.
            z0[i] = 3.5f + rnd.nextFloat() * 2.5f;
            z1[i] = 0.4f + rnd.nextFloat() * 0.3f;
            float sx = 80f + rnd.nextFloat() * 1120f, sy = 660f + rnd.nextFloat() * 100f;
            x0[i] = (sx - CX) * z0[i] / F;
            y0[i] = (sy - CY) * z0[i] / F;
            vx[i] = (sx - CX) / 640f * (0.2f + rnd.nextFloat() * 0.7f) + (rnd.nextFloat() - 0.5f) * 0.6f;
            vy[i] = -(1.4f + rnd.nextFloat() * 1.2f);
        }
        playing = true;
        setLayerType(LAYER_TYPE_HARDWARE, null);
        start = SystemClock.uptimeMillis();
        postDelayed(finish, duration);
        postInvalidateOnAnimation();
    }

    private void finish() {
        if (!playing) return;
        playing = false;
        setLayerType(LAYER_TYPE_NONE, null);
        invalidate();
        Runnable done = onDone;
        onDone = null;
        if (done != null) done.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(finish);
        finish();
        super.onDetachedFromWindow();
    }

    private void ensureCapacity(int n) {
        if (delay.length >= n) return;
        delay = new float[n];
        life = new float[n];
        z0 = new float[n];
        z1 = new float[n];
        x0 = new float[n];
        y0 = new float[n];
        vx = new float[n];
        vy = new float[n];
        span = new float[n];
        tilt = new float[n];
        flapHz = new float[n];
        flapPh = new float[n];
        z = new float[n];
        u = new float[n];
        order = new int[n];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!playing) return;
        long now = SystemClock.uptimeMillis();
        float t = (now - start) / (float) duration;
        float secs = (now - start) / 1000f;
        float k = getWidth() / REF_W, ky = getHeight() / REF_H;
        int shown = 0;
        for (int i = 0; i < count; i++) {
            float ui = (t - delay[i]) / life[i];
            if (ui < 0 || ui > 1) continue;
            float e = ui * ui * (1.6f - 0.6f * ui);              // a little hesitation, then speeding at the camera
            z[i] = z0[i] + (z1[i] - z0[i]) * e;
            u[i] = ui;
            // insertion sort by depth, far ones first
            int j = shown++;
            while (j > 0 && z[order[j - 1]] < z[i]) {
                order[j] = order[j - 1];
                j--;
            }
            order[j] = i;
        }
        for (int s = 0; s < shown; s++) {
            int i = order[s];
            float x = x0[i] + vx[i] * u[i] * 1.6f, y = y0[i] + vy[i] * u[i] * 1.6f;
            float sx = (CX + x / z[i] * F) * k, sy = (CY + y / z[i] * F) * ky, px = span[i] / z[i] * F * k;
            if (sx < -px || sx > getWidth() + px || sy < -px || sy > getHeight() + px) continue;
            float flap = (float) Math.sin(secs * flapHz[i] * 6.2832f + flapPh[i]);
            paint.setAlpha(Math.round(255 * Math.min(1f, u[i] * 6f) * (z[i] > 4f ? 0.75f : 1f)));
            int save = canvas.save();
            canvas.translate(sx, sy);
            canvas.rotate((float) Math.toDegrees(tilt[i] + vx[i] * 0.12f + flap * 0.05f));
            canvas.scale(px, px);
            buildBat(flap);
            canvas.drawPath(path, paint);
            canvas.restoreToCount(save);
        }
        postInvalidateOnAnimation();
    }

    /** One bat into {@link #path}, wingspan 1, centred; flap -1 (wings down) to 1 (up). */
    private void buildBat(float flap) {
        float lift = -0.2f * flap;
        path.rewind();
        wing(1f, lift);
        wing(-1f, lift);
        path.moveTo(-0.035f, -0.04f);   // head and ears
        path.lineTo(-0.05f, -0.13f);
        path.lineTo(-0.015f, -0.075f);
        path.lineTo(0.015f, -0.075f);
        path.lineTo(0.05f, -0.13f);
        path.lineTo(0.035f, -0.04f);
        path.close();
        body.set(-0.05f, -0.065f, 0.05f, 0.105f);
        path.addOval(body, Path.Direction.CW);
    }

    /** One wing, s = 1 right or -1 left, with a scalloped trailing edge. */
    private void wing(float s, float lift) {
        path.moveTo(0f, -0.06f);
        path.cubicTo(s * 0.08f, -0.14f, s * 0.16f, -0.2f + lift, s * 0.26f, -0.24f + lift);
        path.cubicTo(s * 0.34f, -0.2f + lift, s * 0.42f, -0.3f + lift, s * 0.5f, -0.26f + lift);
        path.cubicTo(s * 0.46f, -0.14f + lift, s * 0.44f, -0.04f + lift / 2, s * 0.46f, 0.06f + lift / 2);
        path.cubicTo(s * 0.4f, lift / 2, s * 0.33f, 0.02f, s * 0.3f, 0.08f);
        path.cubicTo(s * 0.25f, 0.02f, s * 0.18f, 0.03f, s * 0.14f, 0.09f);
        path.cubicTo(s * 0.1f, 0.05f, s * 0.06f, 0.06f, 0f, 0.12f);
        path.close();
    }
}
