/*
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

    private final MediaPlayer mediaPlayer;
    private final int videoWidth;
    private final int videoHeight;
    private final double mediaDurationMillis;
    private final ChangeListener<Duration> timeListener;
    private volatile double lastTimeMillis;
    private Listener listener;

    private FxPlaybackEngine(final MediaPlayer player) {
        this.mediaPlayer = player;
        Media media = player.getMedia();
        this.videoWidth = media.getWidth();
        this.videoHeight = media.getHeight();
        this.mediaDurationMillis = media.getDuration().toMillis();
        this.timeListener = (property, oldTime, newTime) -> lastTimeMillis = newTime.toMillis();
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

    /** Returns the player for a MediaView. */
    MediaPlayer player() {
        return mediaPlayer;
    }

    int width() {
        return videoWidth;
    }

    int height() {
        return videoHeight;
    }

    double durationMillis() {
        return mediaDurationMillis;
    }

    @Override
    public void setListener(final Listener listener) {
        this.listener = listener;
    }

    @Override
    public void play() {
        mediaPlayer.play();
    }

    @Override
    public void pause() {
        mediaPlayer.pause();
        lastTimeMillis = mediaPlayer.getCurrentTime().toMillis();
    }

    @Override
    public void seek(final double millis) {
        mediaPlayer.seek(Duration.millis(millis));
        lastTimeMillis = millis;
    }

    @Override
    public void setRate(final double rate) {
        mediaPlayer.setRate(Math.min(rate, MAX_RATE));
    }

    @Override
    public void setVolume(final double volume) {
        mediaPlayer.setVolume(volume);
    }

    @Override
    public double currentTimeMillis() {
        return lastTimeMillis;
    }

    @Override
    public void dispose() {
        mediaPlayer.currentTimeProperty().removeListener(timeListener);
        mediaPlayer.setOnEndOfMedia(null);
        mediaPlayer.setOnError(null);
        mediaPlayer.setOnReady(null);
        mediaPlayer.getMedia().setOnError(null);
        listener = null;
        mediaPlayer.dispose();
    }
}
