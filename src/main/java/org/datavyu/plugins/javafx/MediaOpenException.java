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

import javafx.scene.media.MediaException;

import java.io.File;

/**
 * A video could not be opened. The message is written for the person who chose the file.
 */
public final class MediaOpenException extends RuntimeException {

    static final String SUPPORTED_FORMAT = "Watavyu plays MP4 files (.mp4 or .m4v) with H.264 video and AAC audio.";

    static final String REMUX_HINT = "To convert an OBS .mov without re-encoding, run:\n"
            + "ffmpeg -i input.mov -c copy -movflags +faststart output.mp4";

    MediaOpenException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /** @return An exception describing why the player rejected the file. */
    static MediaOpenException from(final File file, final Throwable error) {
        if (error instanceof MediaException) {
            MediaException media = (MediaException) error;
            return forMediaError(file.getName(), media.getType(), media.getMessage(), error);
        }
        return new MediaOpenException("Cannot open " + file.getName() + ": " + error.getMessage(), error);
    }

    static MediaOpenException forMediaError(final String name, final MediaException.Type type, final String detail,
                                            final Throwable cause) {
        switch (type) {
            case MEDIA_UNSUPPORTED:
            case UNKNOWN:
                String lower = name.toLowerCase();
                if (!lower.endsWith(".mp4") && !lower.endsWith(".m4v")) {
                    return new MediaOpenException("Cannot open " + name + ": this file type is not supported.\n\n"
                            + SUPPORTED_FORMAT + "\n" + REMUX_HINT, cause);
                }
                return new MediaOpenException("Cannot open " + name + ": the video or audio codec is not supported.\n\n"
                        + SUPPORTED_FORMAT, cause);
            case MEDIA_CORRUPTED:
            case MEDIA_INACCESSIBLE:
            case MEDIA_UNAVAILABLE:
                return new MediaOpenException("Cannot open " + name + ": the file could not be read (" + detail + ").",
                        cause);
            default:
                return new MediaOpenException("Cannot open " + name + ": " + detail, cause);
        }
    }

    static MediaOpenException timedOut(final File file, final long seconds) {
        return new MediaOpenException("Cannot open " + file.getName() + ": the player did not become ready within "
                + seconds + " seconds.", null);
    }
}
