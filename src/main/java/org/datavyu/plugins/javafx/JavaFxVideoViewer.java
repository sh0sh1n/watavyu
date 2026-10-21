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
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.Datavyu;
import org.datavyu.models.Identifier;
import org.datavyu.plugins.StreamViewerDialog;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.File;

/**
 * A Swing video window showing an OpenJFX {@link MediaView} through a {@link JFXPanel}.
 * Construct on the Swing EDT with an engine that is already ready; see {@link JavaFxVideoPlugin}.
 */
public final class JavaFxVideoViewer extends StreamViewerDialog {

    private static final Logger logger = LogManager.getLogger(JavaFxVideoViewer.class);

    /** New windows take at most this fraction of the screen width. */
    private static final double INITIAL_MAX_SCREEN_FRACTION = 0.5;

    /** The size of a new window for media without video. */
    private static final int AUDIO_WIDTH = 480;
    private static final int AUDIO_HEIGHT = 270;

    private final FxPlaybackEngine engine;
    private final JavaFxPlayback playback;
    private final JFXPanel panel = new JFXPanel();

    JavaFxVideoViewer(final Identifier identifier, final File sourceFile, final Frame parent, final boolean modal,
                      final FxPlaybackEngine engine, final FrameIndex frames) {
        super(identifier, parent, modal);
        this.engine = engine;
        this.playback = new JavaFxPlayback(engine, frames, Platform::runLater);
        this.isAssumedFramesPerSecond = !frames.isMeasured();
        playback.setErrorHandler(error -> SwingUtilities.invokeLater(() -> showPlaybackError(error)));

        panel.setPreferredSize(initialSize());
        getContentPane().add(panel);
        Platform.runLater(() -> {
            if (playback.isDisposed()) {
                return;
            }
            MediaView view = new MediaView(engine.player());
            view.setPreserveRatio(true);
            StackPane root = new StackPane(view);
            root.setMinSize(0, 0);
            Scene scene = new Scene(root, Color.BLACK);
            view.fitWidthProperty().bind(scene.widthProperty());
            view.fitHeightProperty().bind(scene.heightProperty());
            panel.setScene(scene);
        });

        logger.info("Opened " + sourceFile.getAbsolutePath() + " (" + engine.width() + "x" + engine.height()
                + ", " + frames.frameCount() + " frames, " + String.format("%.3f", frames.framesPerSecond()) + " fps, "
                + (frames.isMeasured() ? (frames.isConstantRate() ? "constant" : "variable") + " frame rate"
                : "frame times unavailable") + ")");
        setSourceFile(sourceFile);
        Datavyu.getVideoController().getClockTimer().registerListener(this);
    }

    private Dimension initialSize() {
        if (engine.width() <= 0 || engine.height() <= 0) {
            // Audio has no picture: a black window of a usual size.
            return new Dimension(AUDIO_WIDTH, AUDIO_HEIGHT);
        }
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        double scale = Math.min(1.0, INITIAL_MAX_SCREEN_FRACTION * screen.width / engine.width());
        return new Dimension((int) (engine.width() * scale), (int) (engine.height() * scale));
    }

    private void showPlaybackError(final Throwable error) {
        logger.error("Playback error in " + getSourceFile(), error);
        JOptionPane.showMessageDialog(this, "Playback failed for " + getSourceFile().getName() + ":\n"
                + error.getMessage(), "Video Error", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    protected void setPlayerVolume(final float volume) {
        playback.setVolume(volume);
    }

    @Override
    protected Dimension getOriginalVideoSize() {
        return new Dimension(engine.width(), engine.height());
    }

    /** @return the JavaFX player's own volume and status, as {@code "1.0 PLAYING"}, for tests */
    String playerState() throws Exception {
        return JavaFxToolkit.onFx(() -> engine.player().getVolume() + " " + engine.player().getStatus()
                + (engine.player().isMute() ? " muted" : "")).get();
    }

    @Override
    public Component getVideoComponent() {
        return panel;
    }

    @Override
    public void setCurrentTime(final long time) {
        playback.seek(time);
    }

    @Override
    protected float getPlayerFramesPerSecond() {
        return playback.framesPerSecond();
    }

    @Override
    public long getDuration() {
        return playback.durationMillis();
    }

    @Override
    public long getCurrentTime() {
        return playback.currentTimeMillis();
    }

    @Override
    public void start() {
        playback.play();
    }

    @Override
    public void stop() {
        playback.pause();
    }

    @Override
    public void pause() {
        playback.pause();
    }

    @Override
    public boolean isPlaying() {
        return playback.isPlaying();
    }

    @Override
    public void setRate(final float rate) {
        playBackRate = rate;
        playback.setRate(rate);
    }

    @Override
    public void stepForward() {
        playback.stepForward();
    }

    @Override
    public void stepBackward() {
        playback.stepBackward();
    }

    @Override
    public boolean isStepEnabled() {
        return playback.isStepEnabled();
    }

    @Override
    public boolean isReversePlaybackSupported() {
        return false;
    }

    @Override
    protected void cleanUp() {
        Datavyu.getVideoController().getClockTimer().unRegisterListener(this);
        playback.dispose();
        Platform.runLater(() -> panel.setScene(null));
    }
}
