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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.models.Identifier;
import org.datavyu.plugins.StreamViewer;

import javax.swing.ImageIcon;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Video playback through OpenJFX Media, the only video player. Accepts only the containers OpenJFX documents for
 * H.264/AAC; QuickTime .mov is rejected by the player and must be remuxed to MP4.
 *
 * <p>Project files identify a track's player by {@link #PLUGIN_UUID}, {@link #NAMESPACE} and
 * {@link #VIEWER_CLASS_NAME}, values from the plugin system this class used to be part of.
 */
public final class JavaFxVideoPlugin {

    private static final Logger logger = LogManager.getLogger(JavaFxVideoPlugin.class);

    /** Player identifier written to project files. */
    public static final UUID PLUGIN_UUID = UUID.nameUUIDFromBytes("plugin.openjfx".getBytes(StandardCharsets.UTF_8));

    /** Kind of media written to project files; any track of this kind opens with this player. */
    public static final String NAMESPACE = "datavyu.video";

    /** Viewer class written to project files. */
    public static final String VIEWER_CLASS_NAME = JavaFxVideoViewer.class.getName();

    /** The files the player opens, for file choosers. */
    public static final FileFilter VIDEO_FILE_FILTER = new FileNameExtensionFilter("Video files", "mp4", "m4v");

    /** Frame rate assumed when a file's frame times cannot be read. */
    private static final double FALLBACK_FRAMES_PER_SECOND = 30;

    private static final long OPEN_TIMEOUT_SECONDS = 30;

    private JavaFxVideoPlugin() {
    }

    /**
     * Opens the file and builds its window. Blocks until the media is ready, so callers
     * must use a background thread; the window itself is built on the Swing EDT.
     *
     * @throws MediaOpenException With a user-facing message if the file cannot be played.
     */
    public static StreamViewer open(final Identifier identifier, final File sourceFile, final Frame parent,
                                    final boolean modal) {
        FxPlaybackEngine engine = FxPlaybackEngine.open(sourceFile, OPEN_TIMEOUT_SECONDS);
        try {
            FrameIndex frames = readFrames(sourceFile, engine.durationMillis());
            return JavaFxToolkit.onEdtAndWait(
                    () -> new JavaFxVideoViewer(identifier, sourceFile, parent, modal, engine, frames));
        } catch (Exception e) {
            JavaFxToolkit.onFx(() -> {
                engine.dispose();
                return null;
            });
            throw e instanceof MediaOpenException ? (MediaOpenException) e
                    : new MediaOpenException("Cannot open " + sourceFile.getName() + ": " + e.getMessage(), e);
        }
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

    /**
     * @return The icon shown in a video's track header.
     */
    public static ImageIcon trackIcon() {
        return new ImageIcon(JavaFxVideoPlugin.class.getResource("/icons/video-track.png"));
    }
}
