package org.datavyu.views.discrete;

import org.datavyu.util.Timestamp;
import org.datavyu.views.VideoController;
import org.datavyu.views.discrete.layouts.TimeAxis;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
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

    /** The needle's colour. */
    static final Color NEEDLE = new Color(204, 34, 34);

    private static final Color STRIP = new Color(244, 244, 244);
    private static final Color EDGE = new Color(190, 190, 190);
    private static final Font TIME_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 11);
    private static final Font RATE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
    private static final int BADGE_HEIGHT = 30;
    private static final int BADGE_MARGIN = 3;

    private final JViewport viewport;
    private final Supplier<Optional<TimeAxis>> axis;
    private final LongConsumer seek;
    private final IntConsumer lineAt;
    private final BooleanSupplier lineAcross;

    /** A time and the rate at it, shown together. */
    private record Reading(long time, String rate) {
    }

    /** What the playhead shows, set by the clock's threads as one, so a time never shows with another tick's rate. */
    private volatile Reading reading = new Reading(0, "stopped");

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
    public void show(final long time, final String rate) {
        this.reading = new Reading(time, rate);
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

    /** Puts the needle and its line where the time falls now, after the time, the cells or the layout change. */
    public void refresh() {
        long time = reading.time();
        lineAt.accept(lineAcross.getAsBoolean() ? axis.get().map(a -> a.y(time)).orElse(-1) : -1);
        repaint();
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
            g.setColor(STRIP);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(EDGE);
            g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());

            int top = SwingUtilities.convertPoint(viewport, 0, 0, this).y;
            int bottom = top + viewport.getExtentSize().height;
            Reading shown = reading;
            Optional<Integer> needle = axis.get().map(a -> toStrip(a.y(shown.time())));
            String marker = "";
            int badgeCentre;
            if (needle.isEmpty()) {
                badgeCentre = top + BADGE_MARGIN + BADGE_HEIGHT / 2;
            } else if (needle.get() < top) {
                marker = "▲ ";
                badgeCentre = top + BADGE_MARGIN + BADGE_HEIGHT / 2;
            } else if (needle.get() >= bottom) {
                marker = "▼ ";
                badgeCentre = bottom - BADGE_MARGIN - BADGE_HEIGHT / 2;
            } else {
                g.setColor(NEEDLE);
                g.setStroke(new BasicStroke(2));
                g.drawLine(0, needle.get(), getWidth(), needle.get());
                badgeCentre = Math.max(top + BADGE_HEIGHT / 2,
                        Math.min(needle.get(), bottom - BADGE_HEIGHT / 2));
            }
            paintBadge(g, badgeCentre, shown.time(), marker + shown.rate());
        } finally {
            g.dispose();
        }
    }

    private void paintBadge(final Graphics2D g, final int centre, final long time, final String rateText) {
        int x = BADGE_MARGIN;
        int y = centre - BADGE_HEIGHT / 2;
        int width = getWidth() - 2 * BADGE_MARGIN - 1;
        g.setColor(NEEDLE);
        g.fillRoundRect(x, y, width, BADGE_HEIGHT, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(TIME_FONT);
        FontMetrics timeMetrics = g.getFontMetrics();
        String timeText = Timestamp.format(time);
        g.drawString(timeText, x + (width - timeMetrics.stringWidth(timeText)) / 2, y + 2 + timeMetrics.getAscent());
        g.setFont(RATE_FONT);
        FontMetrics rateMetrics = g.getFontMetrics();
        g.drawString(rateText, x + (width - rateMetrics.stringWidth(rateText)) / 2,
                y + BADGE_HEIGHT - 3 - rateMetrics.getDescent());
    }
}
