package org.datavyu.views.discrete;

import org.datavyu.util.Timestamp;
import org.datavyu.views.VideoController;
import org.datavyu.views.discrete.layouts.TimeAxis;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * The playhead: a strip beside the spreadsheet's columns with a needle at the clock's time, carrying the time and
 * rate. The needle has no scale of its own; in a spreadsheet laid out in time it sits where its time falls among the
 * cells ({@link TimeAxis}), scrolls with them, and is dragged to move the clock. Its line can run on across the
 * columns. In a spreadsheet laid out by order, which places nothing in time, the strip shows the time at its top.
 */
public final class PlayheadCarriage extends JComponent implements VideoController.Playhead {

    /** The strip's width. */
    static final int WIDTH = 100;

    /** Where the needle stays, as a share of the sheet's visible height from its top, while the sheet follows it. */
    static final double FOLLOW_AT = 1.0 / 3;

    /** Width and height of the triangle that points to a needle out of view. */
    private static final int MARKER = 6;

    private static final Font TIME_FONT = SpreadsheetTheme.labelFont().deriveFont(11.5F);
    private static final Font RATE_FONT = SpreadsheetTheme.labelFont().deriveFont(10.5F);
    private static final int BADGE_HEIGHT = 30;
    private static final int BADGE_MARGIN = 3;

    private final JViewport viewport;
    private final Supplier<Optional<TimeAxis>> axis;
    private final LongConsumer seek;
    private final IntConsumer lineAt;
    private final BooleanSupplier lineAcross;

    /** A time and the rate at it, shown together, and whether playback is running. */
    private record Reading(long time, String rate, boolean playing) {
    }

    /** What the playhead shows, set by the clock's threads as one, so a time never shows with another tick's rate. */
    private volatile Reading reading = new Reading(0, "stopped", false);

    /** Whether a move of the needle is waiting for the EDT, so the clock's ticks do not pile up there. */
    private final AtomicBoolean moving = new AtomicBoolean();

    /**
     * @param viewport the spreadsheet's viewport, whose scrolling the needle follows
     * @param axis     where times fall down the sheet, or empty if the sheet is not laid out in time
     * @param seek     moves the clock to a time
     * @param lineAt     takes the height in the sheet where the playhead's line crosses the columns, or -1 for none
     * @param lineAcross whether the line crosses the columns at all
     */
    public PlayheadCarriage(final JViewport viewport, final Supplier<Optional<TimeAxis>> axis,
                            final LongConsumer seek, final IntConsumer lineAt, final BooleanSupplier lineAcross) {
        this.viewport = viewport;
        this.axis = axis;
        this.seek = seek;
        this.lineAt = lineAt;
        this.lineAcross = lineAcross;
        setName("playhead");
        setOpaque(true);
        setToolTipText("Drag the playhead to move through the media");
        viewport.addChangeListener(e -> repaint());
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent e) {
                moveTo(e.getY());
            }

            @Override
            public void mouseDragged(final MouseEvent e) {
                moveTo(e.getY());
            }

