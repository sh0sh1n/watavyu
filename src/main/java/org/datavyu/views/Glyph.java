package org.datavyu.views;

import org.datavyu.views.discrete.SpreadsheetTheme;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * A line glyph drawn in code, in place of an image file (AGENTS.md › Decisions, U5). Each is drawn with a 1.5 unit
 * stroke on an 18 unit square, scaled to the icon's size, in the colour of the component it is on: the accent while a
 * button is pressed, and the ordinal colour while it is disabled, so it follows the palette.
 *
 * @param shape  what it shows
 * @param size   its width and height, in pixels
 * @param colour its colour, or null to take the component's
 */
public record Glyph(Shape shape, int size, Color colour) implements Icon {

    /** The square glyphs are drawn on. */
    static final double GRID = 18;

    private static final float STROKE = 1.5F;

    /** What a glyph shows. */
    public enum Shape {
        /** Set onset: an opening bracket and a line out of it. */
        SET_ONSET,
        /** Set offset: a line into a closing bracket. */
        SET_OFFSET,
        PLAY,
        STOP,
        PAUSE,
        SHUTTLE_BACK,
        SHUTTLE_FORWARD,
        /** Jog back: a bar and a triangle pointing at it. */
        JOG_BACK,
        /** Jog forward: a triangle pointing at a bar. */
        JOG_FORWARD,
        /** Jump back: an arrow turning back. */
        JUMP_BACK,
        /** Find: crosshairs. */
        FIND,
        /** New cell: a box with a plus. */
        NEW_CELL,
        /** New cell and set the previous one's offset: a closing bracket beside a new cell. */
        NEW_CELL_PREVIOUS_OFFSET,
        /** A locked column: a padlock. */
        LOCK,
        /** Volume: a speaker with sound waves. */
        VOLUME,
        LEFT,
        RIGHT,
        UP,
        DOWN,
        /** Something is wrong, such as a name that cannot be applied: an exclamation mark. */
        WARNING
    }

    /**
     * Returns the glyph in the colour of the component it is on.
     *
     * @param shape what it shows
     * @param size  its width and height, in pixels
     */
    public static Glyph of(final Shape shape, final int size) {
        return new Glyph(shape, size, null);
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(colourOn(c));
            g2.translate(x, y);
            g2.scale(size / GRID, size / GRID);
            g2.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            for (java.awt.Shape line : lines(shape)) {
                g2.draw(line);
            }
            for (java.awt.Shape dot : dots(shape)) {
                g2.fill(dot);
            }
        } finally {
            g2.dispose();
        }
    }

    private Color colourOn(final Component c) {
        if (c instanceof AbstractButton button) {
            if (!button.isEnabled()) {
                return SpreadsheetTheme.PALETTE.ordinal();
            }
            if (button.getModel().isArmed() && button.getModel().isPressed()) {
                return SpreadsheetTheme.PALETTE.accent();
            }
        }
        if (colour != null) {
            return colour;
        }
        return c == null ? SpreadsheetTheme.PALETTE.text() : c.getForeground();
    }

    /**
     * Returns a shape's stroked lines, on the 18 unit grid.
     *
     * @param shape a shape
     */
    static List<java.awt.Shape> lines(final Shape shape) {
        return switch (shape) {
            case SET_ONSET -> List.of(path(8, 3, 4, 3, 4, 15, 8, 15), line(8, 9, 15, 9));
            case SET_OFFSET -> List.of(path(10, 3, 14, 3, 14, 15, 10, 15), line(3, 9, 10, 9));
            case PLAY -> List.of(closed(5, 3, 15, 9, 5, 15));
            case STOP -> List.of(new Rectangle2D.Double(4, 4, 10, 10));
            case PAUSE -> List.of(line(6, 4, 6, 14), line(12, 4, 12, 14));
            case SHUTTLE_BACK -> List.of(closed(9, 4, 3, 9, 9, 14), closed(16, 4, 10, 9, 16, 14));
            case SHUTTLE_FORWARD -> List.of(closed(2, 4, 8, 9, 2, 14), closed(9, 4, 15, 9, 9, 14));
            case JOG_BACK -> List.of(line(4, 4, 4, 14), closed(14, 4, 7, 9, 14, 14));
            case JOG_FORWARD -> List.of(line(14, 4, 14, 14), closed(4, 4, 11, 9, 4, 14));
            case JUMP_BACK -> List.of(
                    line(6, 6, 11, 6), new Arc2D.Double(7, 6, 8, 8, 90, -180, Arc2D.OPEN), line(11, 14, 5, 14),
                    path(8, 3, 5, 6, 8, 9));
            case FIND -> List.of(new Ellipse2D.Double(4, 4, 10, 10),
                    line(9, 1, 9, 5), line(9, 13, 9, 17), line(1, 9, 5, 9), line(13, 9, 17, 9));
            case NEW_CELL -> List.of(new Rectangle2D.Double(3, 3, 12, 12), line(9, 6, 9, 12), line(6, 9, 12, 9));
            case NEW_CELL_PREVIOUS_OFFSET -> List.of(path(2, 3, 5, 3, 5, 15, 2, 15),
                    new Rectangle2D.Double(8, 3, 8, 12), line(12, 6, 12, 12), line(9, 9, 15, 9));
            case LOCK -> List.of(new Rectangle2D.Double(4, 8, 10, 7),
                    line(6, 8, 6, 5), new Arc2D.Double(6, 2, 6, 6, 180, -180, Arc2D.OPEN), line(12, 5, 12, 8));
            case VOLUME -> List.of(closed(3, 7, 6, 7, 10, 4, 10, 14, 6, 11, 3, 11),
                    new Arc2D.Double(8.5, 6.5, 5, 5, -50, 100, Arc2D.OPEN),
                    new Arc2D.Double(6, 4, 10, 10, -50, 100, Arc2D.OPEN));
            case LEFT -> List.of(line(15, 9, 3, 9), path(7, 5, 3, 9, 7, 13));
            case RIGHT -> List.of(line(3, 9, 15, 9), path(11, 5, 15, 9, 11, 13));
            case UP -> List.of(line(9, 15, 9, 3), path(5, 7, 9, 3, 13, 7));
            case DOWN -> List.of(line(9, 3, 9, 15), path(5, 11, 9, 15, 13, 11));
            case WARNING -> List.of(line(9, 3, 9, 11));
        };
    }

    /**
     * Returns a shape's filled dots, on the 18 unit grid.
     *
     * @param shape a shape
     */
    static List<java.awt.Shape> dots(final Shape shape) {
        return switch (shape) {
            case WARNING -> List.of(new Ellipse2D.Double(7.9, 13.4, 2.2, 2.2));
            default -> List.of();
        };
    }

    private static Line2D line(final double x1, final double y1, final double x2, final double y2) {
        return new Line2D.Double(x1, y1, x2, y2);
    }

    private static Path2D path(final double... xy) {
        Path2D path = new Path2D.Double();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            path.lineTo(xy[i], xy[i + 1]);
        }
        return path;
    }

    private static Path2D closed(final double... xy) {
        Path2D path = path(xy);
        path.closePath();
        return path;
    }
}
