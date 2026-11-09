package com.sh0sh1n.watavyu.util;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
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
            }).when(failing).clockPeriodicSync(anyDouble());
            clock.registerListener(failing);

            assertTrue(later.await(5, TimeUnit.SECONDS), "ticks after the failure: " + (ticks.get() - 1));
        } finally {
            clock.close();
        }
    }

    /** A listener that does nothing, for tests to override the calls they follow. */
    private static class Quiet implements ClockListener {
        @Override
        public void clockSeekPlayback(final double clockTime) {
            // Not followed.
        }

        @Override
        public void clockBoundaryCheck(final double clockTime) {
            // Not followed.
        }

        @Override
        public void streamsBoundaryCheck(final double clockTime) {
            // Not followed.
        }

        @Override
        public void clockForceSync(final double clockTime) {
            // Not followed.
        }

        @Override
        public void clockPeriodicSync(final double clockTime) {
            // Not followed.
        }

        @Override
        public void clockStart(final double clockTime) {
            // Not followed.
        }

        @Override
        public void clockStop(final double clockTime) {
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
                public void clockPeriodicSync(final double clockTime) {
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
                public void clockBoundaryCheck(final double clockTime) {
                    throw new IllegalStateException("fails on every tick");
                }
            });
            CountDownLatch ticks = new CountDownLatch(3);
            clock.registerListener(new Quiet() {
                @Override
                public void clockBoundaryCheck(final double clockTime) {
                    ticks.countDown();
                }
            });

            assertTrue(ticks.await(5, TimeUnit.SECONDS));
        } finally {
            clock.close();
        }
    }

    /** Closing stops the ticks but still tells the listeners what changed before it, such as playback stopping. */
    @Test
    public void closingStillTellsTheChangesMadeBeforeIt() {
        ClockTimer clock = new ClockTimer();
        clock.setMaxTime(10_000);
        List<String> told = new CopyOnWriteArrayList<>();
        AtomicLong lastForce = new AtomicLong(-1);
        clock.registerListener(new Quiet() {
            @Override
            public void clockStop(final double clockTime) {
                told.add("stop");
            }

            @Override
            public void clockForceSync(final double clockTime) {
                told.add("force");
                lastForce.set((long) clockTime);
            }
        });

        clock.setRate(1f);
        clock.setRate(0f);
        clock.setForceTime(4_000);
        clock.close();

        assertTrue(told.contains("stop"), "told: " + told);
        assertEquals(told.subList(told.indexOf("stop"), told.size()), List.of("stop", "force", "force"));
        assertEquals(lastForce.get(), 4_000L);
    }

    /**
     * A boundary is judged by the clock's time as it is when the check is made, so a jump made since the tick that
     * prompted it is not undone.
     */
    @Test
    public void aBoundaryIsJudgedByTheTimeNow() {
        ClockTimer clock = new ClockTimer();
        try {
            clock.setMaxTime(10_000);
            clock.setForceTime(6_000);
            clock.start();
            clock.setForceTime(2_000);

            assertFalse(clock.pauseIf((time, rate) -> time >= 5_000, 5_000), "the jump back left the boundary");
            assertFalse(clock.isPaused());

            clock.setForceTime(7_000);
            assertTrue(clock.pauseIf((time, rate) -> time >= 5_000, 5_000));
            assertTrue(clock.isPaused());
            assertEquals(clock.getClockTime(), 5_000.0);

            assertFalse(clock.pauseIf((time, rate) -> true), "a paused clock is not paused again");
        } finally {
            clock.close();
        }
    }

    /** A boundary may pause the clock where it is. */
    @Test
    public void aBoundaryWithNoTimePausesTheClockWhereItIs() {
        ClockTimer clock = new ClockTimer();
        try {
            clock.setMaxTime(10_000);
            clock.setForceTime(3_000);
            clock.setRate(-1f);

            assertTrue(clock.pauseIf((time, rate) -> rate < 0));
            assertTrue(clock.isPaused());
            assertTrue(clock.getClockTime() <= 3_000.0 && clock.getClockTime() > 2_000.0,
                "paused where it was: " + clock.getClockTime());
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
                public void clockPeriodicSync(final double clockTime) {
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