            @Override
            public void mouseWheelMoved(final MouseWheelEvent e) {
                // The strip scrolls the spreadsheet beside it.
                viewport.getParent().dispatchEvent(SwingUtilities.convertMouseEvent(PlayheadCarriage.this, e,
                        viewport.getParent()));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, super.getPreferredSize().height);
    }

    /** Shows the clock's time and rate; called from any thread. */
    @Override
    public void show(final long time, final String rate, final boolean playing) {
        this.reading = new Reading(time, rate, playing);
        if (moving.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                moving.set(false);
                refresh();
            });
        }
    }

    /** @return the time the playhead shows */
    long getTime() {
        return reading.time();
    }

    /** @return the rate the playhead shows */
    String getRate() {
        return reading.rate();
    }

    /**
     * Puts the needle and its line where the time falls now, after the time, the cells or the layout change. While
     * playback runs, the sheet scrolls to keep the needle {@link #FOLLOW_AT} of the way down.
     */
    public void refresh() {
        Reading shown = reading;
        Optional<Integer> sheetY = axis.get().map(a -> a.y(shown.time()));
        if (shown.playing()) {
            sheetY.ifPresent(this::follow);
        }
        lineAt.accept(lineAcross.getAsBoolean() ? sheetY.orElse(-1) : -1);
        repaint();
    }

    private void follow(final int sheetY) {
        Dimension extent = viewport.getExtentSize();
        Dimension view = viewport.getViewSize();
        int top = (int) Math.round(sheetY - extent.height * FOLLOW_AT);
        top = Math.max(0, Math.min(top, view.height - extent.height));
        Point position = viewport.getViewPosition();
        if (top != position.y) {
            viewport.setViewPosition(new Point(position.x, top));
        }
    }

    /**
     * @return the needle's height in this strip, or empty if the sheet is not laid out in time
     */
    Optional<Integer> needleY() {
        long time = reading.time();
        return axis.get().map(a -> toStrip(a.y(time)));
    }

    private int toStrip(final int sheetY) {
        Point viewTop = viewport.getViewPosition();
        return SwingUtilities.convertPoint(viewport, 0, sheetY - viewTop.y, this).y;
    }

    private void moveTo(final int stripY) {
        axis.get().ifPresent(a -> {
            int sheetY = SwingUtilities.convertPoint(this, 0, stripY, viewport).y + viewport.getViewPosition().y;
            seek.accept(a.time(sheetY));
        });
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(SpreadsheetTheme.PALETTE.chrome());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(SpreadsheetTheme.PALETTE.rule());
            g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());

            int top = SwingUtilities.convertPoint(viewport, 0, 0, this).y;
            int bottom = top + viewport.getExtentSize().height;
            Reading shown = reading;
            Optional<Integer> needle = axis.get().map(a -> toStrip(a.y(shown.time())));
            // Which way the needle lies when out of view: -1 above, 1 below, 0 in view or absent.
            int pointing = 0;
            int badgeCentre;
            if (needle.isEmpty()) {
                badgeCentre = top + BADGE_MARGIN + BADGE_HEIGHT / 2;
            } else if (needle.get() < top) {
                pointing = -1;
                badgeCentre = top + BADGE_MARGIN + BADGE_HEIGHT / 2;
            } else if (needle.get() >= bottom) {
                pointing = 1;
                badgeCentre = bottom - BADGE_MARGIN - BADGE_HEIGHT / 2;
            } else {
                g.setColor(SpreadsheetTheme.PALETTE.accent());
                g.setStroke(new BasicStroke(2));
                g.drawLine(0, needle.get(), getWidth(), needle.get());
                badgeCentre = Math.max(top + BADGE_HEIGHT / 2,
                        Math.min(needle.get(), bottom - BADGE_HEIGHT / 2));
            }
            paintBadge(g, badgeCentre, shown.time(), shown.rate(), pointing);
        } finally {
            g.dispose();
        }
    }

    private void paintBadge(final Graphics2D g, final int centre, final long time, final String rateText,
                            final int pointing) {
        int x = BADGE_MARGIN;
        int y = centre - BADGE_HEIGHT / 2;
        int width = getWidth() - 2 * BADGE_MARGIN - 1;
        // Plain text on the strip, not a coloured badge: the needle is the only mark in the accent.
        g.setColor(SpreadsheetTheme.PALETTE.chrome());
        g.fillRect(x, y, width, BADGE_HEIGHT);
        g.setColor(SpreadsheetTheme.PALETTE.text());
        g.setFont(TIME_FONT);
        FontMetrics timeMetrics = g.getFontMetrics();
        String timeText = Timestamp.format(time);
        g.drawString(timeText, x + (width - timeMetrics.stringWidth(timeText)) / 2, y + 2 + timeMetrics.getAscent());
        g.setColor(SpreadsheetTheme.PALETTE.time());
        g.setFont(RATE_FONT);
        FontMetrics rateMetrics = g.getFontMetrics();
        int markerWidth = pointing == 0 ? 0 : MARKER + MARKER / 2;
        int rateX = x + (width - rateMetrics.stringWidth(rateText) + markerWidth) / 2;
        int baseline = y + BADGE_HEIGHT - 3 - rateMetrics.getDescent();
        g.drawString(rateText, rateX, baseline);
        if (pointing != 0) {
            // Drawn, not typed: Plex Mono has no arrows.
            int left = rateX - markerWidth;
            int middle = baseline - rateMetrics.getAscent() / 2 + 1;
            int tip = middle + pointing * MARKER / 2;
            int base = middle - pointing * MARKER / 2;
            g.fillPolygon(new int[]{left, left + MARKER, left + MARKER / 2}, new int[]{base, base, tip}, 3);
        }
    }
}
