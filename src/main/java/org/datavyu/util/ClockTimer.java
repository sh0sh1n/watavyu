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
package org.datavyu.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;


/**
 * Keeps multiple streams in periodic sync and does not play beyond the boundaries of a stream.
 */
public final class ClockTimer {

    /** The logger for this class. */
    private static Logger logger = LogManager.getLogger(ClockTimer.class);

    /** Synchronization threshold in milliseconds */
    public static final long SYNC_THRESHOLD = 1500L; // 1.5 sec  (because some plugins are not very precise in seek)

    /** Clock tick period in milliseconds */
    private static final long CLOCK_INTERVAL = 100L;

    /** Clock initial delay in milliseconds */
    private static final long CLOCK_DELAY = 0L;

    /** Convert nanoseconds to milliseconds */
    private static final long NANO_IN_MILLI = 1000000L;

    /** Minimum time for the clock in milliseconds */
    private long minTime;

    /** Maximum time for the clock in milliseconds */
    private long maxTime;

    /** Current time of the clock in milliseconds */
    private double clockTime;

    /** Last time in nanoseconds; it is used to calculate the elapsed */
    private double lastTime;

    /** Is the clock stopped */
    private volatile boolean isStopped;

    /** The rate factor for the clock updates */
    private float rate = 1F;

    private ScheduledExecutorService execService
        =   Executors.newScheduledThreadPool(4);

    /** Listeners of this clock */
    private final Set<ClockListener> clockListeners = new CopyOnWriteArraySet<>();

