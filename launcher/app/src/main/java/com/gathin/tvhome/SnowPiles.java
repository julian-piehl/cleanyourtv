package com.gathin.tvhome;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The snow lying on Home's top edges: one cap per ledge (a top-bar button, the dock band, a tile), each a height per
 * column. Pure arithmetic, so it can be tested on the JVM; SnowLayer measures the ledges and draws the caps.
 * <p>
 * A cap belongs to its ledge and is kept in units of the ledge's resting width, so it grows and moves with a tile
 * that zooms on focus. Its base follows the ledge's outline, rounded corners included, so the snow never hangs in
 * the air, and it thins out where a corner curves down, as snow slides off a slope. Each cap has its own drift
 * profile (two slow waves and some noise), so a full one still has hills instead of a flat line. A flake lands on a
 * ledge only with some chance, less on slopes and on the dock band, so plenty still falls past onto the tiles below.
 */
final class SnowPiles {

    /** Column width at a ledge's resting size, in units of the reference screen (1280 wide). */
    static final float COLUMN = 3f;
    /** Highest snow on each kind of ledge, in reference units. */
    static final float MAX_DOCK = 9f, MAX_PILL = 6f, MAX_TILE = 7f;
    /** The chance a flake crossing a ledge lands there, on its flat part. */
    static final float CATCH_DOCK = 0.35f, CATCH_OTHER = 0.55f;
    /** How much one landing adds, per reference unit of the flake's radius: a full cap in about ten minutes. */
    static final float ADD = 0.15f;
    /** Below this slope (1 flat, 0 vertical) nothing stays. */
    static final float STEEPEST = 0.35f;
    /** Melting takes about this long, each column at its own pace. */
    static final float MELT_S = 2.5f;

    static final int DOCK = 0, PILL = 1, TILE = 2;

    /** One ledge's snow. */
    static final class Cap {
        final int kind, cols;
        final float[] height, profile, meltRate;

        Cap(int kind, int cols, Random rnd) {
            this.kind = kind;
            this.cols = cols;
            height = new float[cols];
            profile = new float[cols];
            meltRate = new float[cols];
            float p1 = rnd.nextFloat() * 6.28f, p2 = rnd.nextFloat() * 6.28f;
            float f1 = 0.08f + rnd.nextFloat() * 0.08f, f2 = 0.25f + rnd.nextFloat() * 0.2f;
            for (int j = 0; j < cols; j++) {
                float v = 0.55f + 0.25f * (float) Math.sin(j * f1 + p1) + 0.12f * (float) Math.sin(j * f2 + p2)
                        + (rnd.nextFloat() - 0.5f) * 0.08f;
                profile[j] = Math.max(0.45f, Math.min(1f, v));
                meltRate[j] = 0.6f + rnd.nextFloat() * 0.8f;
            }
        }

        float max() {
            return kind == DOCK ? MAX_DOCK : kind == PILL ? MAX_PILL : MAX_TILE;
        }

        boolean empty() {
            for (float h : height) if (h > 0.05f) return false;
            return true;
        }
    }

    private final Map<Object, Cap> caps = new HashMap<>();
    private final Random rnd;

    SnowPiles(Random rnd) {
        this.rnd = rnd;
    }

    /** The cap of the ledge {@code key} that is {@code restWidth} reference units wide at rest, made if new. */
    Cap cap(Object key, int kind, float restWidth) {
        int cols = Math.max(4, Math.round(restWidth / COLUMN));
        Cap c = caps.get(key);
        if (c == null || c.cols != cols || c.kind != kind) {
            c = new Cap(kind, cols, rnd);
            caps.put(key, c);
        }
        return c;
    }

    void clear() {
        caps.clear();
    }

    boolean empty() {
        for (Cap c : caps.values()) if (!c.empty()) return false;
        return true;
    }

    /**
     * The outline of a rounded rectangle {@code width} wide with corner radius {@code radius}, at column {@code j} of
     * {@code cols}: into {@code out}, how far below the top edge the surface is there, and how flat it is (1 flat,
     * 0 vertical).
     */
    static void outline(int j, int cols, float width, float radius, float[] out) {
        float x = (j + 0.5f) / cols * width, d = Math.min(x, width - x);
        if (d >= radius || radius <= 0) {
            out[0] = 0f;
            out[1] = 1f;
            return;
        }
        float dx = radius - d;
        out[0] = radius - (float) Math.sqrt(Math.max(0f, radius * radius - dx * dx));
        float s = dx / radius;
        out[1] = (float) Math.sqrt(Math.max(0f, 1f - s * s));
    }

    /**
     * A flake of radius {@code r} reaches column {@code j} of {@code cap}, where the outline's flatness is
     * {@code flat}: whether it stays there ({@code roll} 0..1 against the chance), adding to the columns around it.
     */
    static boolean land(Cap cap, int j, float flat, float r, float roll) {
        if (flat < STEEPEST) return false;
        float chance = (cap.kind == DOCK ? CATCH_DOCK : CATCH_OTHER) * flat;
        if (roll > chance) return false;
        float add = r * ADD;
        for (int n = -4; n <= 4; n++) {
            int c = j + n;
            if (c < 0 || c >= cap.cols) continue;
            float limit = cap.max() * cap.profile[c];
            cap.height[c] = Math.min(limit, cap.height[c] + add * (float) Math.exp(-n * n / 5.0));
        }
        return true;
    }

    /** Melts every cap by {@code dt} seconds' worth, each column at its own pace; true while snow is left. */
    boolean melt(float dt) {
        boolean left = false;
        for (Cap c : caps.values()) {
            float step = dt / MELT_S * c.max();
            for (int j = 0; j < c.cols; j++) {
                // thin parts go first, so the snow breaks up into lumps before it is gone
                c.height[j] = Math.max(0f, c.height[j] - step * c.meltRate[j] * (1.4f - c.profile[j]) * 1.6f);
                if (c.height[j] > 0.05f) left = true;
            }
        }
        return left;
    }

    /** The height drawn at column {@code j}: the column smoothed with its neighbours. */
    static float smoothed(Cap c, int j) {
        float a = c.height[Math.max(0, j - 1)], b = c.height[j], d = c.height[Math.min(c.cols - 1, j + 1)];
        return (a + 2 * b + d) / 4f;
    }
}
