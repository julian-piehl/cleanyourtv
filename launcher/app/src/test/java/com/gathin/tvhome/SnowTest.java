package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

/** SnowPiles and SnowTier: the pure parts of the Christmas snow. */
public class SnowTest {

    private static SnowPiles.Cap fullCap(SnowPiles piles, Object key) {
        SnowPiles.Cap c = piles.cap(key, SnowPiles.TILE, 180f);
        for (int i = 0; i < 4000; i++) SnowPiles.land(c, i % c.cols, 1f, 3f, 0f);
        return c;
    }

    @Test
    public void aCapKeepsItsSnowAndRemakesOnlyWhenItsLedgeChanges() {
        SnowPiles piles = new SnowPiles(new Random(1));
        SnowPiles.Cap c = piles.cap("tile", SnowPiles.TILE, 180f);
        assertEquals(60, c.cols);
        assertSame(c, piles.cap("tile", SnowPiles.TILE, 180f));
        assertFalse(c == piles.cap("tile", SnowPiles.TILE, 240f));
    }

    @Test
    public void aFullCapStillHasHills() {
        SnowPiles.Cap c = fullCap(new SnowPiles(new Random(2)), "t");
        float lo = Float.MAX_VALUE, hi = 0;
        for (float h : c.height) {
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
            assertTrue(h <= SnowPiles.MAX_TILE + 1e-4f);
        }
        assertTrue("a flat line: " + lo + " to " + hi, hi - lo > SnowPiles.MAX_TILE * 0.2f);
    }

    @Test
    public void steepCornersCatchNothing() {
        SnowPiles.Cap c = new SnowPiles(new Random(3)).cap("p", SnowPiles.PILL, 100f);
        assertFalse(SnowPiles.land(c, 0, 0.2f, 3f, 0f));
        assertTrue(SnowPiles.land(c, 10, 1f, 3f, 0f));
        assertFalse(SnowPiles.land(c, 10, 1f, 3f, 0.99f));   // the roll above the chance
    }

    @Test
    public void theOutlineFollowsTheRoundedCorners() {
        float[] o = new float[2];
        SnowPiles.outline(30, 60, 180f, 12f, o);
        assertEquals(0f, o[0], 1e-4f);
        assertEquals(1f, o[1], 1e-4f);
        SnowPiles.outline(0, 60, 180f, 12f, o);   // 1.5 in from the edge, in the corner
        assertTrue(o[0] > 4f && o[0] < 12f);
        assertTrue(o[1] < 0.6f);
    }

    @Test
    public void meltingTakesAWhileAndEndsBare() {
        SnowPiles piles = new SnowPiles(new Random(4));
        fullCap(piles, "a");
        assertFalse(piles.empty());
        assertTrue(piles.melt(0.5f));
        int frames = 0;
        while (piles.melt(1 / 60f) && frames < 100_000) frames++;
        assertTrue(piles.empty());
        assertTrue("melted in " + frames + " frames", frames > 60);
    }

    @Test
    public void aSmoothTVKeepsItsFlakes() {
        SnowTier t = new SnowTier(SnowTier.FULL, false, 60f);
        long frame = 16_666_667L;
        boolean changed = false;
        for (int i = 0; i < 700 && !changed; i++) changed = t.frame(frame);
        assertTrue(changed);
        assertTrue(t.settled());
        assertEquals(SnowTier.FULL, t.tier());
        assertEquals(90, t.flakes(90));
    }

    @Test
    public void aJankyTVStepsDownToHalfThenNone() {
        SnowTier t = new SnowTier(SnowTier.FULL, false, 60f);
        long late = 40_000_000L;   // every frame 40 ms: far too late at 60 Hz
        while (!t.frame(late)) { }
        assertEquals(SnowTier.HALF, t.tier());
        assertFalse(t.settled());
        assertEquals(45, t.flakes(90));
        while (!t.frame(late)) { }
        assertEquals(SnowTier.NONE, t.tier());
        assertTrue(t.settled());
        assertEquals(0, t.flakes(90));
    }

    @Test
    public void pausesAreNotFramesAndASettledTierIsLeftAlone() {
        SnowTier t = new SnowTier(SnowTier.HALF, true, 60f);
        assertFalse(t.frame(40_000_000L));
        SnowTier u = new SnowTier(SnowTier.FULL, false, 60f);
        assertFalse(u.frame(0));
        assertFalse(u.frame(5_000_000_000L));
        assertEquals(SnowTier.HALF, SnowTier.start(true));
        assertEquals(SnowTier.FULL, SnowTier.start(false));
    }
}
