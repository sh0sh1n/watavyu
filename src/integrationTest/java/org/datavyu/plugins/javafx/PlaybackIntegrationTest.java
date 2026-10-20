package org.datavyu.plugins.javafx;

import javafx.application.Platform;
import org.datavyu.Datavyu;
import org.datavyu.models.Identifier;
import org.datavyu.plugins.StreamViewer;
import org.datavyu.util.ClockTimer;
import org.datavyu.views.VideoController;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.fail;

/**
 * Drives the real OpenJFX player through {@link JavaFxPlayback} on a generated 10 s,
 * 30 fps H.264/AAC MP4 whose frames carry their own number as a barcode.
 *
 * Needs ffmpeg on the PATH (the fixture is generated into build/playback-fixtures)
 * and an awake display; run with: caffeinate -d ./gradlew integrationTest
 */
public class PlaybackIntegrationTest {

    private static final int FPS = 30;
    private static final int SECONDS = 10;
    private static final int FRAMES = FPS * SECONDS;
    private static final long SEEK_TIMEOUT_MILLIS = 2_000;

    private File mp4;
    private File mov;
    private FxPlaybackEngine engine;
    private JavaFxPlayback playback;
    private FrameProbe probe;
    private FrameIndex frames;

    @BeforeClass
    public void createFixture() throws Exception {
        String ffmpeg = findFfmpeg();
        File dir = new File(System.getProperty("fixtureDir", "build/playback-fixtures"));
        mp4 = new File(dir, "barcode-10s.mp4");
        mov = new File(dir, "barcode-10s.mov");
        if (!mp4.isFile() || !mov.isFile()) {
            dir.mkdirs();
            String barcode = "color=c=black:s=1024x64:r=" + FPS
                    + ",format=gray,geq=lum='if(bitand(N\\,pow(2\\,floor(X/64)))\\,255\\,0)'";
            run(ffmpeg, "-v", "error", "-y",
                    "-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=" + FPS,
                    "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000",
                    "-f", "lavfi", "-i", barcode,
                    "-filter_complex", "[2:v]format=yuv420p[bar];[0:v][bar]overlay=0:0:shortest=1[v]",
                    "-map", "[v]", "-map", "1:a", "-t", Integer.toString(SECONDS),
                    "-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p",
                    "-g", Integer.toString(FPS), "-keyint_min", Integer.toString(FPS), "-sc_threshold", "0",
                    "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", mp4.getPath());
            run(ffmpeg, "-v", "error", "-y", "-i", mp4.getPath(), "-c", "copy", mov.getPath());
        }
        JavaFxToolkit.ensureStarted();
    }

