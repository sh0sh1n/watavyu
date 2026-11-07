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
package com.sh0sh1n.watavyu.plugins.javafx;

import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.sh0sh1n.watavyu.models.Identifier;
import com.sh0sh1n.watavyu.util.ClockTimer;
import com.sh0sh1n.watavyu.plugins.StreamViewer;

import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Video playback through OpenJFX Media, the only video player. Accepts only the containers OpenJFX documents for
 * H.264/AAC; QuickTime .mov is rejected by the player and must be remuxed to MP4.
 */
public final class JavaFxVideoPlugin {

    private static final Logger logger = LogManager.getLogger(JavaFxVideoPlugin.class);

    /** The extensions of the media files the player opens: video in MP4, and audio that OpenJFX plays on macOS. */
    public static final List<String> MEDIA_EXTENSIONS = List.of("mp4", "m4v", "m4a", "mp3", "wav", "aif", "aiff");

    /** The files the player opens, for file choosers. */
    public static final FileFilter VIDEO_FILE_FILTER =
            new FileNameExtensionFilter("Media files", MEDIA_EXTENSIONS.toArray(new String[0]));

    /** Frame rate assumed when a file's frame times cannot be read. */
    private static final double FALLBACK_FRAMES_PER_SECOND = 30;

    private static final long OPEN_TIMEOUT_SECONDS = 30;

    private JavaFxVideoPlugin() {
    }

    /**
     * Opens the file and builds its window. Blocks until the media is ready, so callers
     * must use a background thread; the window itself is built on the Swing EDT.
     *
     * @param clockTimer the clock the player follows
     * @throws MediaOpenException With a user-facing message if the file cannot be played.
     */
    public static StreamViewer open(final Identifier identifier, final File sourceFile, final Frame parent,
                                    final ClockTimer clockTimer, final boolean modal) {
        FxPlaybackEngine engine = FxPlaybackEngine.open(sourceFile, OPEN_TIMEOUT_SECONDS);
        boolean opened = false;
        try {
            // Audio has no frames: positions keep to an assumed rate, and frame stepping is off.
            FrameIndex frames = engine.width() > 0
                    ? readFrames(sourceFile, engine.durationMillis())
                    : FrameIndex.assumed(FALLBACK_FRAMES_PER_SECOND, engine.durationMillis());
            StreamViewer viewer = JavaFxToolkit.onEdtAndWait(
                    () -> new JavaFxVideoViewer(identifier, sourceFile, parent, clockTimer, modal, engine, frames));
            opened = true;
            return viewer;
        } catch (ExecutionException e) {
            throw new MediaOpenException("Cannot open " + sourceFile.getName() + ": " + e.getCause().getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaOpenException("Interrupted while opening " + sourceFile.getName(), e);
        } finally {
            if (!opened) {
                // Whatever stopped the window, the player it would have shown goes.
                Platform.runLater(engine::dispose);
            }
        }
    }

    /**
     * Returns whether it is media the player opens, judged by its name in any letter case.
     * @param file a file
     */
    public static boolean isMediaFile(final File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && MEDIA_EXTENSIONS.contains(name.substring(dot + 1));
    }

    static FrameIndex readFrames(final File file, final double durationMillis) {
        try {
            return Mp4FrameTable.read(file);
        } catch (IOException | RuntimeException e) {
            logger.warn("Cannot read frame times from " + file + "; assuming " + FALLBACK_FRAMES_PER_SECOND
                    + " fps and disabling frame stepping", e);
            return FrameIndex.assumed(FALLBACK_FRAMES_PER_SECOND, durationMillis);
        }
    }
}
