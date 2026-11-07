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

/**
 * The media player operations {@link JavaFxPlayback} needs. Commands are only issued
 * on the player's own thread (the JavaFX Application Thread for {@link FxPlaybackEngine});
 * {@link #currentTimeMillis()} may be read from any thread.
 */
interface PlaybackEngine {

    /** Callbacks from the engine, delivered on the player's thread. */
    interface Listener {
        void endOfMedia();

        void error(Throwable error);
    }

    void setListener(Listener listener);

    void play();

    void pause();

    /** Seeks exactly to the given stream time. */
    void seek(double millis);

    /**
     * Sets the playback rate.
     *
     * @param rate A forward rate within the engine's supported range.
     */
    void setRate(double rate);

    /**
     * Sets the volume.
     *
     * @param volume Linear volume from 0 to 1.
     */
    void setVolume(double volume);

    /** Returns the last presentation time the engine reported. */
    double currentTimeMillis();

    void dispose();
}