    private static String findFfmpeg() {
        for (String candidate : Arrays.asList("/opt/homebrew/bin/ffmpeg", "/usr/local/bin/ffmpeg")) {
            if (new File(candidate).canExecute()) {
                return candidate;
            }
        }
        for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
            if (new File(dir, "ffmpeg").canExecute()) {
                return new File(dir, "ffmpeg").getPath();
            }
        }
        throw new SkipException("ffmpeg is needed to generate the playback fixture");
    }

    private static void run(final String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).inheritIO().start();
        if (process.waitFor() != 0) {
            throw new IOException("Command failed: " + String.join(" ", command));
        }
    }

    @BeforeMethod
    public void open() throws Exception {
        engine = FxPlaybackEngine.open(mp4, 30);
        frames = Mp4FrameTable.read(mp4);
        playback = new JavaFxPlayback(engine, frames, Platform::runLater);
        playback.setVolume(0);
        probe = FrameProbe.attach(engine.player());
        assertEquals(probe.settledFrame(200), 0, "The first frame shows after opening (is the display awake?)");
    }

    @AfterMethod(alwaysRun = true)
    public void close() {
        if (playback != null) {
            playback.dispose();
        }
    }

    @AfterClass(alwaysRun = true)
    public void drainFxQueue() throws Exception {
        JavaFxToolkit.onFx(() -> null).get(5, TimeUnit.SECONDS);
    }

    private void assertShows(final int frame) throws Exception {
        long start = System.nanoTime();
        long latency = probe.awaitFrame(frame, start, SEEK_TIMEOUT_MILLIS);
        if (latency < 0) {
            fail("Expected frame " + frame + " but the display shows " + probe.read());
        }
        assertEquals(probe.settledFrame(150), frame, "the display moved off the target frame");
    }

    /** A video window reports what a project file stores about its clip and source. */
    @Test
    public void aVideoWindowReportsWhatAProjectFileStores() throws Exception {
        VideoController videoController = mock(VideoController.class);
        when(videoController.getClockTimer()).thenReturn(mock(ClockTimer.class));
        Datavyu.setVideoController(videoController);
        StreamViewer viewer = JavaFxVideoPlugin.open(Identifier.generateIdentifier(), mp4, null, false);
        try {
            assertEquals(viewer.getVideoSize(), new Dimension(1280, 720));
            assertEquals(viewer.getFramesPerSecond(), (float) FPS, 1e-3);
            assertEquals(viewer.getDuration(), SECONDS * 1000L);

            JavaFxToolkit.onEdtAndWait(() -> {
                viewer.setVolumeLevel(0.25f);
                viewer.setViewerVisible(false);
                return null;
            });
            assertEquals(viewer.getVolumeLevel(), 0.25f);
            assertFalse(viewer.isViewerVisible());
        } finally {
            JavaFxToolkit.onEdtAndWait(() -> {
                viewer.close();
                return null;
            });
            Datavyu.setVideoController(null);
        }
    }

    /** A clip that is not its media column's clip in play pauses, and does not start with the clock. */
    @Test
    public void aClipOutOfPlayPauses() throws Exception {
        VideoController videoController = mock(VideoController.class);
        when(videoController.getClockTimer()).thenReturn(mock(ClockTimer.class));
        Datavyu.setVideoController(videoController);
        StreamViewer viewer = JavaFxVideoPlugin.open(Identifier.generateIdentifier(), mp4, null, false);
        try {
            viewer.start();
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (!viewer.isPlaying() && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(viewer.isPlaying(), "the clip starts");

            viewer.setInPlay(false);
            deadline = System.nanoTime() + 2_000_000_000L;
            while (viewer.isPlaying() && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertFalse(viewer.isPlaying(), "a clip out of play pauses");
        } finally {
            JavaFxToolkit.onEdtAndWait(() -> {
                viewer.close();
                return null;
            });
            Datavyu.setVideoController(null);
        }
    }

    @Test
    public void reportsDurationAndFrameRate() {
        assertEquals(frames.frameCount(), FRAMES);
        assertTrue(frames.isConstantRate());
        assertEquals(playback.framesPerSecond(), (float) FPS, 1e-3);
        assertEquals(playback.durationMillis(), SECONDS * 1000L);
        assertEquals(engine.durationMillis(), SECONDS * 1000.0, 50, "the player agrees on the duration");
        assertEquals(engine.width(), 1280);
        assertEquals(engine.height(), 720);
    }

    @Test
    public void pausedSeeksShowTheExactFrame() throws Exception {
        for (int frame : new int[]{0, 1, 29, 30, 31, 151, 238, 1, FRAMES - 1}) {
            playback.seek(frames.reportedMillis(frame));
            assertShows(frame);
            assertEquals(playback.currentFrame(), frame);
        }
    }

    @Test
    public void seekAfterPlayAndPauseShowsTheExactFrame() throws Exception {
        playback.seek(frames.reportedMillis(60));
        playback.play();
        Thread.sleep(500);
        playback.pause();
        assertFalse(playback.isPlaying());

        playback.seek(frames.reportedMillis(200));
        assertShows(200);
    }

    @Test
    public void seeksImmediatelyAfterPauseAlwaysShowTheTarget() throws Exception {
        // Datavyu's clock seeks every viewer right after pausing. Stock OpenJFX sometimes
        // left the frame from before the seek on screen here.
        java.util.Random random = new java.util.Random(3);
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            playback.play();
            Thread.sleep(150);
            playback.pause();
            int target = random.nextInt(FRAMES);
            playback.seek(frames.reportedMillis(target));
            if (probe.awaitFrame(target, System.nanoTime(), SEEK_TIMEOUT_MILLIS) < 0) {
                failures.add(target + " showed " + probe.read());
            }
        }
        assertTrue(failures.isEmpty(), "stale frames after pause and seek: " + failures);
    }

    @Test
    public void playsForwardFromAnOnset() throws Exception {
        playback.seek(frames.reportedMillis(90));
        assertShows(90);

        long start = System.nanoTime();
        playback.play();
        long firstAdvance = -1;
        while (System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2)) {
            int shown = probe.read();
            if (shown > 90) {
                firstAdvance = System.nanoTime() - start;
                break;
            }
        }
        assertTrue(firstAdvance > 0, "playback advanced past the onset");
        Thread.sleep(1_000);
        int shown = probe.read();
        playback.pause();

        assertTrue(shown > 90 + FPS / 2 && shown < 90 + 2 * FPS, "about one second played, showing " + shown);
        assertTrue(playback.currentFrame() >= shown - 2, "the paused position follows playback");
    }

    @Test
    public void playbackWithAudioStartsPromptly() throws Exception {
        // Stock OpenJFX takes about 470 ms here because of its audio processing tap;
        // the patched library takes about 135 ms.
        List<Long> starts = new ArrayList<>();
        for (int onset : new int[]{40, 120, 200}) {
            playback.seek(frames.reportedMillis(onset));
            assertShows(onset);
            long start = System.nanoTime();
            playback.play();
            while (probe.read() <= onset && System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2)) {
                Thread.onSpinWait();
            }
            starts.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            playback.pause();
        }
        Collections.sort(starts);
        assertTrue(starts.get(1) < 300, "median time to the first new frame was " + starts.get(1) + " ms: " + starts);
    }

    @Test
    public void playsAtTheRequestedRate() throws Exception {
        playback.seek(frames.reportedMillis(30));
        assertShows(30);
        playback.setRate(2F);
        playback.play();
        Thread.sleep(500);
        int from = probe.read();
        Thread.sleep(1_000);
        int to = probe.read();
        playback.pause();

        int advanced = to - from;
        assertTrue(advanced > 1.6 * FPS && advanced < 2.4 * FPS, "2x for 1 s advanced " + advanced + " frames");
    }

    @Test
    public void seekingToTheEndShowsTheLastFrameAndStaysPaused() throws Exception {
        playback.seek(playback.durationMillis());
        assertShows(FRAMES - 1);

        playback.seek(frames.reportedMillis(10));
        assertShows(10);
        Thread.sleep(300);
        assertEquals(probe.read(), 10, "a seek after the end must not resume playback");
        assertFalse(playback.isPlaying());
    }

    @Test
    public void playingToTheEndPausesAndLaterSeeksStayPaused() throws Exception {
        playback.seek(frames.reportedMillis(FRAMES - 15));
        assertShows(FRAMES - 15);
        playback.play();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (playback.isPlaying() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertFalse(playback.isPlaying(), "end of media stops playback");
        assertEquals(playback.currentFrame(), FRAMES - 1);

        playback.seek(frames.reportedMillis(20));
        assertShows(20);
        Thread.sleep(300);
        assertEquals(probe.read(), 20, "the player stays paused after a seek from the end");
    }

    @Test
    public void stepsMoveOneFrameEachWay() throws Exception {
        playback.seek(frames.reportedMillis(100));
        assertShows(100);

        List<Integer> shown = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            playback.stepForward();
            assertShows(101 + i);
            shown.add(probe.read());
        }
        for (int i = 0; i < 5; i++) {
            playback.stepBackward();
            assertShows(104 - i);
            shown.add(probe.read());
        }
        assertEquals(shown, Arrays.asList(101, 102, 103, 104, 105, 104, 103, 102, 101, 100));
        assertEquals(playback.currentTimeMillis(), frames.reportedMillis(100));
    }

    @Test
    public void rapidScrubbingEndsOnTheLastRequestedFrame() throws Exception {
        for (int frame = 10; frame <= 250; frame += 8) {
            playback.seek(frames.reportedMillis(frame));
            Thread.sleep(16);
        }
        assertShows(250);
    }

    @Test
    public void disposedPlaybackIgnoresCommands() throws Exception {
        playback.dispose();

        playback.play();
        playback.seek(5_000);
        playback.stepForward();
        JavaFxToolkit.onFx(() -> null).get(5, TimeUnit.SECONDS);

        assertTrue(playback.isDisposed());
        assertFalse(playback.isPlaying());
    }

    @Test
    public void quickTimeContainerIsRejectedWithARemuxHint() {
        MediaOpenException error = expectThrows(MediaOpenException.class, () -> FxPlaybackEngine.open(mov, 30));

        assertTrue(error.getMessage().contains("-c copy"), error.getMessage());
    }

    @Test
    public void openingMustNotBlockAUiThread() throws Exception {
        Throwable error = JavaFxToolkit.onFx(() -> {
            try {
                FxPlaybackEngine.open(mp4, 30);
                return null;
            } catch (IllegalStateException e) {
                return e;
            }
        }).get(5, TimeUnit.SECONDS);

        assertTrue(error instanceof IllegalStateException);
    }
}
