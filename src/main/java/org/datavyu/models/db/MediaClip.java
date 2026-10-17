package org.datavyu.models.db;

import java.nio.file.Path;

/**
 * What a media column's cell plays: a media file and what is known about it. The details are what the file said when it
 * last opened, or what the project file recorded while it is missing; when the file opens, what it says wins.
 *
 * @param file      the media file, as an absolute path
 * @param duration  its length in milliseconds, 0 while unknown
 * @param frameRate frames per second, 0 while unknown
 * @param width     frame width in pixels, 0 while unknown
 * @param height    frame height in pixels, 0 while unknown
 * @param volume    the clip's volume, from 0 to 1
 */
public record MediaClip(String file, long duration, double frameRate, int width, int height, double volume) {

    /** The volume of a clip added in the app. */
    public static final double DEFAULT_VOLUME = 1.0;

    /**
     * A clip whose file has not opened yet.
     *
     * @param file the media file, as an absolute path
     * @return the clip, with nothing known about the file
     */
    public static MediaClip of(final String file) {
        return new MediaClip(file, 0, 0, 0, 0, DEFAULT_VOLUME);
    }

    /**
     * @param newVolume the clip's volume, from 0 to 1
     * @return this clip at that volume
     */
    public MediaClip withVolume(final double newVolume) {
        return new MediaClip(file, duration, frameRate, width, height, newVolume);
    }

    /**
     * @return this clip with what its opened file says
     */
    public MediaClip withDetails(final long newDuration, final double newFrameRate, final int newWidth,
                                 final int newHeight) {
        return new MediaClip(file, newDuration, newFrameRate, newWidth, newHeight, volume);
    }

    /** @return the file's name, without its folder */
    public String fileName() {
        Path name = Path.of(file).getFileName();
        return name == null ? file : name.toString();
    }
}
