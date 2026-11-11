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
package com.sh0sh1n.watavyu.playback;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.sh0sh1n.watavyu.models.Identifier;
import com.sh0sh1n.watavyu.util.ClockTimer;
import com.sh0sh1n.watavyu.util.ClockTimer.ClockListener;
import com.sh0sh1n.watavyu.views.WindowPlacement;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.function.Consumer;

/**
 * What all media players have in common: a clip's place on the timeline (its offset), its volume, and following the
 * clock. A clip plays from its offset for its duration, and only while it is in play in its media column.
 */
public abstract class StreamViewerDialog extends JDialog implements StreamViewer,
    ClockListener {

    /** Logger for this class */
    private static Logger logger = LogManager.getLogger(StreamViewerDialog.class);

    /** Keeps the clock's calls to this clip one at a time. */
    private final Object lock = new Object();

    /** The clip's volume, from 0 to 1 */
    protected volatile float volume = 1f;

    /** Controls visibility */
    protected volatile boolean isVisible = true;

    /** Whether this clip is the one its media column plays at the clock's time. */
    private volatile boolean inPlay = true;

    /** Shows or hides the window the video is shown in, when that is not this dialog. */
    private volatile Consumer<Boolean> windowListener;

    /** The playback speed */
    protected volatile float playBackRate = 0;

    /** Frames per second */
    private volatile float framesPerSecond = -1;

    /** The current video file */
    protected File sourceFile;

    /** Identifier of this data viewer */
    private Identifier identifier;

    /** Where the clip starts on the timeline, in milliseconds. */
    private volatile long offset;

    /** The clock the player follows: its spreadsheet's */
    protected final ClockTimer clockTimer;

    /**
     * Constructs a base data video viewer.
     *
     * @param clockTimer the clock the player follows
     */
    public StreamViewerDialog(Identifier identifier, final Frame parent, final ClockTimer clockTimer,
                              final boolean modal) {
        super(parent, modal);
        this.identifier = identifier;
        this.clockTimer = clockTimer;
        offset = 0;
    }

    /**
     * Sets the player's volume to the clip's, or silences it while hidden or out of play.
     */
    protected void setVolume() {
        applyVolume();
    }

    /** The volume last given to the player, to log changes. */
    private volatile float appliedVolume = -1;

    private void applyVolume() {
        float effective = isVisible && inPlay ? volume : 0F;
        if (effective != appliedVolume) {
            appliedVolume = effective;
            logger.info("Volume of " + identifier + " (" + (sourceFile == null ? "" : sourceFile.getName()) + ") is "
                + effective + (isVisible ? "" : ", hidden") + (inPlay ? "" : ", out of play"));
        }
        setPlayerVolume(effective);
    }

    protected abstract void setPlayerVolume(float volume);

    protected abstract Dimension getOriginalVideoSize();

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract void setCurrentTime(final long time);

    protected abstract float getPlayerFramesPerSecond();

    /**
     * @return The duration of the movie in milliseconds. If -1 is returned, the
     * movie's duration cannot be determined.
     */
    @Override
    public abstract long getDuration();

    /**
     * @return The playback startTime of the movie in milliseconds.
     */
    @Override
    public long getOffset() {
        return offset;
    }

    /**
     * @param newOffset The playback startTime of the movie in milliseconds.
     */
    @Override
    public void setOffset(final long newOffset) {
        logger.info("Set offset of stream viewer with " + identifier + " to " + newOffset);
        offset = newOffset;
    }

    /**
     * @return The file used to display this data feed.
     */
    @Override
    public File getSourceFile() {
        return sourceFile;
    }

    protected void setSourceFile(final File sourceFile) {
        this.sourceFile = sourceFile;
        setTitle(sourceFile.getName());
        setName(getClass().getSimpleName() + "-" + sourceFile.getName());
        pack();
        invalidate();

        // Each video's window comes back where the user last left a window showing a file of the same name. The
        // window is shown by whoever shows the video: in the app, its media column's window shows it instead.
        WindowPlacement.restoreAndTrack(this, getName(), true);
        isVisible = true;

        if (framesPerSecond == -1) {
            framesPerSecond = getPlayerFramesPerSecond();
        }

        logger.info("Frames per second: " + framesPerSecond);
    }

    /**
     * @return The frames per second.
     */
    @Override
    public float getFramesPerSecond() {
        return framesPerSecond;
    }

    @Override
    public float getRate() {
        return playBackRate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRate(final float speed) {
        playBackRate = speed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract long getCurrentTime();

    @Override
    public float getVolumeLevel() {
        return volume;
    }

    @Override
    public void setVolumeLevel(final float level) {
        volume = level;
        setVolume();
    }

    @Override
    public boolean isViewerVisible() {
        return isVisible;
    }

    @Override
    public Dimension getVideoSize() {
        return getOriginalVideoSize();
    }

    /**
     * Hides the window when it is closed, stopping and silencing the clip. A subclass calls this at the start of its
     * constructor; this class's constructor does not, so that no overridable method runs before the subclass is built.
     */
    protected final void initComponents() {
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent evt) {
                stop();
                isVisible = false;
                setVolume();
            }
        });
        pack();
    }

    protected abstract void cleanUp();

    @Override
    public Identifier getIdentifier() { return identifier; }

    @Override
    public void close() {
        stop();
        cleanUp();
        dispose();
    }

    @Override
    public void setViewerVisible(final boolean isVisible) {
        Consumer<Boolean> listener = windowListener;
        if (listener == null) {
            setVisible(isVisible);
        } else {
            listener.accept(isVisible);
        }
        this.isVisible = isVisible;
        setVolume();
    }

    @Override
    public void setWindowListener(final Consumer<Boolean> showWindow) {
        windowListener = showWindow;
        setVisible(false);
    }

    @Override
    public void setInPlay(final boolean newInPlay) {
        synchronized (lock) {
            if (newInPlay != inPlay) {
                inPlay = newInPlay;
                applyVolume();
                if (!inPlay && isPlaying()) {
                    pause();
                } else if (inPlay && !isPlaying() && !clockTimer.isPaused() && getCurrentTime() != -1
                        && holds(clockTimer.getClockTime())) {
                    // Put in play while the clock runs through the clip, as when the clock enters it: start now,
                    // rather than at the next tick, whichever the clock tells first.
                    logger.info("In play, starting " + getIdentifier() + " at clock " + clockTimer.getClockTime());
                    start();
                }
            }
        }
    }

    @Override
    public boolean isStepEnabled() { return false; }

    @Override
    public void stepForward() {
        // Nothing to do here
    }

    @Override
    public void stepBackward() {
        // Nothing to do here
    }

    /** @return the clip's own time at a clock time, kept within the clip */
    private long clipTime(final double clockTime) {
        return (long) Math.min(Math.max(clockTime - (double) offset, 0), (double) getDuration());
    }

    /** @return whether a clock time falls within the clip */
    private boolean holds(final double clockTime) {
        return clockTime >= offset && clockTime < offset + getDuration();
    }

    /**
     * Starts the clip when the running clock enters it while it is in play, pauses it when the clock leaves it or it
     * goes out of play, and moves it to the clock's time if it has drifted by the sync threshold or more.
     */
    @Override
    public void clockTick(final double clockTime) {
        synchronized (lock) {
            // The clock logs a failure here and goes on.
            if (getCurrentTime() == -1) {
                return;
            }
            if (!clockTimer.isPaused()) {
                if (inPlay && holds(clockTime) && !isPlaying()) {
                    logger.info("Stream Boundary Starting " + getIdentifier() + " at clock " + clockTime);
                    start();
                }
                if ((!inPlay || !holds(clockTime)) && isPlaying()) {
                    logger.info("Stream Boundary Stopping " + getIdentifier() + " at clock " + clockTime);
                    pause();
                }
            }
            long clipTime = clipTime(clockTime);
            if (Math.abs(clipTime - getCurrentTime()) >= ClockTimer.SYNC_THRESHOLD) {
                logger.info("Sync stream " + getIdentifier() + " to " + clipTime + " milliseconds; it was at "
                    + getCurrentTime());
                setCurrentTime(clipTime);
            }
        }
    }

    @Override
    public void clockForceSync(double clockTime) {
        synchronized (lock) {
            long clipTime = clipTime(clockTime);
            // Force a sync only when the time is different
            if (clipTime != getCurrentTime()) {
                logger.info("Forced sync stream " + getIdentifier() + " to " + clipTime + " milliseconds");
                setCurrentTime(clipTime);
            }
        }
    }

    @Override
    public void clockStart(final double clockTime) {
        synchronized (lock) {
            if (inPlay && holds(clockTime)) {
                logger.info("Clock Start Starts " + getIdentifier() + " at clock " + clockTime);
                start();
            }
        }
    }

    @Override
    public void clockStop(double clockTime) {
        synchronized (lock) {
            logger.debug("Clock stop Stops track: " + getIdentifier() + " at time: " + clockTime);
            stop();
        }
    }

    @Override
    public void clockPause(double clockTime) {
        synchronized (lock) {
            logger.debug("Clock pause Pauses track: " + getIdentifier() + " at time: " + clockTime);
            pause();
        }
    }

    @Override
    public void clockRate(final float rate) {
        synchronized (lock) {
            logger.debug("Clock setting rate of track: " + getIdentifier() + " to " + rate + "X");
            setRate(rate);
        }
    }
}
