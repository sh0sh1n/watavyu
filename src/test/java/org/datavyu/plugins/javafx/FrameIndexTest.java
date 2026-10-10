package org.datavyu.plugins.javafx;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class FrameIndexTest {

    /** 30 fps, 10 frames: frame i starts at i * 33.33 ms. */
    private static FrameIndex thirtyFps() {
        double[] starts = new double[10];
        for (int i = 0; i < starts.length; i++) {
            starts[i] = i * 1000.0 / 30;
        }
        return FrameIndex.measured(starts, 1000.0 / 3);
    }

    @Test
    public void mapsTimesToTheFrameOnScreen() {
        FrameIndex index = thirtyFps();

        assertEquals(index.frameAt(0), 0);
        assertEquals(index.frameAt(33), 0, "33 ms is still inside frame 0");
        assertEquals(index.frameAt(33.4), 1);
        assertEquals(index.frameAt(66.67), 2);
    }

    @Test
    public void clampsTimesOutsideTheStream() {
        FrameIndex index = thirtyFps();

        assertEquals(index.frameAt(-500), 0);
        assertEquals(index.frameAt(10_000), 9);
    }

    @Test
    public void reportedMillisecondsMapBackToTheSameFrame() {
        FrameIndex index = thirtyFps();

        for (int frame = 0; frame < index.frameCount(); frame++) {
            assertEquals(index.frameAt(index.reportedMillis(frame)), frame, "frame " + frame);
        }
        assertEquals(index.reportedMillis(1), 34);
        assertEquals(index.reportedMillis(2), 67);
    }

    @Test
    public void seeksAimAtTheMiddleOfAFrame() {
        FrameIndex index = thirtyFps();

        assertEquals(index.seekTargetMillis(0), 1000.0 / 60, 1e-9);
        assertEquals(index.seekTargetMillis(9), 9 * 1000.0 / 30 + 1000.0 / 60, 1e-9);
    }

    @Test
    public void detectsVariableFrameRate() {
        assertTrue(thirtyFps().isConstantRate());

        FrameIndex variable = FrameIndex.measured(new double[]{0, 33.3, 66.7, 166.7}, 200);
        assertFalse(variable.isConstantRate());
        assertEquals(variable.framesPerSecond(), 20.0, 1e-9);
    }

    @Test
    public void treatsMillisecondTimestampJitterAsConstantRate() {
        // 30 fps stored with a 1 ms timescale alternates 33 and 34 ms frames.
        FrameIndex index = FrameIndex.measured(new double[]{0, 33, 67, 100, 133}, 167);

        assertTrue(index.isConstantRate());
    }

    @Test
    public void assumedIndexesCoverTheDurationAndAreMarked() {
        FrameIndex index = FrameIndex.assumed(30, 1000);

        assertFalse(index.isMeasured());
        assertEquals(index.frameCount(), 30);
        assertEquals(index.endMillis(), 1000, 1e-9);
    }

    @Test
    public void rejectsUnorderedFrames() {
        expectThrows(IllegalArgumentException.class, () -> FrameIndex.measured(new double[]{0, 40, 40}, 80));
        expectThrows(IllegalArgumentException.class, () -> FrameIndex.measured(new double[]{0, 40}, 40));
        expectThrows(IllegalArgumentException.class, () -> FrameIndex.measured(new double[0], 40));
    }
}
