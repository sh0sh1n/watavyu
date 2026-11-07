package org.datavyu.util;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.util.ClockTimer.ClockListener;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
}
