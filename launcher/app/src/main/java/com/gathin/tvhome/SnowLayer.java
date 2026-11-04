package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Quiet snow on Home: small flakes falling behind the tiles, a few large near ones in front for depth, and the snow
 * that lands on the top-bar buttons, the dock band and the tiles and piles up there (SnowPiles). Two full-screen
 * views: one just above the wallpaper and the sky, one on top of everything. The front one drives a frame while Home
 * is visible, and nothing runs while it is not. How many flakes fall follows SnowTier, which watches the frames and
 * steps down on a TV that cannot keep up; without flakes the caps still grow, a few times a second.
 * <p>
 * The caps belong to the process, not to one Home: a rebuild keeps them, the screen coming on clears them, so every
 * switch-on starts with bare edges. Numbers are in units of a 1280-wide reference screen, scaled to the real one.
 */
final class SnowLayer {

    private static final float REF_W = 1280f;
    /** The large near flakes in front, one for so many behind. */
    private static final int NEAR_EVERY = 18;
    /** Without flakes (SnowTier.NONE), landings a second for each configured flake, and how often to redraw. */
    private static final float VIRTUAL_RATE = 0.05f;
    private static final long VIRTUAL_FRAME_MS = 400;
    private static final String TIER = "snow_tier", SETTLED = "snow_tier_settled", THEME = "snow_tier_theme";

    /** The snow lying on Home, kept across rebuilds; cleared when the screen comes on (see ScreenWake). */
    private static final SnowPiles PILES = new SnowPiles(new Random());
    private static long seenWake = Long.MIN_VALUE;

    private final FrameLayout root;
    private final ViewGroup topButtons;
    private final RecyclerView grid;
    private final DockDecoration dock;
    private final float density;
    /** Flakes to fall (0 when only the piles are wanted), and the flakes the piles grow from either way. */
    private final int configured, nominal;
    private final boolean piles;
    private final SharedPreferences prefs;
    private final String theme;
    private final SnowTier tier;
    private final Random rnd = new Random();

    private final FallView back;
    private final FrontView front;
    private final Paint flakePaint = new Paint(Paint.ANTI_ALIAS_FLAG), capPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path capPath = new Path();
    private final RectF box = new RectF();
    private final float[] outline = new float[2];

    // flakes: 0..far-1 behind, far.. in front
    private int far, near;
    private float[] x = new float[0], y = new float[0], vy = new float[0], sway = new float[0], phase = new float[0],
            hz = new float[0], r = new float[0], alpha = new float[0];

    /** The ledges of this frame, reused. */
    private final List<Ledge> ledges = new ArrayList<>();
    private int ledgeCount;

    private boolean running;
    private Runnable afterMelt;
    private long lastNs;
    private float clock, virtualDebt;

    private static final class Ledge {
        Object key;
        int kind;
        final RectF rect = new RectF();
        float radius, scale;
        SnowPiles.Cap cap;
    }

