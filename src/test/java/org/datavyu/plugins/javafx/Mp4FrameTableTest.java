package org.datavyu.plugins.javafx;

import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Frame times read from the sample tables of the fixtures made by
 * tools/playback/make-unit-fixtures.sh, checked against ffprobe's frame pts_time.
 */
public class Mp4FrameTableTest {

    private static final double EPSILON = 1e-6;

    private static File fixture(final String name) throws Exception {
        return new File(Mp4FrameTableTest.class.getResource("/video/" + name).toURI());
    }

    private static void assertStarts(final FrameIndex index, final double... expectedMillis) {
        assertEquals(index.frameCount(), expectedMillis.length);
        for (int i = 0; i < expectedMillis.length; i++) {
            assertEquals(index.startMillis(i), expectedMillis[i], 1e-3, "frame " + i);
        }
    }

    @Test
    public void reordersBFramesAndAppliesTheEditListShift() throws Exception {
        FrameIndex index = Mp4FrameTable.read(fixture("cfr-bframes.mp4"));

        double[] expected = new double[12];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = i * 1000.0 / 30;
        }
        assertStarts(index, expected);
        assertEquals(index.endMillis(), 400.0, EPSILON);
        assertEquals(index.framesPerSecond(), 30.0, EPSILON);
        assertTrue(index.isConstantRate());
        assertTrue(index.isMeasured());
    }

    @Test
    public void readsQuickTimeMovies() throws Exception {
        FrameIndex mov = Mp4FrameTable.read(fixture("cfr-bframes.mov"));
        FrameIndex mp4 = Mp4FrameTable.read(fixture("cfr-bframes.mp4"));

        assertEquals(mov.frameCount(), mp4.frameCount());
        for (int i = 0; i < mov.frameCount(); i++) {
            assertEquals(mov.startMillis(i), mp4.startMillis(i), EPSILON);
        }
        assertEquals(mov.endMillis(), mp4.endMillis(), EPSILON);
    }

    @Test
    public void reportsVariableFrameRate() throws Exception {
        FrameIndex index = Mp4FrameTable.read(fixture("vfr.mp4"));

        assertStarts(index, 0, 33.333, 66.667, 100, 133.333, 233.333, 333.333, 433.333);
        assertEquals(index.endMillis(), 466.667, 1e-3);
        assertFalse(index.isConstantRate());
    }

    @Test
    public void delaysFramesByAnInitialEmptyEdit() throws Exception {
        FrameIndex index = Mp4FrameTable.read(fixture("delayed.mp4"));

        assertStarts(index, 480, 520, 560, 600, 640);
        assertEquals(index.endMillis(), 680.0, EPSILON);
        assertEquals(index.frameAt(0), 0, "times before the first frame show the first frame");
    }

    @Test
    public void rejectsFilesWithoutAMovieBox() throws Exception {
        File notVideo = Files.createTempFile("not-video", ".mp4").toFile();
        notVideo.deleteOnExit();
        Files.write(notVideo.toPath(), new byte[]{0, 0, 0, 16, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0});

        expectThrows(IOException.class, () -> Mp4FrameTable.read(notVideo));
    }
}