    /**
     * Default constructor.
     */
    public ClockTimer() {

        // Initialize values
        clockTime = 0;
        lastTime = 0;
        minTime = 0;
        maxTime = 0;
        isStopped = true;

        execService.scheduleAtFixedRate(() -> periodicSync()
            , CLOCK_DELAY, CLOCK_INTERVAL, TimeUnit.MILLISECONDS);
        execService.scheduleAtFixedRate(() -> checkClockBoundary()
            , CLOCK_DELAY, CLOCK_INTERVAL, TimeUnit.MILLISECONDS);
        execService.scheduleAtFixedRate(() -> checkStreamsBoundary()
            , CLOCK_DELAY, CLOCK_INTERVAL, TimeUnit.MILLISECONDS);
        execService.scheduleAtFixedRate(() -> notifySeekPlayback()
            , CLOCK_DELAY, CLOCK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * Sets the minimum stream time
     *
     * @param minTime The minimum stream time
     */
    public synchronized void setMinTime(long minTime) {
        logger.debug("Setting Clock minimum time");
        this.minTime = minTime;
    }

    /**
     * Sets the maximum stream time
     *
     * @param maxTime The maximum stream time
     */
    public synchronized void setMaxTime(long maxTime) {
        logger.debug("Setting Clock maximum time");
        this.maxTime = maxTime;
    }

    /**
     * Get the clock time into the range for this clock timer
     *
     * @param clockTime Clock time
     *
     * @return Clock time in range for this clock timer
     */
    public synchronized long toRange(long clockTime) {
        return Math.min(Math.max(clockTime, minTime), maxTime);
    }


    /**
     * Get the current stream time
     *
     * @return Current stream time
     */
    public synchronized double getStreamTime() {
        return (long) clockTime + minTime;
    }

    /**
     * Get the current Clock time, within the tolerance of the periodic sync.
     *
     * @return Current clock time
     */
    public synchronized double getClockTime() {
        return clockTime;
    }

    /**
     * @return Current clock rate.
     */
    public synchronized float getRate() {
        return rate;
    }

    /**
     * Set the time but don't activate any of the listeners
     *
     * All listeners will be updated through the auto sync to the new time
     *
     * Use this method if eventual synchronization is enough
     *
     * @param time The new time
     */
    public synchronized void setTime(long time) {
        if (minTime <= time && time <= maxTime) {
            logger.debug("Setting Clock time to: " + time);
            clockTime = time;
            // Don't notify a sync or force a sync
            // The time will be updated by a periodic sync
        }
    }

    /**
     * Set the time and force an update of the clock time to all listeners
     *
     * Use this method if immediate synchronization is desired
     *
     * @param time The new time
     */
    public synchronized void setForceTime(long time) {
        if (minTime <= time && time <= maxTime) {
            clockTime = time;
            // Notify a force sync
            notifyForceSync();
        }else if (time < minTime){
            clockTime = minTime;
            // Notify a force sync
            notifyForceSync();
        }
    }

  /**
   * Sets the update rate for the clock.
   *
   * <p>IMPORTANT: Setting the rate to 0 will stop the clock and fires a
   * notify stop event.
   *
   * @param newRate New update rate
   */
  public synchronized void setRate(float newRate) {
        logger.debug("Setting Clock Rate to " + newRate + "X");
        updateElapsedTime();
        rate = newRate;
        // FIRST notify about the rate change
        notifyRate();
        // SECOND start or stop
        if (Math.abs(rate) < Math.ulp(1f)) {
            stop();
        } else {
            start();
        }
    }

    /**
     * If the clock is not running, then this starts the clock and fires a notify
     * start event with the current clock time
     */
    public synchronized void start() {
        if (isStopped) {
            logger.debug("Starting Clock");
            isStopped = false;
            lastTime = System.nanoTime();
            notifyStart();
        }
    }

    private synchronized void stop() {
        if (!isStopped) {
            logger.debug("Stopping Clock");
            updateElapsedTime();
            isStopped = true;
            notifyStop();
            // Force sync after a stop
            notifyForceSync();
        }
    }

    /**
     * If the clock is not stopped, then this pause the clock and fires a notify
     * pause event with the current clock time
     *
     * WARNING: If you want to influence the rate as well you need to set the rate to 0
     * instead of calling stop directly!
     */
    public synchronized void pause() {
        if (!isStopped) {
            logger.debug("Pausing Clock");
            updateElapsedTime();
            isStopped = true;
            notifyPause();
            // Force sync after a stop
            notifyForceSync();
        }
    }

    /**
     * @return True if clock is Paused.
     */
    public boolean isPaused() {
        return isStopped;
    }

    /**
     * @return True if clock is stopped.
     */
    public boolean isStopped() {
        return getRate() == 0f;
    }

    /**
     * Registers a clock listener
     *
     * @param listener Listener requiring clockTick updates
     */
    public synchronized void registerListener(final ClockListener listener) {
        clockListeners.add(listener);
    }

  /**
   * Un-Registers a clock listener
   *
   * @param listener Listener requiring clockTick updates
   */
    public synchronized void unRegisterListener(final ClockListener listener) {
        clockListeners.remove(listener);
    }

    /**
     * Update the clock time with the elapsed time since the last update
     */
    private synchronized void updateElapsedTime() {
        double newTime = System.nanoTime();
        clockTime = isStopped ? clockTime : Math.min(Math.max(
                clockTime + rate * (newTime - lastTime) / NANO_IN_MILLI, minTime), maxTime);
        lastTime = newTime;
    }

    /**
     * The "periodicSync" of the clock - updates listeners of changes in time.
     */
    private synchronized void periodicSync() {
        updateElapsedTime();
        notifyPeriodicSync();
    }

    private synchronized void checkClockBoundary() {
        updateElapsedTime();
        notifyCheckClockBoundary();
    }

    private synchronized void checkStreamsBoundary() {
        updateElapsedTime();
        notifyCheckStreamsBoundary();
    }

    // Notify seek playback could be used if a different interval is need for the
    // For the fake playback scheduler
    private void notifySeekPlayback() {
        // Runs on its own pool thread without the clock's lock, so it reads the time under the lock first.
        double time;
        synchronized (this) {
            time = clockTime;
        }
        for (ClockListener clockListener : clockListeners) {
            clockListener.clockSeekPlayback(time);
        }
    }

    private void notifyCheckClockBoundary() {
        for (ClockListener clockListener : clockListeners) {
            clockListener.clockBoundaryCheck(clockTime);
        }
    }

    private void notifyCheckStreamsBoundary() {
        for (ClockListener clockListener : clockListeners) {
            clockListener.streamsBoundaryCheck(clockTime);
        }
    }

    /**
     * Notify clock listeners of a force periodicSync -- consumers must act on this
     */
    private void notifyForceSync() {
        for (ClockListener clockListener : clockListeners) {
            clockListener.clockForceSync(clockTime);
        }
    }

    /**
     * Notify clock listeners of a periodic periodicSync -- consumers may act on this
     */
    private void notifyPeriodicSync() {
        for (ClockListener clockListener : clockListeners) {
            clockListener.clockPeriodicSync(clockTime);
        }
    }

    /**
     * Notify clock listeners of rate update.
     */
    private void notifyRate() {
        for (ClockListener clockListener : clockListeners) {
            clockListener.clockRate(rate);
        }
    }

    /**
     * Notify clock listeners of start event.
     */
    private void notifyStart() {
        for (ClockListener clockListener : clockListeners) {
            clockListener.clockStart(clockTime);
        }
    }

    /**
     * Notify clock listeners of stop event.
     */
    private void notifyStop() {
        for (ClockListener clockListener : clockListeners) {
            clockListener.clockStop(clockTime);
        }
    }

    /**
     * Notify clock listeners of pause event.
     */
    private void notifyPause() {
        for (ClockListener clockListener : clockListeners) {
            clockListener.clockPause(clockTime);
        }
    }
    /**
     * Listener interface for clock 'ticks'.
     */
    public interface ClockListener {
        /**
         * @param clockTime Current time in milliseconds
         */
        void clockSeekPlayback(double clockTime);

        /**
         * @param clockTime Current time in milliseconds
         */
        void clockBoundaryCheck(double clockTime);

        /**
         * @param clockTime Current time in milliseconds
         */
        void streamsBoundaryCheck(double clockTime);

        /**
         * @param clockTime Current time in milliseconds
         */
        void clockForceSync(double clockTime);

        /**
         * @param clockTime Current time in milliseconds
         */
        void clockPeriodicSync(double clockTime);

        /**
         * @param clockTime Current time in milliseconds
         */
        void clockStart(double clockTime);

        /**
         * @param clockTime Current time in milliseconds
         */
        void clockStop(double clockTime);

        /**
         * @param clockTime Current time in milliseconds
         */
        void clockPause(double clockTime);

        /**
         * @param rate Current (updated) rate.
         */
        void clockRate(float rate);
    }
}
