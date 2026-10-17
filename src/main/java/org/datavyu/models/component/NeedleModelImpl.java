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
package org.datavyu.models.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.beans.PropertyChangeEvent;

/**
 * This model provides information used to render a timing needle on the tracks interface.
 */
public final class NeedleModelImpl extends MixerComponentModelImpl implements NeedleModel {

    /** The logger for this class */
    private static Logger logger = LogManager.getLogger(NeedleModelImpl.class);

    /** Current time represented by the needle */
    private long currentTime;

    /** Color of the needle line to be drawn on screen */
    private Color needleColor = new Color(250, 0, 0, 100);

    /** Height of the transition area between the timescale and zoom indicator area (pixels) */
    private int timescaleTransitionHeight = 0;

    /** Height of the zoom indicator area below the timescale transition area (pixels) */
    private int zoomIndicatorHeight = 0;

    NeedleModelImpl(final MixerModel mixerModel) {
        super(mixerModel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void wireListeners() {
        mixerModel.getViewportModel().addPropertyChangeListener(this);
    }

    @Override
    public synchronized NeedleState getNeedle() {
        return new NeedleStateImpl(currentTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized long getCurrentTime() {
        return currentTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentTime(final long time) {
        //logger.info("The current time is " + time + " msec.");
        final long oldTime;
        final long newTime;

        synchronized (this) {
            oldTime = currentTime;
            newTime = calculateTimelineBoundedTime(time);
            if (oldTime == newTime) {
                return;
            }
            this.currentTime = newTime;
        }

        //logger.info("Change time from " + oldTime + " to " + newTime);
        firePropertyChange(NeedleModel.NAME, oldTime, newTime);
    }

    /**
     * @return color of the needle line
     */
    public synchronized Color getNeedleColor() {
        return needleColor;
    }

    /**
     * @return height of the transition area between the timescale and zoom indicator window (pixels)
     */
    public synchronized int getTimescaleTransitionHeight() {
        return timescaleTransitionHeight;
    }

    /**
     * Sets the height between the transition area and the zoom indicator window.
     *
     * @param newHeight new height in pixels
     */
    public synchronized void setTimescaleTransitionHeight(int newHeight) {
        timescaleTransitionHeight = newHeight;
    }

    /**
     * @return height of the zoom indicator area below the timescale (pixels)
     */
    public synchronized int getZoomIndicatorHeight() {
        return zoomIndicatorHeight;
    }

    /**
     * Sets the height of the zoom indicator area below the timescale.
     *
     * @param newHeight new height in pixels
     */
    public synchronized void setZoomIndicatorHeight(int newHeight) {
        zoomIndicatorHeight = newHeight;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() == mixerModel.getViewportModel()) {
            // keep the needle within the timeline
            setCurrentTime(getCurrentTime());
        }
    }

    private long calculateTimelineBoundedTime(final long time) {
        return Math.min(Math.max(time, 0), mixerModel.getViewportModel().getViewport().getMaxEnd());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        // Each getter takes one model's lock at a time, so two models compared from two threads cannot deadlock.

        if (this == obj)
            return true;

        if (obj == null)
            return false;

        if (getClass() != obj.getClass())
            return false;

        NeedleModelImpl other = (NeedleModelImpl) obj;

        if (getCurrentTime() != other.getCurrentTime())
            return false;

        if (!getNeedleColor().equals(other.getNeedleColor()))
            return false;

        if (getTimescaleTransitionHeight() != other.getTimescaleTransitionHeight())
            return false;

        if (getZoomIndicatorHeight() != other.getZoomIndicatorHeight())
            return false;

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + (int) (currentTime ^ (currentTime >>> 32));
        result = (prime * result) + needleColor.hashCode();
        result = (prime * result) + timescaleTransitionHeight;
        result = (prime * result) + zoomIndicatorHeight;

        return result;
    }
}