    SnowLayer(Context context, FrameLayout root, ViewGroup topButtons, RecyclerView grid, DockDecoration dock,
              int flakes, boolean fall, boolean piles, SharedPreferences prefs, String theme) {
        this.root = root;
        this.topButtons = topButtons;
        this.grid = grid;
        this.dock = dock;
        this.density = context.getResources().getDisplayMetrics().density;
        this.nominal = Math.max(0, flakes);
        this.configured = fall ? nominal : 0;
        this.piles = piles;
        this.prefs = prefs;
        this.theme = theme == null ? "" : theme;
        boolean known = this.theme.equals(prefs.getString(THEME, null));
        int start = known ? prefs.getInt(TIER, SnowTier.FULL) : SnowTier.start(lowRam(context));
        Display display = root.getDisplay();
        tier = new SnowTier(start, known && prefs.getBoolean(SETTLED, false),
                display != null ? display.getRefreshRate() : 60f);
        flakePaint.setColor(0xFFFFFFFF);
        capPaint.setColor(0xFFF2F6FC);
        back = new FallView(context);
        front = new FrontView(context);
        View sky = root.findViewById(R.id.sky);
        int at = sky != null ? root.indexOfChild(sky) + 1 : 1;
        root.addView(back, Math.min(at, root.getChildCount()), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(front, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        resize();
    }

    private static boolean lowRam(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return am.isLowRamDevice() || mi.totalMem < 1536L * 1024 * 1024;
    }

    // ---- life ----------------------------------------------------------------------------------------

    /** Home is on screen: snow again, and bare edges if the screen came on since the snow last lay. */
    void start() {
        long woke = ScreenWake.wokeAt();
        if (woke != seenWake) {
            seenWake = woke;
            PILES.clear();
        }
        running = true;
        lastNs = 0;
        front.postInvalidateOnAnimation();
    }

    /** Home went to the background: nothing runs. */
    void stop() {
        running = false;
        front.removeCallbacks(redraw);
    }

    void close() {
        stop();
        root.removeView(back);
        root.removeView(front);
    }

    boolean hasSnow() {
        return !PILES.empty();
    }

    /** Melts the snow away where it lies, then runs {@code done}; at once if there is none. */
    void melt(Runnable done) {
        if (PILES.empty()) {
            done.run();
            return;
        }
        afterMelt = done;
        running = true;
        lastNs = 0;
        front.postInvalidateOnAnimation();
    }

    private final Runnable redraw = this::invalidateNow;

    private void invalidateNow() {
        front.invalidate();
    }

    // ---- flakes --------------------------------------------------------------------------------------

    private void resize() {
        int total = tier.flakes(configured);
        far = total;
        near = total == 0 ? 0 : Math.max(1, Math.round(total / (float) NEAR_EVERY));
        int n = far + near;
        if (x.length < n) {
            x = new float[n];
            y = new float[n];
            vy = new float[n];
            sway = new float[n];
            phase = new float[n];
            hz = new float[n];
            r = new float[n];
            alpha = new float[n];
        }
        for (int i = 0; i < n; i++) {
            spawn(i);
            y[i] = rnd.nextFloat() * 760f - 40f;   // already falling when the snow starts
        }
    }

    /** A new flake at the top: far ones small and slow, near ones large, slow and faint. */
    private void spawn(int i) {
        boolean isNear = i >= far;
        float z = isNear ? 0.8f + rnd.nextFloat() * 0.2f : (float) Math.pow(rnd.nextFloat(), 1.4) * 0.6f;
        x[i] = rnd.nextFloat() * REF_W;
        y[i] = -20f - rnd.nextFloat() * 40f;
        vy[i] = isNear ? 24f + rnd.nextFloat() * 14f : 16f + 46f * z;
        sway[i] = 8f + rnd.nextFloat() * 18f;
        phase[i] = rnd.nextFloat() * 6.28f;
        hz[i] = 0.08f + rnd.nextFloat() * 0.12f;
        r[i] = isNear ? 5f + rnd.nextFloat() * 3f : 0.9f + 2.4f * z;
        alpha[i] = isNear ? 0.22f + rnd.nextFloat() * 0.12f : 0.35f + 0.45f * z;
    }

    private float flakeX(int i) {
        return x[i] + (float) Math.sin(clock * hz[i] * 6.2832f + phase[i]) * sway[i];
    }

    // ---- ledges --------------------------------------------------------------------------------------

    /** Collects this frame's ledges, in reference units, with their caps. */
    private void gatherLedges(float k) {
        ledgeCount = 0;
        if (topButtons != null && topButtons.getVisibility() == View.VISIBLE) {
            for (int i = 0; i < topButtons.getChildCount(); i++) {
                View b = topButtons.getChildAt(i);
                if (b.getVisibility() != View.VISIBLE || b.getWidth() == 0) continue;
                addLedge(b, SnowPiles.PILL, b, b.getHeight() / 2f, k);
            }
        }
        if (grid == null) return;
        if (dock != null && dock.band() != null) {
            box.set(dock.band());
            if (map(grid, box)) addRect("dock", SnowPiles.DOCK, dock.bandRadius() * scaleOf(grid), 1f, k);
        }
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            int pos = grid.getChildAdapterPosition(child);
            if (pos < 0) continue;
            RecyclerView.ViewHolder vh = grid.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null || card.getWidth() == 0) continue;
            addLedge(card, SnowPiles.TILE, pos, AppAdapter.cornerRadius(card.getWidth(), density), k);
        }
    }

