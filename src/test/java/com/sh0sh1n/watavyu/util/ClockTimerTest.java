package com.sh0sh1n.watavyu.util;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.util.ClockTimer.ClockListener;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** The clock ticks on through a failing listener; a closed one, as of a closed tab, has stopped and tells nothing. */
public class ClockTimerTest {

    @Test
    public void aClosedClockTellsItsListenersNothing() {
        ClockTimer clock = new ClockTimer();
        ClockListener before = mock(ClockListener.class);
        ClockListener after = mock(ClockListener.class);
        clock.registerListener(before);
        assertFalse(clock.isClosed());

        clock.close();
        clock.registerListener(after);
        clock.setForceTime(5_000);
        clock.setRate(1f);
        clock.start();
        clock.pause();

        assertTrue(clock.isClosed());
        verifyNoInteractions(before, after);
    }

    /** A listener that throws on a tick is logged, and it and the others go on hearing the ticks after. */
    @Test
    public void aListenerThatFailsDoesNotStopTheClock() throws InterruptedException {
        ClockTimer clock = new ClockTimer();
        try {
            AtomicInteger ticks = new AtomicInteger();
            CountDownLatch later = new CountDownLatch(3);
            ClockListener failing = mock(ClockListener.class);
            doAnswer(invocation -> {
                if (ticks.getAndIncrement() == 0) {
                    throw new IllegalStateException("the first tick fails");
                }
                later.countDown();
                return null;
            }).when(failing).clockTick(anyDouble());
            clock.registerListener(failing);

            assertTrue(later.await(5, TimeUnit.SECONDS), "ticks after the failure: " + (ticks.get() - 1));
        } finally {
            clock.close();
        }
    }

    /** A listener that does nothing, for tests to override the calls they follow. */
    private static class Quiet implements ClockListener {
        @Override
        public void clockTick(final double clockTime) {
            // Not followed.
        }

        @Override
        public void clockForceSync(final double clockTime) {
            // Not followed.
        }

        @Override
        public void clockStart(final double clockTime) {
            // Not followed.
        }

        @Override
        public void clockPause(final double clockTime) {
            // Not followed.
        }

        @Override
        public void clockRate(final float rate) {
            // Not followed.
        }
    }

    /** Ticks and changes alike come on the clock's one thread, not on the thread that made the change. */
    @Test
    public void everyCallComesOnTheClockThread() throws InterruptedException {
        ClockTimer clock = new ClockTimer();
        try {
            clock.setMaxTime(10_000);
            List<Thread> threads = new CopyOnWriteArrayList<>();
            CountDownLatch heard = new CountDownLatch(4);
            clock.registerListener(new Quiet() {
                @Override
                public void clockTick(final double clockTime) {
                    threads.add(Thread.currentThread());
                }

                @Override
                public void clockRate(final float rate) {
                    threads.add(Thread.currentThread());
                    heard.countDown();
                }

                @Override
                public void clockStart(final double clockTime) {
                    threads.add(Thread.currentThread());
                    heard.countDown();
                }

                @Override
                public void clockPause(final double clockTime) {
                    threads.add(Thread.currentThread());
                    heard.countDown();
                }

                @Override
                public void clockForceSync(final double clockTime) {
                    threads.add(Thread.currentThread());
                    heard.countDown();
                }
            });

            clock.setRate(1f);
            clock.start();
            clock.pause();

            assertTrue(heard.await(5, TimeUnit.SECONDS));
            Set<Thread> distinct = Set.copyOf(threads);
            assertEquals(distinct.size(), 1, "threads: " + distinct);
            Thread clockThread = distinct.iterator().next();
            assertNotEquals(clockThread, Thread.currentThread());
            assertEquals(clockThread.getName(), "clock");
        } finally {
            clock.close();
        }
    }

    /** Changes are told in the order they were made, each with the time as it was then. */
    @Test
    public void changesAreToldInTheOrderTheyWereMade() throws InterruptedException {
        ClockTimer clock = new ClockTimer();
        try {
            clock.setMaxTime(10_000);
            List<String> told = new CopyOnWriteArrayList<>();
            CountDownLatch forces = new CountDownLatch(3);
            clock.registerListener(new Quiet() {
                @Override
                public void clockForceSync(final double clockTime) {
                    told.add("force");
                    forces.countDown();
                }

                @Override
                public void clockRate(final float rate) {
                    told.add("rate " + rate);
                }

                @Override
                public void clockStart(final double clockTime) {
                    told.add("start");
                }

                @Override
                public void clockPause(final double clockTime) {
                    told.add("pause");
                }
            });

            clock.setForceTime(1_000);
            clock.setRate(2f);
            clock.start();
            clock.pause();
            clock.setForceTime(3_000);

            assertTrue(forces.await(5, TimeUnit.SECONDS), "told: " + told);
            assertEquals(told, List.of("force", "rate 2.0", "start", "pause", "force", "force"));
        } finally {
            clock.close();
        }
    }

