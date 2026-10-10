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

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link PlaybackEngine} backed by an OpenJFX {@link MediaPlayer}. Every method except
 * {@link #open} and {@link #currentTimeMillis()} must run on the JavaFX Application Thread.
 *
 * Exact seeking and prompt playback start depend on the patched libjfxmedia_avf in
 * third_party/openjfx-media-avf; the stock OpenJFX library truncates seeks to whole seconds
 * and starts playback about 350 ms late.
 */
final class FxPlaybackEngine implements PlaybackEngine {

    /** The fastest rate MediaPlayer accepts. */
    static final double MAX_RATE = 8.0;

    private final MediaPlayer player;
    private final int width;
    private final int height;
    private final double durationMillis;
    private final ChangeListener<Duration> timeListener;
    private volatile double currentTimeMillis;
    private Listener listener;

    private FxPlaybackEngine(final MediaPlayer player) {
        this.player = player;
        Media media = player.getMedia();
        this.width = media.getWidth();
        this.height = media.getHeight();
        this.durationMillis = media.getDuration().toMillis();
        this.timeListener = (property, oldTime, newTime) -> currentTimeMillis = newTime.toMillis();
        player.currentTimeProperty().addListener(timeListener);
        player.setOnEndOfMedia(() -> {
            if (listener != null) {
                listener.endOfMedia();
            }
        });
        player.setOnError(() -> {
            if (listener != null) {
                listener.error(player.getError());
            }
        });
    }

    /**
     * Opens a file and waits until the player is ready. The caller blocks, so this must
     * run on a background thread.
     *
     * @throws MediaOpenException If the file is unsupported, unreadable or too slow to load.
     */
    static FxPlaybackEngine open(final File file, final long timeoutSeconds) {
        JavaFxToolkit.requireBackgroundThread();
        JavaFxToolkit.ensureStarted();
        CompletableFuture<FxPlaybackEngine> ready = new CompletableFuture<>();
        AtomicReference<MediaPlayer> created = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Media media = new Media(file.toURI().toString());
                MediaPlayer player = new MediaPlayer(media);
                created.set(player);
                media.setOnError(() -> ready.completeExceptionally(media.getError()));
                player.setOnError(() -> ready.completeExceptionally(player.getError()));
                player.setOnReady(() -> {
                    if (!ready.isDone()) {
                        ready.complete(new FxPlaybackEngine(player));
                    }
                });
            } catch (Throwable t) {
                ready.completeExceptionally(t);
            }
        });
        try {
            return ready.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            ready.cancel(false);
            disposeLater(created);
            throw MediaOpenException.timedOut(file, timeoutSeconds);
        } catch (ExecutionException e) {
            disposeLater(created);
            throw MediaOpenException.from(file, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ready.cancel(false);
            disposeLater(created);
            throw new MediaOpenException("Interrupted while opening " + file.getName(), e);
        }
    }

    private static void disposeLater(final AtomicReference<MediaPlayer> created) {
        Platform.runLater(() -> {
            MediaPlayer player = created.get();
            if (player != null) {
                player.dispose();
            }
        });
    }

    /** @return The player for a MediaView. */
    MediaPlayer player() {
        return player;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    double durationMillis() {
        return durationMillis;
    }

    @Override
    public void setListener(final Listener listener) {
        this.listener = listener;
    }

    @Override
    public void play() {
        player.play();
    }

    @Override
    public void pause() {
        player.pause();
        currentTimeMillis = player.getCurrentTime().toMillis();
    }

    @Override
    public void seek(final double millis) {
        player.seek(Duration.millis(millis));
        currentTimeMillis = millis;
    }

    @Override
    public void setRate(final double rate) {
        player.setRate(Math.min(rate, MAX_RATE));
    }

    @Override
    public void setVolume(final double volume) {
        player.setVolume(volume);
    }

    @Override
    public double currentTimeMillis() {
        return currentTimeMillis;
    }

    @Override
    public void dispose() {
        player.currentTimeProperty().removeListener(timeListener);
        player.setOnEndOfMedia(null);
        player.setOnError(null);
        player.setOnReady(null);
        player.getMedia().setOnError(null);
        listener = null;
        player.dispose();
    }
}