    private void addLedge(View v, int kind, Object key, float radiusPx, float k) {
        box.set(0, 0, v.getWidth(), v.getHeight());
        if (!map(v, box)) return;
        float zoom = box.width() / v.getWidth();
        addRect(key, kind, radiusPx * zoom, zoom, k);
    }

    private void addRect(Object key, int kind, float radiusPx, float zoom, float k) {
        if (box.bottom < 0 || box.top > root.getHeight()) return;
        if (ledgeCount == ledges.size()) ledges.add(new Ledge());
        Ledge l = ledges.get(ledgeCount++);
        l.key = key;
        l.kind = kind;
        l.rect.set(box.left / k, box.top / k, box.right / k, box.bottom / k);
        l.radius = radiusPx / k;
        l.scale = zoom;
        l.cap = PILES.cap(key, kind, l.rect.width() / zoom);
    }

    /** Maps {@code rect}, in {@code v}'s own coordinates, into the root's, through every transform (a focus zoom). */
    private boolean map(View v, RectF rect) {
        View view = v;
        while (view != root) {
            Matrix m = view.getMatrix();
            if (!m.isIdentity()) m.mapRect(rect);
            rect.offset(view.getLeft(), view.getTop());
            ViewParent p = view.getParent();
            if (!(p instanceof View)) return false;
            view = (View) p;
            rect.offset(-view.getScrollX(), -view.getScrollY());
        }
        return true;
    }

    private float scaleOf(View v) {
        float s = 1f;
        for (View view = v; view != root && view != null; view = view.getParent() instanceof View ? (View) view.getParent() : null) {
            s *= view.getScaleX();
        }
        return s;
    }

    /** A flake at reference x crossed from {@code before} to {@code after}: lands on the first ledge it crosses. */
    private boolean land(float fx, float before, float after, float radius) {
        for (int i = 0; i < ledgeCount; i++) {
            Ledge l = ledges.get(i);
            if (fx < l.rect.left || fx > l.rect.right) continue;
            int j = Math.min(l.cap.cols - 1, (int) ((fx - l.rect.left) / l.rect.width() * l.cap.cols));
            SnowPiles.outline(j, l.cap.cols, l.rect.width(), l.radius, outline);
            float top = l.rect.top + outline[0];
            if (before < top && after >= top && SnowPiles.land(l.cap, j, outline[1], radius, rnd.nextFloat())) {
                return true;
            }
        }
        return false;
    }

    // ---- frames ----------------------------------------------------------------------------------------

    /** Steps the snow by one frame and draws the front: near flakes and the caps. */
    private void frame(Canvas c) {
        long now = System.nanoTime();
        long interval = lastNs == 0 ? 0 : now - lastNs;
        float dt = Math.min(0.05f, interval / 1e9f);
        lastNs = now;
        clock += dt;
        float k = root.getWidth() / REF_W;
        if (k <= 0) return;
        if (afterMelt == null && configured > 0 && tier.frame(interval)) {
            prefs.edit().putInt(TIER, tier.tier()).putBoolean(SETTLED, tier.settled()).putString(THEME, theme).apply();
            resize();
        }
        if (piles) gatherLedges(k);
        if (afterMelt != null) {
            if (!PILES.melt(dt)) {
                Runnable done = afterMelt;
                afterMelt = null;
                running = false;
                drawCaps(c, k);
                done.run();
                return;
            }
        } else if (far + near > 0) {
            for (int i = 0; i < far + near; i++) {
                float before = y[i];
                y[i] += vy[i] * dt;
                if (y[i] > 760f || piles && land(flakeX(i), before, y[i], r[i])) spawn(i);
            }
        } else if (piles) {
            // No flakes to draw on this TV (or none wanted): the caps grow from flakes nobody sees.
            virtualDebt += dt * VIRTUAL_RATE * nominal;
            while (virtualDebt >= 1f && ledgeCount > 0) {
                virtualDebt -= 1f;
                Ledge l = ledges.get(rnd.nextInt(ledgeCount));
                int j = rnd.nextInt(l.cap.cols);
                SnowPiles.outline(j, l.cap.cols, l.rect.width(), l.radius, outline);
                SnowPiles.land(l.cap, j, outline[1], 2f, rnd.nextFloat());
            }
        }
        for (int i = far; i < far + near && afterMelt == null; i++) drawFlake(c, i, k);
        drawCaps(c, k);
        back.invalidate();
        if (!running) return;
        if (far + near > 0 || afterMelt != null) front.postInvalidateOnAnimation();
        else front.postDelayed(redraw, VIRTUAL_FRAME_MS);
    }