    /**
     * The clock tells its listeners without holding its lock, so a listener may wait on a thread that uses the clock,
     * as one waiting on the Swing event thread would while that thread sets the rate.
     */
    @Test
    public void aListenerMayWaitOnAnotherThreadThatUsesTheClock() throws InterruptedException {
        ClockTimer clock = new ClockTimer();
        try {
            CountDownLatch waited = new CountDownLatch(1);
            clock.registerListener(new Quiet() {
                @Override
                public void clockStart(final double clockTime) {
                    try {
                        CompletableFuture.supplyAsync(clock::getClockTime).get(2, TimeUnit.SECONDS);
                        waited.countDown();
                    } catch (Exception e) {
                        throw new IllegalStateException("the other thread could not use the clock", e);
                    }
                }
            });

            clock.start();

            assertTrue(waited.await(5, TimeUnit.SECONDS));
        } finally {
            clock.close();
        }
    }

    /** A listener that fails on every tick keeps no other listener from hearing them. */
    @Test
    public void aFailingListenerKeepsNoOtherFromHearing() throws InterruptedException {
        ClockTimer clock = new ClockTimer();
        try {
            clock.registerListener(new Quiet() {
                @Override
                public void clockTick(final double clockTime) {
                    throw new IllegalStateException("fails on every tick");
                }
            });
            CountDownLatch ticks = new CountDownLatch(3);
            clock.registerListener(new Quiet() {
                @Override
                public void clockTick(final double clockTime) {
                    ticks.countDown();
                }
            });

            assertTrue(ticks.await(5, TimeUnit.SECONDS));
        } finally {
            clock.close();
        }
    }

    /** Closing stops the ticks but still tells the listeners what changed before it, such as playback pausing. */
    @Test
    public void closingStillTellsTheChangesMadeBeforeIt() {
        ClockTimer clock = new ClockTimer();
        clock.setMaxTime(10_000);
        List<String> told = new CopyOnWriteArrayList<>();
        AtomicLong lastForce = new AtomicLong(-1);
        clock.registerListener(new Quiet() {
            @Override
            public void clockPause(final double clockTime) {
                told.add("pause");
            }

            @Override
            public void clockForceSync(final double clockTime) {
                told.add("force");
                lastForce.set((long) clockTime);
            }
        });

        clock.start();
        clock.pause();
        clock.setForceTime(4_000);
        clock.close();

        assertTrue(told.contains("pause"), "told: " + told);
        assertEquals(told.subList(told.indexOf("pause"), told.size()), List.of("pause", "force", "force"));
        assertEquals(lastForce.get(), 4_000L);
    }

