package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * The Christmas icons for the top bar: a gift for Inputs, a fir tree for Wi-Fi, a snowflake for Settings.
 * <ul>
 *   <li>The tree's lights are the signal: one row of lights for each level above the first (cut out of the tree), and
 *       the star on top while online; offline it stands dark. On focus the lights come on one after another from the
 *       bottom, then stay lit in the accent, the star with them.</li>
 *   <li>The gift's ribbon is cut out and lights in the accent; on focus the lid hops up and settles.</li>
 *   <li>The snowflake turns a sixth on focus; its centre lights in the accent.</li>
 * </ul>
 * Shapes are built once (the tree in one variant per number of light rows); a frame only moves the canvas. See
 * SeasonalIconDrawable for what all the icons share.
 */
public final class ChristmasIconDrawable extends SeasonalIconDrawable {

    static final int GIFT = 0, TREE = 1, FLAKE = 2;

    private static final long[] ANIM_MS = {700, 900, 900};

    /** The tree's lights, three rows from the bottom, as x, y pairs. */
    private static final float[][] LIGHTS = {
            {7.6f, 18.2f, 10.4f, 18.9f, 13.6f, 18.9f, 16.4f, 18.2f},
            {8.8f, 13.9f, 12f, 14.6f, 15.2f, 13.9f},
            {10.4f, 9.6f, 13.6f, 9.6f}};
    private static final float LIGHT_R = 0.95f;

    /** The tree with 0 to 3 rows of lights cut out; the star; the gift's box and lid with the ribbon cut out. */
    private final Path[] trees = new Path[LIGHTS.length + 1];
    private final Path star = new Path(), smallStar = new Path();
    private final Path box = new Path(), lid = new Path(), ribbonBox = new Path(), ribbonLid = new Path();
    private final Path flake = new Path();

    ChristmasIconDrawable(int kind, float density) {
        super(kind, density, SeasonalLook.CHRISTMAS_RED);
        if (kind == TREE) buildTree();
        else if (kind == GIFT) buildGift();
        else buildFlake();
    }

    @Override
    long animMs() {
        return ANIM_MS[kind];
    }

    // ---- shapes, in box units ----------------------------------------------------------------------

    private void buildTree() {
        Path tree = new Path();
        tree.moveTo(12f, 4.6f);
        tree.lineTo(16.6f, 10.6f);
        tree.lineTo(14.6f, 10.6f);
        tree.lineTo(18.6f, 15.6f);
        tree.lineTo(16.4f, 15.6f);
        tree.lineTo(20.6f, 20.4f);
        tree.lineTo(3.4f, 20.4f);
        tree.lineTo(7.6f, 15.6f);
        tree.lineTo(5.4f, 15.6f);
        tree.lineTo(9.4f, 10.6f);
        tree.lineTo(7.4f, 10.6f);
        tree.close();
        tree.addRect(10.6f, 20.2f, 13.4f, 22.8f, Path.Direction.CW);   // trunk
        Path cut = new Path();
        for (int rows = 0; rows <= LIGHTS.length; rows++) {
            if (rows > 0) {
                float[] row = LIGHTS[rows - 1];
                for (int i = 0; i < row.length; i += 2) cut.addCircle(row[i], row[i + 1], LIGHT_R, Path.Direction.CW);
            }
            trees[rows] = new Path(tree);
            trees[rows].op(cut, Path.Op.DIFFERENCE);
        }
        starPath(star, 12f, 3.6f, 2.3f);
        starPath(smallStar, 12f, 3.6f, 1.2f);
    }

    private static void starPath(Path p, float cx, float cy, float r) {
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = i % 2 == 1 ? r * 0.45f : r;
            float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
    }