    private void drawFlake(Canvas c, int i, float k) {
        if (y[i] < -10f) return;
        flakePaint.setAlpha(Math.round(255 * alpha[i]));
        c.drawCircle(flakeX(i) * k, y[i] * k, r[i] * k, flakePaint);
    }

    /** Snow thinner than this (reference units) is not drawn, so bare stretches of an edge stay bare. */
    private static final float BARE = 0.15f;
    /** How far the snow reaches down over the edge, at most, so no seam shows between it and the ledge. */
    private static final float OVERLAP = 0.8f;

    private void drawCaps(Canvas c, float k) {
        capPaint.setAlpha(247);
        for (int i = 0; i < ledgeCount; i++) {
            Ledge l = ledges.get(i);
            SnowPiles.Cap cap = l.cap;
            if (cap.empty()) continue;
            // Only where snow lies: each run of columns with snow is its own drift, starting and ending at the edge.
            int j = 0;
            while (j < cap.cols) {
                while (j < cap.cols && height(l, j) < BARE) j++;
                if (j == cap.cols) break;
                int first = j;
                while (j < cap.cols && height(l, j) >= BARE) j++;
                drawRun(c, l, first, j - 1, k);
            }
        }
    }

    /** The snow's height drawn at column {@code j} of a ledge, in reference units at its current zoom. */
    private float height(Ledge l, int j) {
        SnowPiles.outline(j, l.cap.cols, l.rect.width(), l.radius, outline);
        return SnowPiles.smoothed(l.cap, j) * outline[1] * l.scale;
    }

    /** One drift from column {@code first} to {@code last}: a curve over its heights, back along the edge below. */
    private void drawRun(Canvas c, Ledge l, int first, int last, float k) {
        int cols = l.cap.cols;
        float w = l.rect.width(), half = w / cols / 2;
        capPath.rewind();
        // the drift begins half a column out, on the edge, so its ends taper instead of standing up square
        SnowPiles.outline(first, cols, w, l.radius, outline);
        float x0 = l.rect.left + (first + 0.5f) / cols * w - half;
        capPath.moveTo(x0 * k, (l.rect.top + outline[0]) * k);
        float px = x0, py = l.rect.top + outline[0];
        for (int j = first; j <= last; j++) {
            float h = height(l, j);   // also leaves this column's outline in outline[]
            float cx = l.rect.left + (j + 0.5f) / cols * w, cy = l.rect.top + outline[0] - h;
            capPath.quadTo(px * k, py * k, (px + cx) / 2 * k, (py + cy) / 2 * k);
            px = cx;
            py = cy;
        }
        SnowPiles.outline(last, cols, w, l.radius, outline);
        float xe = l.rect.left + (last + 0.5f) / cols * w + half, ye = l.rect.top + outline[0];
        capPath.quadTo(px * k, py * k, xe * k, ye * k);
        // back along the edge, reaching a little down over it where the snow is thick enough
        for (int j = last; j >= first; j--) {
            float h = height(l, j);
            capPath.lineTo((l.rect.left + (j + 0.5f) / cols * w) * k,
                    (l.rect.top + outline[0] + Math.min(OVERLAP, h)) * k);
        }
        capPath.close();
        c.drawPath(capPath, capPaint);
    }

    // ---- the two views ---------------------------------------------------------------------------------

    @SuppressLint("ViewConstructor")
    private final class FallView extends View {
        FallView(Context context) {
            super(context);
            setFocusable(false);
            setClickable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!running || afterMelt != null) return;
            float k = getWidth() / REF_W;
            for (int i = 0; i < far; i++) drawFlake(c, i, k);
        }
    }

    @SuppressLint("ViewConstructor")
    private final class FrontView extends View {
        FrontView(Context context) {
            super(context);
            setFocusable(false);
            setClickable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas c) {
            if (running || afterMelt != null) frame(c);
            else drawCaps(c, getWidth() / REF_W);
        }
    }
}
