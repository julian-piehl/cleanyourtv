package com.gathin.tvhome;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;

/**
 * Knows when the TV's screen last came on, so a seasonal effect can wait until there is a picture to see. Coming out
 * of standby, Android brings Home back before the panel shows anything, and an effect started then has played out
 * by the time the picture appears. A display listener rather than ACTION_SCREEN_ON, as some firmware leaves the app
 * on screen out of that broadcast (see ClockRefresh). The listener costs nothing between changes; it stays registered
 * while Home is in the background, since that is when the screen goes off and on.
 */
final class ScreenWake implements DisplayManager.DisplayListener {

    /** How long after the screen comes on the panel takes to show a picture, about. */
    static final long SETTLE_MS = 1200;
    /** The least wait before an effect, so Home's first frame is drawn. */
    static final long FIRST_FRAME_MS = 100;
    /** A cold start this soon after boot still has the boot animation or a black panel on screen. */
    static final long BOOT_MS = 60_000, BOOT_WAIT_MS = 3000;

    /** elapsedRealtime when the screen last came on; -1 while this process has not seen it. */
    private static long wokeAt = -1;

    private final DisplayManager displays;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean on;
    private Runnable pending;

    ScreenWake(Context context) {
        displays = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        on = displayOn();
        if (displays != null) displays.registerDisplayListener(this, handler);
    }

    /** When the screen last came on (elapsedRealtime), or -1 if not since this process started. */
    static long wokeAt() {
        return wokeAt;
    }

    /**
     * Runs {@code action} once the screen has a picture: now if it has been on a while, after {@link #SETTLE_MS}
     * from when it came on, or once it comes on if it is off. A later call replaces an action still waiting.
     */
    void whenSeen(Runnable action) {
        cancel();
        pending = action;
        if (!on) return;   // onDisplayChanged schedules it
        handler.postDelayed(this::runPending, delay(SystemClock.elapsedRealtime(), wokeAt));
    }

    /**
     * How long to wait before an effect at {@code now} (elapsedRealtime, the time since boot): until
     * {@link #SETTLE_MS} after the screen came on at {@code woke} (-1 for not seen), {@link #BOOT_WAIT_MS} in the
     * first minute after boot when no wake was seen, and at least {@link #FIRST_FRAME_MS}.
     */
    static long delay(long now, long woke) {
        long wait = FIRST_FRAME_MS;
        if (woke >= 0 && now - woke < SETTLE_MS) wait = Math.max(wait, SETTLE_MS - (now - woke));
        if (woke < 0 && now < BOOT_MS) wait = Math.max(wait, BOOT_WAIT_MS);
        return wait;
    }

    /** Drops an action still waiting; Home went to the background. */
    void cancel() {
        handler.removeCallbacksAndMessages(null);
        pending = null;
    }

    void close() {
        cancel();
        if (displays != null) displays.unregisterDisplayListener(this);
    }

    private void runPending() {
        Runnable r = pending;
        pending = null;
        if (r != null) r.run();
    }

    private boolean displayOn() {
        Display d = displays == null ? null : displays.getDisplay(Display.DEFAULT_DISPLAY);
        return d == null || d.getState() == Display.STATE_ON;
    }

    @Override
    public void onDisplayChanged(int displayId) {
        if (displayId != Display.DEFAULT_DISPLAY) return;
        boolean nowOn = displayOn();
        if (nowOn && !on) {
            wokeAt = SystemClock.elapsedRealtime();
            if (pending != null) {
                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(this::runPending, SETTLE_MS);
            }
        }
        on = nowOn;
    }

    @Override
    public void onDisplayAdded(int displayId) {
    }

    @Override
    public void onDisplayRemoved(int displayId) {
    }
}
