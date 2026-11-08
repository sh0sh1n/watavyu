package com.sh0sh1n.watavyu.views;

import com.sh0sh1n.watavyu.views.spreadsheet.Palette;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.Taskbar;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * The application's icon, drawn in code like the {@link Glyph}s rather than read from an image file: the play mark, in
 * the accent, between the onset and offset brackets, on a Graphite tile. It is drawn on Apple's 1024 unit icon grid,
 * whose tile is 824 units square with room around it for a shadow, and is the same in light and dark appearance.
 *
 * <p>Run from Gradle, the app is a bare {@code java} process with no bundle to name an icon, so macOS shows Java's own
 * until {@link #install()} sets this one in the Dock.
 */
public final class AppIcon {

    /** The square the icon is drawn on, as Apple's icon grid. */
    static final double GRID = 1024;

    /** The middle of the tile. */
    static final double CENTRE = GRID / 2;

    /** Half the tile's width: 824 units, 100 in from each edge. */
    static final double TILE_RADIUS = 412;

    /** Where the brackets' stems stand and how far they reach. */
    static final double LEFT_STEM = 262;
    static final double RIGHT_STEM = 762;
    static final double BRACKET_TOP = 322;
    static final double BRACKET_BOTTOM = 702;
    private static final double BRACKET_ARM = 90;
    private static final float BRACKET_STROKE = 46;

    /** The play mark: where it stands, its height, and the round edge that softens its corners. */
    static final double PLAY_X = 518;
    private static final double PLAY_HEIGHT = 230;
    private static final float PLAY_STROKE = 26;

    /** How round the tile's corners are, as the exponent of a superellipse; Apple's continuous corners are near 5. */
    private static final double TILE_ROUNDNESS = 5;
    private static final int TILE_STEPS = 720;

    private static final int SHADOW_RINGS = 12;
    private static final float SHADOW_SPREAD = 2.4F;
    private static final double SHADOW_DROP = 10;
    private static final Color SHADOW = new Color(0, 0, 0, 5);
    private static final Color RIM = new Color(255, 255, 255, 30);
    private static final float RIM_STROKE = 3;

    /** The colours, from the dark palette whatever the Mac's appearance, as a Dock icon has one look. */
    static final Palette PALETTE = Palette.GRAPHITE;

    /** The size macOS draws Dock icons from. */
    private static final int DOCK_SIZE = 1024;

    private AppIcon() {
    }

    /**
     * Sets the icon in the Dock, where macOS supports it.
     *
     * @return whether it was set
     */
    public static boolean install() {
        if (!Taskbar.isTaskbarSupported() || !Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
            return false;
        }
        Taskbar.getTaskbar().setIconImage(image(DOCK_SIZE));
        return true;
    }

    /**
     * Returns the icon drawn on a square image.
     *
     * @param size its width and height, in pixels
     */
    public static BufferedImage image(final int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(size / GRID, size / GRID);
            paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void paint(final Graphics2D g) {
        Shape tile = tile();
        g.setColor(SHADOW);
        AffineTransform drop = AffineTransform.getTranslateInstance(0, SHADOW_DROP);
        for (int ring = SHADOW_RINGS; ring >= 1; ring--) {
            g.fill(drop.createTransformedShape(new BasicStroke(ring * SHADOW_SPREAD).createStrokedShape(tile)));
        }
        double top = CENTRE - TILE_RADIUS;
        double bottom = CENTRE + TILE_RADIUS;
        g.setPaint(new GradientPaint(0, (float) top, PALETTE.rule(), 0, (float) bottom, PALETTE.chrome()));
        g.fill(tile);
        g.setColor(RIM);
        g.setStroke(new BasicStroke(RIM_STROKE));
        g.draw(tile);

        g.setColor(PALETTE.text());
        g.setStroke(round(BRACKET_STROKE));
        g.draw(bracket(LEFT_STEM, BRACKET_ARM));
        g.draw(bracket(RIGHT_STEM, -BRACKET_ARM));

        g.setColor(PALETTE.accent());
        Shape play = play();
        g.fill(play);
        g.setStroke(round(PLAY_STROKE));
        g.draw(play);
    }

    /** The tile: a superellipse, close to the continuous corners of macOS icons. */
    static Shape tile() {
        Path2D path = new Path2D.Double();
        double power = 2 / TILE_ROUNDNESS;
        for (int step = 0; step < TILE_STEPS; step++) {
            double angle = 2 * Math.PI * step / TILE_STEPS;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double x = CENTRE + TILE_RADIUS * Math.signum(cos) * Math.pow(Math.abs(cos), power);
            double y = CENTRE + TILE_RADIUS * Math.signum(sin) * Math.pow(Math.abs(sin), power);
            if (step == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.closePath();
        return path;
    }

    /** A bracket whose stem stands at x and whose arms reach by arm, rightward for an onset and leftward for an offset. */
    private static Shape bracket(final double x, final double arm) {
        Path2D path = new Path2D.Double();
        path.moveTo(x + arm, BRACKET_TOP);
        path.lineTo(x, BRACKET_TOP);
        path.lineTo(x, BRACKET_BOTTOM);
        path.lineTo(x + arm, BRACKET_BOTTOM);
        return path;
    }

    /** The play mark, a triangle pointing right, its weight between the brackets so it looks centred. */
    private static Shape play() {
        double width = PLAY_HEIGHT * 0.88;
        Path2D path = new Path2D.Double();
        path.moveTo(PLAY_X - width * 0.4, CENTRE - PLAY_HEIGHT / 2);
        path.lineTo(PLAY_X + width * 0.6, CENTRE);
        path.lineTo(PLAY_X - width * 0.4, CENTRE + PLAY_HEIGHT / 2);
        path.closePath();
        return path;
    }

    private static Stroke round(final float width) {
        return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }
}
