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

import com.google.common.collect.Lists;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.Datavyu;
import org.datavyu.models.Identifier;
import org.datavyu.plugins.Filter;
import org.datavyu.plugins.FilterNames;
import org.datavyu.plugins.Plugin;
import org.datavyu.plugins.StreamViewer;

import javax.swing.ImageIcon;
import java.awt.Frame;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Video playback through OpenJFX Media. Accepts only the containers OpenJFX documents for
 * H.264/AAC; QuickTime .mov is rejected by the player and must be remuxed to MP4.
 */
public final class JavaFxVideoPlugin implements Plugin {

    private static final Logger logger = LogManager.getLogger(JavaFxVideoPlugin.class);

    private static final UUID PLUGIN_UUID = UUID.nameUUIDFromBytes("plugin.openjfx".getBytes());

    /** Frame rate assumed when a file's frame times cannot be read. */
    private static final double FALLBACK_FRAMES_PER_SECOND = 30;

    private static final long OPEN_TIMEOUT_SECONDS = 30;

    private static final Filter VIDEO_FILTER = new Filter() {
        final List<String> extensions = Lists.newArrayList(".mp4", ".m4v");
        final SuffixFileFilter fileFilter = new SuffixFileFilter(extensions, IOCase.INSENSITIVE);

        @Override
        public FileFilter getFileFilter() {
            return fileFilter;
        }

        @Override
        public String getName() {
            return FilterNames.VIDEO.getFilterName();
        }

        @Override
        public Iterable<String> getExtensions() {
            return extensions;
        }
    };

    /**
     * Opens the file and builds its window. Blocks until the media is ready, so callers
     * must use a background thread; the window itself is built on the Swing EDT.
     *
     * @throws MediaOpenException With a user-facing message if the file cannot be played.
     */
    @Override
    public StreamViewer getNewStreamViewer(final Identifier identifier, final File sourceFile, final Frame parent,
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

    @Override
    public Class<? extends StreamViewer> getViewerClass() {
        return JavaFxVideoViewer.class;
    }

    @Override
    public ImageIcon getTypeIcon() {
        return new ImageIcon(getClass().getResource("/icons/gstreamerplugin-icon.png"));
    }

    @Override
    public String getPluginName() {
        return "OpenJFX Video";
    }

    @Override
    public UUID getPluginUUID() {
        return PLUGIN_UUID;
    }

    @Override
    public String getNamespace() {
        return "datavyu.video";
    }

    @Override
    public Filter[] getFilters() {
        return new Filter[]{VIDEO_FILTER};
    }

    @Override
    public List<Datavyu.Platform> getValidPlatforms() {
        return Lists.newArrayList(Datavyu.Platform.MAC);
    }
}
