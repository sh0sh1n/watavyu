package org.datavyu.plugins.javafx;

import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;

import java.util.concurrent.TimeUnit;

/**
 * Reads which frame a player is displaying from the barcode burned in by
 * tools/playback/make-fixtures.sh: 16 cells of 64x64 px along the top-left edge,
 * white where the matching bit of the zero-based frame number is set.
 *
 * The probe renders the player through its own off-screen MediaView cropped to the
 * barcode, so snapshots are small. JavaFX only receives video frames while a display
 * is awake.
 */
final class FrameProbe {

    static final int UNREADABLE = -1;

    private static final int CELL = 64;
    private static final int BITS = 16;

    private final MediaView view;

    private FrameProbe(final MediaView view) {
        this.view = view;
    }

    static FrameProbe attach(final MediaPlayer player) throws Exception {
        return JavaFxToolkit.onFx(() -> {
            MediaView view = new MediaView(player);
            view.setViewport(new Rectangle2D(0, 0, CELL * BITS, CELL));
            new Scene(new Group(view), CELL * BITS, CELL);
            return new FrameProbe(view);
        }).get(10, TimeUnit.SECONDS);
    }

    /** @return The displayed frame number, or {@link #UNREADABLE} if any cell is ambiguous. */
    int read() throws Exception {
        return JavaFxToolkit.onFx(() -> {
            WritableImage image = view.snapshot(null, null);
            PixelReader pixels = image.getPixelReader();
            if (pixels == null || image.getWidth() < CELL * BITS) {
                return UNREADABLE;
            }
            int value = 0;
            for (int bit = 0; bit < BITS; bit++) {
                int argb = pixels.getArgb(bit * CELL + CELL / 2, CELL / 2);
                int luma = ((argb >> 16 & 0xff) * 299 + (argb >> 8 & 0xff) * 587 + (argb & 0xff) * 114) / 1000;
                if (luma > 192) {
                    value |= 1 << bit;
                } else if (luma > 64) {
                    return UNREADABLE;
                }
            }
            return value;
        }).get(10, TimeUnit.SECONDS);
    }

    /**
     * Polls until the expected frame is displayed.
     *
     * @return Nanoseconds from {@code startNanos} until the frame appeared, or -1 on timeout.
     */
    long awaitFrame(final int expected, final long startNanos, final long timeoutMillis) throws Exception {
        long deadline = startNanos + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (read() == expected) {
                return System.nanoTime() - startNanos;
            }
        }
        return -1;
    }

    /** Waits for the display to stop changing and returns the frame shown. */
    int settledFrame(final long quietMillis) throws Exception {
        int last = read();
        long stableSince = System.nanoTime();
        long deadline = stableSince + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            int now = read();
            if (now != last) {
                last = now;
                stableSince = System.nanoTime();
            } else if (System.nanoTime() - stableSince >= TimeUnit.MILLISECONDS.toNanos(quietMillis)) {
                break;
            }
        }
        return last;
    }
}
