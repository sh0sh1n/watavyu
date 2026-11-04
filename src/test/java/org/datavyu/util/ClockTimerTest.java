package org.datavyu.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.util.ClockTimer.ClockListener;
import org.testng.annotations.Test;

/** A closed clock, as of a closed tab, has stopped its threads and tells its listeners nothing more. */
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
}
