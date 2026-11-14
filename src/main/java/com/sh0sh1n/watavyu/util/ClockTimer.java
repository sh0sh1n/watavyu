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
package com.sh0sh1n.watavyu.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.ObjDoubleConsumer;


/**
 * Keeps multiple streams in sync, and pauses where playback ends: at the end of playback it is given (the end of the
 * media), or else at the end of its range. It plays forward only.
 *
 * <p>The clock has one thread of its own, the clock thread. It ticks there, and tells its listeners of every tick and
 * every change there too, in the order the clock changed, and never while holding the clock's lock: a change made on
 * another thread, such as a rate set on the Swing event thread, is told to the listeners on the clock thread soon
 * after. So a listener may call the clock and may wait on another thread without deadlocking it, and one listener is
 * never called on two threads at once. Listeners hand Swing work to the event thread themselves.
 *
 * <p>Each call carries the clock's time as it is when the call is made, not when the change was: a listener told of a
 * jump made before a step follows the step too. Each tick, the clock first pauses at any end it has run into, judged
 * under its lock with the time then, so a jump made since is not undone and no listener hears a time past the end.
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

    /** Guards the clock's times and rate, which its ticks and its callers' threads share. */
    private final Object lock = new Object();

    /** Minimum time for the clock in milliseconds */
    private long minTime;

    /** Maximum time for the clock in milliseconds */
    private long maxTime;

    /** Current time of the clock in milliseconds */
    private double clockTime;

    /** Last time in nanoseconds; it is used to calculate the elapsed */
    private double lastTime;

    /** Whether the clock is paused. */
    private volatile boolean paused;

    /** The rate the clock runs at, or resumes at while paused; never 0. */
    private float rate = 1F;

    /** Where playback going forward ends, asked on each tick, or negative to run to the end of the range. */
    private volatile LongSupplier playbackEnd = () -> -1;

    /** How long closing waits for a tick already running to finish. */
    private static final long CLOSE_WAIT_MILLIS = 500;

    /** The clock thread, which ticks and tells the listeners of each tick and change in turn. */
    private final ScheduledExecutorService clockThread = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "clock");
        thread.setDaemon(true);
        return thread;
    });

    /** Listeners of this clock */
    private final Set<ClockListener> clockListeners = new CopyOnWriteArraySet<>();

    /** The repeating tick, which closing cancels */
    private final ScheduledFuture<?> ticking;

    /**
     * Default constructor.
     */
    public ClockTimer() {

        // Initialize values
        clockTime = 0;
        lastTime = 0;
        minTime = 0;
        maxTime = 0;
        paused = true;

        // A fixed delay, not a fixed rate: ticks missed while a listener was slow are not run back to back, ahead of
        // the changes made meanwhile.
        ticking = clockThread.scheduleWithFixedDelay(this::tick, CLOCK_DELAY, CLOCK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * Sets the minimum stream time
     *
     * @param minTime The minimum stream time
     */
    public void setMinTime(long minTime) {
        synchronized (lock) {
            logger.debug("Setting Clock minimum time");
            this.minTime = minTime;
        }
    }

    /**
     * Sets the maximum stream time
     *
     * @param maxTime The maximum stream time
     */
    public void setMaxTime(long maxTime) {
        synchronized (lock) {
            logger.debug("Setting Clock maximum time");
            this.maxTime = maxTime;
        }
    }

    /** Returns the end of the clock's range, in milliseconds. */
    public long getMaxTime() {
        synchronized (lock) {
            return maxTime;
        }
    }

    /**
     * Get the clock time into the range for this clock timer
     *
     * @param clockTime Clock time
     *
     * @return Clock time in range for this clock timer
     */
    public long toRange(long clockTime) {
        synchronized (lock) {
            return Math.min(Math.max(clockTime, minTime), maxTime);
        }
    }


    /**
     * Get the current stream time
     *
     * @return Current stream time
     */
    public double getStreamTime() {
        synchronized (lock) {
            return (long) clockTime + minTime;
        }
    }

    /**
     * Get the current Clock time.
     *
     * @return Current clock time
     */
    public double getClockTime() {
        synchronized (lock) {
            return clockTime;
        }
    }

    /**
     * Returns current clock rate.
     */
    public float getRate() {
        synchronized (lock) {
            return rate;
        }
    }

    /**
     * Set the time but don't activate any of the listeners
     *
     * All listeners follow the new time at the next tick
     *
     * Use this method if eventual synchronization is enough
     *
     * @param time The new time
     */
    public void setTime(long time) {
        synchronized (lock) {
            if (minTime <= time && time <= maxTime) {
                logger.debug("Setting Clock time to: " + time);
                clockTime = time;
                // Don't notify a sync or force a sync
                // The listeners follow at the next tick
            }
        }
    }

    /**
     * Set the time and force an update of the clock time to all listeners
     *
     * Use this method if immediate synchronization is desired
     *
     * @param time The new time
     */
    public void setForceTime(long time) {
        synchronized (lock) {
            if (minTime <= time && time <= maxTime) {
                clockTime = time;
                notifyForceSync();
            } else if (time < minTime) {
                clockTime = minTime;
                notifyForceSync();
            }
        }
    }

    /**
     * Sets the rate: a running clock carries on at it, and a paused one stays paused until started. The clock plays
     * forward only, so the rate is never 0 or negative; a clock that should not move is paused, and keeps the rate it
     * resumes at.
     *
     * @param newRate the rate, above 0
     * @throws IllegalArgumentException if the rate is 0 or negative
     */
    public void setRate(final float newRate) {
        if (!(newRate > 0)) {
            throw new IllegalArgumentException("The clock plays forward only, not at " + newRate + "x; pause it to stop");
        }
        synchronized (lock) {
            logger.debug("Setting Clock Rate to " + newRate + "X");
            updateElapsedTime();
            rate = newRate;
            notifyRate();
        }
    }

    /**
     * If the clock is not running, then this starts the clock and fires a notify
     * start event with the current clock time
     */
    public void start() {
        synchronized (lock) {
            if (paused) {
                logger.debug("Starting Clock");
                paused = false;
                lastTime = System.nanoTime();
                notifyStart();
            }
        }
    }

    /**
     * If the clock is running, pauses it where it is and tells the listeners; it keeps its rate, which starting it
     * again resumes at.
     */
    public void pause() {
        synchronized (lock) {
            if (!paused) {
                updateElapsedTime();
                pauseAt(clockTime);
            }
        }
    }

    /** Pauses the running clock at a time, and tells the listeners once. Called under the lock. */
    private void pauseAt(final double time) {
        logger.debug("Pausing Clock at " + time);
        paused = true;
        clockTime = time;
        notifyPause();
        // Force sync after a pause
        notifyForceSync();
    }

    /**
     * Sets where playback going forward ends before the end of the clock's range, asked on each tick from the clock
     * thread: the clock pauses there and moves to it.
     *
     * @param end returns where playback ends, or a negative time to run to the end of the range
     */
    public void setPlaybackEnd(final LongSupplier end) {
        playbackEnd = end;
    }

    /**
     * Pauses the clock if it has run into an end of playback, judged with its time now. Called under the lock.
     *
     * @param end where playback going forward ends, or negative for the end of the range
     */
    private void pauseAtEnd(final long end) {
        if (paused) {
            return;
        }
        if (end >= 0 && clockTime >= end) {
            logger.info("Pausing at the end of the media, " + end);
            pauseAt((double) end);
        } else if (clockTime >= maxTime) {
            logger.info("Pausing at the end of the timeline, " + clockTime);
            pauseAt(clockTime);
        }
    }

    /**
     * The clock's time, rate and whether it is paused, read together.
     *
     * @param time   the time, within the clock's range, in milliseconds
     * @param rate   the rate
     * @param paused whether the clock is paused
     */
    public record Reading(long time, float rate, boolean paused) {
    }

    /** Returns the clock's time, rate and whether it is paused, read at one moment. */
    public Reading read() {
        synchronized (lock) {
            return new Reading(toRange((long) clockTime), rate, paused);
        }
    }

    /** Returns whether the clock is paused, as a new clock is. */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Registers a clock listener
     *
     * @param listener Listener requiring clockTick updates
     */
    public void registerListener(final ClockListener listener) {
        synchronized (lock) {
            if (!isClosed()) {
                clockListeners.add(listener);
            }
        }
    }

    /**
     * Stops the clock for good, as its spreadsheet closes: it ticks no more, its listeners are told the changes made
     * before closing, such as playback pausing, and then nothing more. Closing waits briefly for them to be told, and
     * drops what is left if a listener is slow.
     */
    public void close() {
        synchronized (lock) {
            ticking.cancel(false);
            clockThread.shutdown();
        }
        try {
            if (!clockThread.awaitTermination(CLOSE_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                logger.warn("The clock's listeners were still being told " + CLOSE_WAIT_MILLIS
                        + " ms after closing; the rest go untold");
                clockThread.shutdownNow();
            }
        } catch (InterruptedException e) {
            clockThread.shutdownNow();
            Thread.currentThread().interrupt();
        }
        clockListeners.clear();
    }

    /** Returns whether {@link #close()} has stopped the clock's thread. */
    public boolean isClosed() {
        return clockThread.isShutdown();
    }

    /**
     * Un-Registers a clock listener. The clock thread may be telling the listener something as it is removed, so it
     * may hear one call more; a listener that has closed ignores it.
     *
     * @param listener Listener requiring clockTick updates
     */
    public void unRegisterListener(final ClockListener listener) {
        synchronized (lock) {
            clockListeners.remove(listener);
        }
    }

    /**
     * Update the clock time with the elapsed time since the last update
     */
    private void updateElapsedTime() {
        synchronized (lock) {
            double newTime = (double) System.nanoTime();
            clockTime = paused ? clockTime : Math.min(Math.max(
                    clockTime + rate * (newTime - lastTime) / NANO_IN_MILLI, (double) minTime), (double) maxTime);
            lastTime = newTime;
        }
    }

    /**
     * Moves the time on by the time elapsed, pauses at any end of playback it has run into, and then tells the
     * listeners the time, on the clock thread.
     */
    private void tick() {
        long end = -1;
        boolean asked = false;
        while (true) {
            synchronized (lock) {
                updateElapsedTime();
                if (asked || paused) {
                    pauseAtEnd(end);
                    break;
                }
            }
            // Playing: ask where playback ends, outside the lock as it reads the media columns, then judge
            // again, as the clock may have changed meanwhile. Not asked while paused, as it mostly is.
            end = askPlaybackEnd();
            asked = true;
        }
        tellTime(ClockListener::clockTick);
    }

    /** Returns where playback going forward ends, or -1 for the end of the range if it cannot be found. */
    private long askPlaybackEnd() {
        AtomicLong end = new AtomicLong(-1);
        guarded(() -> end.set(playbackEnd.getAsLong()), "The end of playback could not be found");
        return end.get();
    }

    /** Tells every listener some news with the clock's time now, on the clock thread. */
    private void tellTime(final ObjDoubleConsumer<ClockListener> news) {
        double time = getClockTime();
        tell(listener -> news.accept(listener, time));
    }

    /**
     * Tells every listener some news, on the clock thread. A listener that fails is logged and the others are still
     * told, as an exception would also cancel every later tick.
     */
    private void tell(final Consumer<ClockListener> news) {
        for (ClockListener listener : clockListeners) {
            guarded(() -> news.accept(listener), "A clock listener failed");
        }
    }

    /**
     * Runs work on the clock thread that calls out of the clock, logging a failure rather than letting it escape: an
     * exception on the clock thread would cancel every later tick.
     */
    private static void guarded(final Runnable work, final String failure) {
        try {
            work.run();
        } catch (RuntimeException e) {
            logger.error(failure, e);
        }
    }

    /**
     * Has the clock thread tell the listeners of a change, with the clock's time as it is then, once it has told them
     * of everything before. Called under the clock's lock, so changes are told in the order they were made.
     */
    private void post(final ObjDoubleConsumer<ClockListener> news) {
        if (!clockThread.isShutdown()) {
            clockThread.execute(() -> tellTime(news));
        }
    }

    /** Tells the listeners the time was set, which they must follow at once. */
    private void notifyForceSync() {
        post(ClockListener::clockForceSync);
    }

    private void notifyRate() {
        if (!clockThread.isShutdown()) {
            clockThread.execute(() -> {
                float now = getRate();
                tell(listener -> listener.clockRate(now));
            });
        }
    }

    private void notifyStart() {
        post(ClockListener::clockStart);
    }

    private void notifyPause() {
        post(ClockListener::clockPause);
    }

    /**
     * Listener for the clock's ticks and changes. Every call comes on the clock's thread, in the order the clock
     * changed, without the clock's lock held, and carries the clock's time and rate as they are when it is made;
     * ticks come every 100 ms, whether or not the clock is running.
     */
    public interface ClockListener {
        /**
         * Called on each tick, once the clock has paused at any end of playback it ran into: listeners follow the
         * time, start or stop the clips it has entered or left, and catch up if they have drifted.
         *
         * @param clockTime Current time in milliseconds
         */
        void clockTick(double clockTime);

        /**
         * Called when the time is set, as by a jump or a pause, which listeners must follow at once.
         *
         * @param clockTime Current time in milliseconds
         */
        void clockForceSync(double clockTime);

        /**
         * Called when the clock starts running.
         *
         * @param clockTime Current time in milliseconds
         */
        void clockStart(double clockTime);

        /**
         * Called when the clock pauses.
         *
         * @param clockTime Current time in milliseconds
         */
        void clockPause(double clockTime);

        /**
         * Called when the clock's rate changes.
         *
         * @param rate Current (updated) rate.
         */
        void clockRate(float rate);
    }
}
