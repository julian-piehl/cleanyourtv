package com.gathin.tvhome;

/**
 * How much falling snow a TV can take. It starts from what the set is (half the flakes on a low-memory one), and
 * while the snow falls it watches the frames for its first {@link #MEASURE_MS}: if more than {@link #JANK_LIMIT} of
 * them come late, it steps down once, to half the flakes and then to none (the caps still grow, without flakes to
 * draw). Each step is measured again; the result is remembered, so a TV is measured once per theme.
 */
final class SnowTier {

    static final int FULL = 2, HALF = 1, NONE = 0;
    /** How long each tier is measured. */
    static final long MEASURE_MS = 10_000;
    /** A frame counts as late past this many times its expected interval. */
    static final float LATE = 1.6f;
    /** More late frames than this share steps down. */
    static final float JANK_LIMIT = 0.10f;
    /** The fewest frames a measurement needs to count. */
    static final int MIN_FRAMES = 120;

    private int tier;
    private boolean settled;
    private final long frameNs;
    private long measured;
    private int frames, late;

    /**
     * {@code tier} the start, {@code settled} when it was measured before; {@code refreshHz} the display's rate.
     */
    SnowTier(int tier, boolean settled, float refreshHz) {
        this.tier = Math.max(NONE, Math.min(FULL, tier));
        this.settled = settled || this.tier == NONE;
        frameNs = (long) (1e9 / (refreshHz > 1 ? refreshHz : 60f));
    }

    /** The starting tier for a set: half the flakes when it is short of memory. */
    static int start(boolean lowRam) {
        return lowRam ? HALF : FULL;
    }

    int tier() {
        return tier;
    }

    boolean settled() {
        return settled;
    }

    /** The flakes to draw at this tier out of {@code configured}. */
    int flakes(int configured) {
        return tier == FULL ? configured : tier == HALF ? configured / 2 : 0;
    }

    /**
     * One drawn frame, {@code intervalNs} after the one before (0 for the first after a pause, which is not
     * counted). True when the tier just changed, so the caller draws differently and remembers it.
     */
    boolean frame(long intervalNs) {
        if (settled || intervalNs <= 0 || intervalNs > 1_000_000_000L) return false;   // a pause is no frame
        frames++;
        if (intervalNs > frameNs * LATE) late++;
        measured += intervalNs;
        if (measured < MEASURE_MS * 1_000_000L || frames < MIN_FRAMES) return false;
        boolean janky = late > frames * JANK_LIMIT;
        measured = 0;
        frames = 0;
        late = 0;
        if (!janky) {
            settled = true;
            return true;   // settled: remember it, nothing to redraw
        }
        tier--;
        if (tier == NONE) settled = true;
        return true;
    }
}
