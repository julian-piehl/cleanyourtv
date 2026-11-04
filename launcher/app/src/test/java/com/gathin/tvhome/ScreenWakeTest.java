package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ScreenWakeTest {

    private static final long LATE = 10 * 60_000L;   // ten minutes after boot

    @Test
    public void aScreenOnForAWhileWaitsOnlyForTheFirstFrame() {
        assertEquals(ScreenWake.FIRST_FRAME_MS, ScreenWake.delay(LATE, LATE - 5_000));
        assertEquals(ScreenWake.FIRST_FRAME_MS, ScreenWake.delay(LATE, -1));
    }

    @Test
    public void aScreenThatJustCameOnWaitsForThePicture() {
        assertEquals(ScreenWake.SETTLE_MS, ScreenWake.delay(LATE, LATE));
        assertEquals(ScreenWake.SETTLE_MS - 400, ScreenWake.delay(LATE, LATE - 400));
        assertEquals(ScreenWake.FIRST_FRAME_MS, ScreenWake.delay(LATE, LATE - ScreenWake.SETTLE_MS + 50));
    }

    @Test
    public void aColdStartRightAfterBootWaitsLonger() {
        assertEquals(ScreenWake.BOOT_WAIT_MS, ScreenWake.delay(20_000, -1));
        assertEquals(ScreenWake.FIRST_FRAME_MS, ScreenWake.delay(ScreenWake.BOOT_MS, -1));
    }
}
