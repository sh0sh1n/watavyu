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

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Datavyu's playback contract on top of a {@link PlaybackEngine}.
 *
 * Methods may be called from any thread (Swing EDT, clock timer threads) and never block:
 * engine commands are queued on {@code engineThread}. While paused, the position is a frame
 * of the {@link FrameIndex}, reported in whole milliseconds. Seeks requested faster than the
 * engine thread runs them are coalesced so that only the latest target is decoded.
 *
 * Reverse playback is not supported: a negative rate pauses.
 */
public final class JavaFxPlayback {

    private final PlaybackEngine engine;
    private final FrameIndex frames;
    private final Executor engineThread;
    private final AtomicReference<Double> pendingSeek = new AtomicReference<>();

    /** Increments whenever the paused position is set explicitly. */
    private final AtomicLong positionVersion = new AtomicLong();

    private volatile boolean playing;
    private volatile boolean disposed;
    private volatile float rate = 1F;
    private volatile int frame;
    private volatile Consumer<Throwable> errorHandler = error -> { };

    /**
     * @param engine Engine to drive; this playback takes ownership and disposes it.
     * @param frames Frame times of the engine's video stream.
     * @param engineThread Executor for the engine's thread.
     */
    public JavaFxPlayback(final PlaybackEngine engine, final FrameIndex frames, final Executor engineThread) {
        this.engine = engine;
        this.frames = frames;
        this.engineThread = engineThread;
        onEngine(() -> engine.setListener(new PlaybackEngine.Listener() {
            @Override
            public void endOfMedia() {
                playing = false;
                engine.pause();
                positionVersion.incrementAndGet();
                frame = frames.frameCount() - 1;
            }

            @Override
            public void error(final Throwable error) {
                playing = false;
                errorHandler.accept(error);
            }
        }));
    }

    /** @param handler Receives playback errors on the engine thread. */
    public void setErrorHandler(final Consumer<Throwable> handler) {
        errorHandler = handler;
    }

    public long durationMillis() {
        return (long) frames.endMillis();
    }

    public float framesPerSecond() {
        return (float) frames.framesPerSecond();
    }

    /** @return True when frame times were read from the file, so single-frame steps are exact. */
    public boolean isStepEnabled() {
        return frames.isMeasured();
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isDisposed() {
        return disposed;
    }

    /** @return The requested forward rate, used the next time playback starts. */
    public float rate() {
        return rate;
    }

    /** @return The playing position, or the start of the displayed frame while paused. */
    public long currentTimeMillis() {
        if (playing) {
            return (long) engine.currentTimeMillis();
        }
        return frames.reportedMillis(frame);
    }

    /** @return The frame shown while paused. */
    public int currentFrame() {
        return playing ? frames.frameAt(engine.currentTimeMillis()) : frame;
    }

    /** Shows the frame on screen at the given time, clamped to the stream. */
    public void seek(final long millis) {
        seekFrame(frames.frameAt(millis));
    }

    public void play() {
        if (disposed || playing) {
            return;
        }
        playing = true;
        float playRate = rate;
        // AVFoundation resets the rate to 1 when playback starts, so set it afterwards.
        onEngine(() -> {
            engine.play();
            engine.setRate(playRate);
        });
    }

    public void pause() {
        if (disposed || !playing) {
            return;
        }
        playing = false;
        long version = positionVersion.incrementAndGet();
        frame = frames.frameAt(engine.currentTimeMillis());
        onEngine(() -> {
            engine.pause();
            // Keep a seek requested after this pause; otherwise record where playback stopped.
            if (!playing && positionVersion.get() == version) {
                frame = frames.frameAt(engine.currentTimeMillis());
            }
        });
    }

    /**
     * @param newRate Positive rates play forward at that speed; zero or negative pauses,
     *                because this backend cannot play in reverse.
     */
    public void setRate(final float newRate) {
        if (newRate <= 0) {
            pause();
            return;
        }
        rate = newRate;
        if (playing) {
            onEngine(() -> engine.setRate(newRate));
        }
    }

    public void stepForward() {
        pause();
        seekFrame(frame + 1);
    }

    public void stepBackward() {
        pause();
        seekFrame(frame - 1);
    }

    /** @param volume Linear volume from 0 to 1. */
    public void setVolume(final float volume) {
        onEngine(() -> engine.setVolume(volume));
    }

    /** Releases the engine. Safe to call more than once; later calls to other methods do nothing. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        playing = false;
        engineThread.execute(engine::dispose);
    }

    private void seekFrame(final int requested) {
        if (disposed) {
            return;
        }
        int target = Math.max(0, Math.min(requested, frames.frameCount() - 1));
        positionVersion.incrementAndGet();
        frame = target;
        if (pendingSeek.getAndSet(frames.seekTargetMillis(target)) == null) {
            onEngine(() -> {
                Double millis = pendingSeek.getAndSet(null);
                if (millis != null) {
                    engine.seek(millis);
                }
            });
        }
    }

    private void onEngine(final Runnable command) {
        engineThread.execute(() -> {
            if (!disposed) {
                command.run();
            }
        });
    }
}