    private void buildGift() {
        // The box sits at its place; the lid and bow are drawn around the origin, and moved on the canvas when they hop.
        ribbonBox.addRect(11.1f, 11.6f, 12.9f, 21.6f, Path.Direction.CW);
        box.addRect(4.4f, 11.6f, 19.6f, 21.6f, Path.Direction.CW);
        box.op(ribbonBox, Path.Op.DIFFERENCE);
        lid.addRoundRect(new RectF(-9f, -1.8f, 9f, 1.8f), 0.8f, 0.8f, Path.Direction.CW);
        Path bow = new Path();
        bow.addOval(new RectF(-5.3f, -5.7f, 0.1f, -2.3f), Path.Direction.CW);
        bow.addOval(new RectF(-0.1f, -5.7f, 5.3f, -2.3f), Path.Direction.CW);
        lid.op(bow, Path.Op.UNION);
        ribbonLid.addRect(-0.9f, -1.8f, 0.9f, 1.8f, Path.Direction.CW);
        lid.op(ribbonLid, Path.Op.DIFFERENCE);
    }

    private void buildFlake() {
        // Six arms around the origin, each with a pair of branches, as strokes.
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            arm(0f, 0f, 0f, -9.4f, cos, sin);
            arm(0f, -5.6f, -2.4f, -8f, cos, sin);
            arm(0f, -5.6f, 2.4f, -8f, cos, sin);
        }
    }

    /** One stroke from (x0, y0) to (x1, y1), turned by the angle with the given cosine and sine. */
    private void arm(float x0, float y0, float x1, float y1, float cos, float sin) {
        flake.moveTo(x0 * cos - y0 * sin, x0 * sin + y0 * cos);
        flake.lineTo(x1 * cos - y1 * sin, x1 * sin + y1 * cos);
    }

    // ---- drawing -----------------------------------------------------------------------------------

    @Override
    void drawIcon(Canvas c, float p) {
        if (kind == TREE) drawTree(c, p);
        else if (kind == GIFT) drawGift(c, p);
        else drawFlake(c, p);
    }

    private void drawTree(Canvas c, float p) {
        int rows = Math.max(0, level - 1);
        c.drawPath(trees[rows], ink);
        if (level > 0) c.drawPath(star, ink);
        if (!focused) return;
        // The lights come on one after another, bottom to top, two lights fading in at a time.
        int count = 0;
        for (int r = 0; r < rows; r++) count += LIGHTS[r].length / 2;
        lit.setColor(accent);
        int i = 0;
        for (int r = 0; r < rows; r++) {
            float[] row = LIGHTS[r];
            for (int k = 0; k < row.length; k += 2, i++) {
                float on = p < 0 ? 1f : Math.min(1f, Math.max(0f, (p * (count + 2) - i) / 2f));
                lit.setAlpha(Math.round(alpha * on));
                c.drawCircle(row[k], row[k + 1], LIGHT_R, lit);
            }
        }
        if (level > 0) {
            lit.setAlpha(Math.round(alpha * (p < 0 ? 1f : Math.max(0f, p * 2 - 1))));
            c.drawPath(smallStar, lit);
        }
    }

    private void drawGift(Canvas c, float p) {
        float up = p < 0 ? 0f : (float) Math.sin(Math.min(1f, p / 0.6f) * Math.PI);
        float hop = up * 3.2f, tilt = up * -10f;   // degrees
        c.drawPath(box, ink);
        if (focused) {
            lit.setColor(accent);
            lit.setAlpha(alpha);
            c.drawPath(ribbonBox, lit);
        }
        int save = c.save();
        c.translate(12f, 10.6f - hop);
        c.rotate(tilt);
        c.drawPath(lid, ink);
        if (focused) c.drawPath(ribbonLid, lit);
        c.restoreToCount(save);
    }

    private void drawFlake(Canvas c, float p) {
        int save = c.save();
        c.translate(12f, 12f);
        if (p >= 0) c.rotate(ease(p) * 60f);
        c.drawPath(flake, line);
        if (focused) {
            lit.setColor(accent);
            lit.setAlpha(alpha);
            c.drawCircle(0f, 0f, 2f, lit);
        }
        c.restoreToCount(save);
    }
}
