package org.datavyu.plugins.javafx;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Drives {@link JavaFxPlayback} with a fake engine and a queue standing in for the JavaFX
 * Application Thread, so commands can be inspected before and after they run.
 */
public class JavaFxPlaybackTest {

    /** Records engine commands; plays back time only when told to. */
    private static final class FakeEngine implements PlaybackEngine {
        final List<String> commands = new ArrayList<>();
        Listener listener;
        double time;
        boolean disposed;

        @Override
        public void setListener(final Listener listener) {
            this.listener = listener;
        }

        @Override
        public void play() {
            commands.add("play");
        }

        @Override
        public void pause() {
            commands.add("pause");
        }

        @Override
        public void seek(final double millis) {
            commands.add(String.format("seek %.3f", millis));
            time = millis;
        }

        @Override
        public void setRate(final double rate) {
            commands.add("rate " + rate);
        }

        @Override
        public void setVolume(final double volume) {
            commands.add("volume " + volume);
        }

        @Override
        public double currentTimeMillis() {
            return time;
        }

        @Override
        public void dispose() {
            commands.add("dispose");
            disposed = true;
        }
    }

    private static final class QueueExecutor implements Executor {
        final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(final Runnable task) {
            tasks.add(task);
        }

        void drain() {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                task.run();
            }
        }
    }

    private FakeEngine engine;
    private QueueExecutor fx;
    private JavaFxPlayback playback;

    /** 30 fps for 10 s: frame i starts at i * 33.33 ms. */
    @BeforeMethod
    public void setUp() {
        double[] starts = new double[300];
        for (int i = 0; i < starts.length; i++) {
            starts[i] = i * 1000.0 / 30;
        }
        engine = new FakeEngine();
        fx = new QueueExecutor();
        playback = new JavaFxPlayback(engine, FrameIndex.measured(starts, 10_000), fx);
        fx.drain();
    }

    @Test
    public void reportsDurationAndFrameRateFromTheFrameIndex() {
        assertEquals(playback.durationMillis(), 10_000);
        assertEquals(playback.framesPerSecond(), 30F, 1e-4);
        assertTrue(playback.isStepEnabled());
    }

    @Test
    public void pausedSeekReportsTheStartOfTheTargetFrameImmediately() {
        playback.seek(1_010);

        assertEquals(playback.currentTimeMillis(), 1_000, "1010 ms is inside frame 30, which starts at 1000 ms");
        assertEquals(playback.currentFrame(), 30);
        assertTrue(engine.commands.isEmpty(), "the engine is only touched on its own thread");

        fx.drain();
        assertEquals(engine.commands, List.of("seek 1016.667"), "seeks aim at the middle of the frame");
    }

    @Test
    public void reportedTimeRoundTripsThroughSeek() {
        playback.seek(40);
        long reported = playback.currentTimeMillis();

        playback.seek(reported);

        assertEquals(reported, 34);
        assertEquals(playback.currentFrame(), 1);
    }

    @Test
    public void rapidSeeksAreCoalescedToTheLatestTarget() {
        for (long millis = 0; millis <= 5_000; millis += 100) {
            playback.seek(millis);
        }
        fx.drain();

        assertEquals(engine.commands, List.of("seek 5016.667"));
    }

    @Test
    public void seeksOutsideTheStreamClampToTheFirstAndLastFrame() {
        playback.seek(-200);
        assertEquals(playback.currentFrame(), 0);

        playback.seek(10_000);
        assertEquals(playback.currentFrame(), 299, "seeking to the end shows the last frame");
        fx.drain();
        assertEquals(engine.commands, List.of("seek 9983.333"));
    }

    @Test
    public void playStartsAtTheRequestedRateAndReportsEngineTime() {
        playback.setRate(2F);
        playback.play();
        fx.drain();
        engine.time = 1_234.5;

        assertTrue(playback.isPlaying());
        assertEquals(engine.commands, List.of("play", "rate 2.0"), "the rate is applied after playback starts");
        assertEquals(playback.currentTimeMillis(), 1_234);
    }

    @Test
    public void changingRateWhilePlayingUpdatesTheEngine() {
        playback.play();
        fx.drain();
        engine.commands.clear();

        playback.setRate(4F);
        fx.drain();

        assertEquals(engine.commands, List.of("rate 4.0"));
        assertEquals(playback.rate(), 4F);
    }

    @Test
    public void pauseSettlesOnTheFrameWherePlaybackStopped() {
        playback.play();
        fx.drain();
        engine.time = 2_010;

        playback.pause();
        assertFalse(playback.isPlaying());
        assertEquals(playback.currentTimeMillis(), 2_000);

        fx.drain();
        assertEquals(engine.commands.get(engine.commands.size() - 1), "pause");
        assertEquals(playback.currentFrame(), 60);
    }

    @Test
    public void aSeekAfterPauseIsNotOverwrittenByThePausePosition() {
        playback.play();
        fx.drain();
        engine.time = 2_010;

        playback.pause();
        playback.seek(500);
        fx.drain();

        assertEquals(playback.currentFrame(), 15);
        assertEquals(playback.currentTimeMillis(), 500);
    }

    @Test
    public void zeroOrNegativeRatePausesBecauseReverseIsUnsupported() {
        playback.play();
        playback.setRate(-1F);

        assertFalse(playback.isPlaying());
        assertEquals(playback.rate(), 1F, "the last forward rate is kept");

        playback.play();
        playback.setRate(0F);
        assertFalse(playback.isPlaying());
    }

    @Test
    public void stepsMoveExactlyOneFrameAndPauseFirst() {
        playback.seek(1_000);
        playback.play();
        fx.drain();
        engine.time = 1_000;

        playback.stepForward();
        assertFalse(playback.isPlaying());
        assertEquals(playback.currentFrame(), 31);

        playback.stepBackward();
        playback.stepBackward();
        assertEquals(playback.currentFrame(), 29);
        fx.drain();
        assertEquals(engine.commands.get(engine.commands.size() - 1), "seek 983.333");
    }

    @Test
    public void stepsStopAtTheEndsOfTheStream() {
        playback.stepBackward();
        assertEquals(playback.currentFrame(), 0);

        playback.seek(10_000);
        playback.stepForward();
        assertEquals(playback.currentFrame(), 299);
    }

    @Test
    public void endOfMediaPausesOnTheLastFrame() {
        playback.play();
        fx.drain();
        engine.commands.clear();

        engine.listener.endOfMedia();

        assertFalse(playback.isPlaying());
        assertEquals(playback.currentFrame(), 299);
        assertEquals(engine.commands, List.of("pause"), "pausing clears the engine's finished state");
    }

    @Test
    public void engineErrorsStopPlaybackAndReachTheHandler() {
        List<Throwable> errors = new ArrayList<>();
        playback.setErrorHandler(errors::add);
        playback.play();
        fx.drain();

        RuntimeException failure = new RuntimeException("decoder failed");
        engine.listener.error(failure);

        assertFalse(playback.isPlaying());
        assertEquals(errors, List.of(failure));
    }

    @Test
    public void disposeReleasesTheEngineOnceAndIgnoresLaterCalls() {
        playback.seek(1_000);
        playback.dispose();
        playback.dispose();
        playback.play();
        playback.seek(2_000);
        playback.setVolume(0.5F);
        fx.drain();

        assertTrue(playback.isDisposed());
        assertFalse(playback.isPlaying());
        assertEquals(engine.commands, List.of("dispose"), "queued commands are dropped after dispose");
    }

    @Test
    public void unmeasuredFrameTimesDisableStepping() {
        JavaFxPlayback assumed = new JavaFxPlayback(new FakeEngine(), FrameIndex.assumed(30, 1_000), fx);

        assertFalse(assumed.isStepEnabled());
    }

    @Test
    public void listenerIsRegisteredOnTheEngineThread() {
        FakeEngine fresh = new FakeEngine();
        new JavaFxPlayback(fresh, FrameIndex.assumed(30, 1_000), fx);

        assertNull(fresh.listener);
        fx.drain();
        assertNotNull(fresh.listener);
    }
}