    /** Waits for a running clock to pause on its own, at a tick. */
    private static void awaitPause(final ClockTimer clock) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!clock.isPaused() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(clock.isPaused(), "paused by a tick, at " + clock.getClockTime());
    }

    /**
     * Playing forward, the clock pauses at the end of playback it is given and moves to it, before any listener hears
     * the tick, so none hears a time past the end; the listeners are told of the pause once.
     */
    @Test
    public void playingForwardPausesAtTheEndOfPlayback() throws InterruptedException {
        ClockTimer clock = new ClockTimer();
        try {
            clock.setMaxTime(10_000);
            clock.setPlaybackEnd(() -> 2_150);
            List<Double> ticks = new CopyOnWriteArrayList<>();
            List<String> changes = new CopyOnWriteArrayList<>();
            clock.registerListener(new Quiet() {
                @Override
                public void clockTick(final double clockTime) {
                    ticks.add(clockTime);
                }

                @Override
                public void clockForceSync(final double clockTime) {
                    changes.add("force");
                }

                @Override
                public void clockPause(final double clockTime) {
                    changes.add("pause");
                }
            });
            clock.setForceTime(2_000);
            clock.setRate(2f);
            clock.start();

            awaitPause(clock);
            clock.close();
            assertEquals(clock.getClockTime(), 2_150.0);
            assertTrue(ticks.stream().allMatch(time -> time <= 2_150), "ticks: " + ticks);
            assertEquals(changes, List.of("force", "pause", "force"), "the jump to 2000, then the pause at the end");
        } finally {
            clock.close();
        }
    }

    /** With no end of playback, playing forward pauses at the end of the clock's range. */
    @Test
    public void playingForwardWithNoEndPausesAtTheEndOfTheRange() throws InterruptedException {
        ClockTimer clock = new ClockTimer();
        try {
            clock.setMaxTime(3_000);
            clock.setForceTime(2_900);
            clock.setRate(2f);
            clock.start();

            awaitPause(clock);
            assertEquals(clock.getClockTime(), 3_000.0);
        } finally {
            clock.close();
        }
    }

    /**
     * A paused clock past the end of playback stays where it is, as after a jump there, and is not asked where
     * playback ends.
     */
    @Test
    public void aPausedClockPastTheEndStaysWhereItIs() throws InterruptedException {
        ClockTimer clock = new ClockTimer();
        try {
            clock.setMaxTime(10_000);
            AtomicInteger asked = new AtomicInteger();
            clock.setPlaybackEnd(() -> {
                asked.incrementAndGet();
                return 2_150;
            });
            CountDownLatch ticks = new CountDownLatch(3);
            clock.registerListener(new Quiet() {
                @Override
                public void clockTick(final double clockTime) {
                    ticks.countDown();
                }
            });
            clock.setForceTime(5_000);

            assertTrue(ticks.await(5, TimeUnit.SECONDS));
            assertTrue(clock.isPaused());
            assertEquals(clock.getClockTime(), 5_000.0);
            assertEquals(asked.get(), 0, "asked while paused");
        } finally {
            clock.close();
        }
    }

    /** If the end of playback cannot be found, the failure is logged and the clock goes on ticking. */
    @Test
    public void aFailingPlaybackEndKeepsTheClockTicking() throws InterruptedException {
        ClockTimer clock = new ClockTimer();
        try {
            clock.setMaxTime(60_000);
            AtomicInteger asked = new AtomicInteger();
            clock.setPlaybackEnd(() -> {
                asked.incrementAndGet();
                throw new IllegalStateException("no end");
            });
            CountDownLatch ticks = new CountDownLatch(3);
            clock.registerListener(new Quiet() {
                @Override
                public void clockTick(final double clockTime) {
                    ticks.countDown();
                }
            });
            clock.start();

            assertTrue(ticks.await(5, TimeUnit.SECONDS), "ticks after the failure");
            assertTrue(asked.get() >= 2, "asked " + asked.get() + " times");
        } finally {
            clock.close();
        }
    }

    /** A new clock is paused at the normal rate. */
    @Test
    public void aNewClockIsPausedAtTheNormalRate() {
        ClockTimer clock = new ClockTimer();
        try {
            assertTrue(clock.isPaused());
            assertEquals(clock.getRate(), 1f);
        } finally {
            clock.close();
        }
    }

    /**
     * The clock plays forward only, and its rate is never 0: a clock that should not move is paused, and keeps the
     * rate it resumes at.
     */
    @Test
    public void theRateIsAlwaysForward() {
        ClockTimer clock = new ClockTimer();
        try {
            clock.setRate(2f);
            assertThrows(IllegalArgumentException.class, () -> clock.setRate(0f));
            assertThrows(IllegalArgumentException.class, () -> clock.setRate(-1f));
            assertThrows(IllegalArgumentException.class, () -> clock.setRate(Float.NaN));
            assertEquals(clock.getRate(), 2f);
        } finally {
            clock.close();
        }
    }

    /** Setting the rate leaves a paused clock paused, and a running one running at the new rate. */
    @Test
    public void settingTheRateNeitherStartsNorPauses() {
        ClockTimer clock = new ClockTimer();
        try {
            clock.setMaxTime(60_000);
            clock.setRate(2f);
            assertTrue(clock.isPaused(), "a paused clock stays paused");

            clock.start();
            clock.setRate(0.5f);
            assertFalse(clock.isPaused(), "a running clock keeps running");
            assertEquals(clock.getRate(), 0.5f);

            clock.pause();
            clock.start();
            assertEquals(clock.getRate(), 0.5f, "a clock resumes at the rate it paused at");
        } finally {
            clock.close();
        }
    }

    /**
     * A change is told with the clock's time when it is told, not when it was made: a player told late of a jump
     * follows a step taken since, rather than going back to the jump.
     */
    @Test
    public void newsCarriesTheTimeWhenItIsTold() throws InterruptedException {
        ClockTimer clock = new ClockTimer();
        try {
            clock.setMaxTime(10_000);
            CountDownLatch busy = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch bothForces = new CountDownLatch(2);
            List<Double> forcedTo = new CopyOnWriteArrayList<>();
            clock.registerListener(new Quiet() {
                @Override
                public void clockTick(final double clockTime) {
                    if (busy.getCount() > 0) {
                        busy.countDown();
                        try {
                            release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

                @Override
                public void clockForceSync(final double clockTime) {
                    forcedTo.add(clockTime);
                    bothForces.countDown();
                }
            });

            assertTrue(busy.await(5, TimeUnit.SECONDS), "the clock thread is busy with a tick");
            clock.setForceTime(1_000);
            clock.setTime(1_033);
            clock.setForceTime(2_000);
            clock.setTime(2_033);
            release.countDown();

            assertTrue(bothForces.await(5, TimeUnit.SECONDS), "told: " + forcedTo);
            assertEquals(forcedTo, List.of(2_033.0, 2_033.0));
        } finally {
            clock.close();
        }
    }

    /** The clock's time, rate and pause are read together. */
    @Test
    public void aReadingTakesTheClockAtOneMoment() {
        ClockTimer clock = new ClockTimer();
        try {
            clock.setMaxTime(10_000);
            clock.setForceTime(4_000);
            clock.setRate(2f);
            clock.start();
            clock.pause();

            ClockTimer.Reading now = clock.read();
            assertEquals(now.rate(), 2f);
            assertTrue(now.paused());
            assertEquals(now.time(), (long) clock.getClockTime());
        } finally {
            clock.close();
        }
    }
}
