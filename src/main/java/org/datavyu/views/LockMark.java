package org.datavyu.views;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Graphics;

/**
 * The padlock shown on a locked column. It shakes from side to side when the column refuses a change, as a Mac's
 * password field does, so a keystroke that did nothing is seen rather than heard.
 */
public final class LockMark implements Icon {

    /** How long a shake lasts, in milliseconds. */
    static final int SHAKE_MILLIS = 360;

    /** How far the padlock swings each way at first, in pixels. */
    static final int AMPLITUDE = 3;

    /** How many times it swings back and forth. */
    private static final int SWINGS = 3;

    private final Icon padlock;
    private final JComponent host;
    private final Timer timer;
    private long started;
    private int offset;

    /**
     * @param size the padlock's size in pixels
     * @param host the component the mark is painted on, repainted as it shakes
     */
    public LockMark(final int size, final JComponent host) {
        this.padlock = Glyph.of(Glyph.Shape.LOCK, size);
        this.host = host;
        this.timer = new Timer(16, e -> step());
    }

    /** Shakes the padlock, starting again if it is already shaking. */
    public void shake() {
        started = System.nanoTime();
        offset = 0;
        timer.restart();
    }

    /** Returns whether the padlock is shaking. */
    public boolean isShaking() {
        return timer.isRunning();
    }

    private void step() {
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        offset = offsetAt(elapsed);
        if (elapsed >= SHAKE_MILLIS) {
            timer.stop();
        }
        host.repaint();
    }

    /**
     * Returns how far the padlock is from its place, in pixels: a swing that dies away, and nothing once it is over.
     *
     * @param elapsed milliseconds since the shake started
     */
    static int offsetAt(final long elapsed) {
        if (elapsed <= 0 || elapsed >= SHAKE_MILLIS) {
            return 0;
        }
        double progress = (double) elapsed / SHAKE_MILLIS;
        return (int) Math.round(AMPLITUDE * (1 - progress) * Math.sin(progress * SWINGS * 2 * Math.PI));
    }

    @Override
    public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
        padlock.paintIcon(c, g, x + offset, y);
    }

    @Override
    public int getIconWidth() {
        return padlock.getIconWidth();
    }

    @Override
    public int getIconHeight() {
        return padlock.getIconHeight();
    }
}
