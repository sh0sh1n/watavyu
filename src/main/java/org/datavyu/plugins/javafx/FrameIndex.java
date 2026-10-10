/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.datavyu.plugins.javafx;

import java.util.Arrays;

/**
 * Presentation times of every video frame in a stream, in milliseconds.
 *
 * Datavyu works in whole milliseconds while frames start at fractional times
 * (frame 1 of 30 fps video starts at 33.33 ms). {@link #reportedMillis(int)} rounds
 * a frame start up so that {@link #frameAt(long)} maps the reported time back to the
 * same frame. Seeks aim at {@link #seekTargetMillis(int)}, the middle of the frame, so
 * that rounding inside the decoder cannot land on the previous frame.
 */
public final class FrameIndex {

    /** Frame durations within this tolerance are treated as one constant rate. */
    private static final double CONSTANT_RATE_TOLERANCE_MILLIS = 1.0;

    private final double[] startMillis;
    private final double endMillis;
    private final boolean measured;

    private FrameIndex(final double[] startMillis, final double endMillis, final boolean measured) {
        if (startMillis.length == 0) {
            throw new IllegalArgumentException("A frame index needs at least one frame");
        }
        for (int i = 1; i < startMillis.length; i++) {
            if (startMillis[i] <= startMillis[i - 1]) {
                throw new IllegalArgumentException("Frame times must be strictly increasing at frame " + i);
            }
        }
        if (endMillis <= startMillis[startMillis.length - 1]) {
            throw new IllegalArgumentException("The stream must end after its last frame starts");
        }
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.measured = measured;
    }

    /**
     * @param startMillis Presentation start of each frame, strictly increasing.
     * @param endMillis End of the last frame.
     */
    public static FrameIndex measured(final double[] startMillis, final double endMillis) {
        return new FrameIndex(startMillis.clone(), endMillis, true);
    }

    /**
     * An index for a stream whose frame times could not be read. Frames are assumed to
     * be evenly spaced, so stepping by frame is not trustworthy; see {@link #isMeasured()}.
     */
    public static FrameIndex assumed(final double framesPerSecond, final double durationMillis) {
        double frameMillis = 1000.0 / framesPerSecond;
        // The tolerance keeps a duration that is an exact multiple of frames from gaining a frame.
        int count = Math.max(1, (int) Math.ceil(durationMillis / frameMillis - 1e-9));
        double[] starts = new double[count];
        for (int i = 0; i < count; i++) {
            starts[i] = i * frameMillis;
        }
        return new FrameIndex(starts, Math.max(durationMillis, starts[count - 1] + frameMillis), false);
    }

    /** @return True when frame times were read from the file rather than assumed. */
    public boolean isMeasured() {
        return measured;
    }

    public int frameCount() {
        return startMillis.length;
    }

    /** @return End of the last frame, which is the stream duration. */
    public double endMillis() {
        return endMillis;
    }

    /** @return Average frame rate over the stream. */
    public double framesPerSecond() {
        return startMillis.length * 1000.0 / (endMillis - startMillis[0]);
    }

    /** @return True when every frame lasts the same time, within a millisecond. */
    public boolean isConstantRate() {
        double min = Double.MAX_VALUE;
        double max = 0;
        for (int i = 0; i < startMillis.length; i++) {
            double duration = frameDurationMillis(i);
            min = Math.min(min, duration);
            max = Math.max(max, duration);
        }
        return max - min <= CONSTANT_RATE_TOLERANCE_MILLIS;
    }

    /**
     * @param millis A stream time.
     * @return The frame on screen at that time, clamped to the first and last frame.
     */
    public int frameAt(final double millis) {
        int i = Arrays.binarySearch(startMillis, millis);
        int frame = i >= 0 ? i : -i - 2;
        return Math.max(0, Math.min(frame, startMillis.length - 1));
    }

    public double startMillis(final int frame) {
        return startMillis[frame];
    }

    /** @return The frame start rounded up to a whole millisecond, which still maps back to this frame. */
    public long reportedMillis(final int frame) {
        return (long) Math.ceil(startMillis[frame]);
    }

    /** @return The middle of the frame, where an exact seek reliably shows this frame. */
    public double seekTargetMillis(final int frame) {
        return startMillis[frame] + frameDurationMillis(frame) / 2;
    }

    private double frameDurationMillis(final int frame) {
        double next = frame + 1 < startMillis.length ? startMillis[frame + 1] : endMillis;
        return next - startMillis[frame];
    }
}
