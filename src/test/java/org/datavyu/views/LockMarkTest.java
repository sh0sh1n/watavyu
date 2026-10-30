package org.datavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;

/** The lock mark shakes briefly from side to side, then comes to rest where it was. */
public class LockMarkTest {

    @Test
    public void theShakeSwingsBothWaysWithinItsAmplitudeAndComesToRest() {
        assertEquals(LockMark.offsetAt(0), 0);
        assertEquals(LockMark.offsetAt(LockMark.SHAKE_MILLIS), 0);
        assertEquals(LockMark.offsetAt(LockMark.SHAKE_MILLIS + 1_000), 0);

        int least = 0;
        int most = 0;
        for (int t = 0; t <= LockMark.SHAKE_MILLIS; t++) {
            int offset = LockMark.offsetAt(t);
            assertTrue(Math.abs(offset) <= LockMark.AMPLITUDE, "at " + t + " ms: " + offset);
            least = Math.min(least, offset);
            most = Math.max(most, offset);
        }
        assertTrue(least < 0 && most > 0, "it swings both ways");
    }

    @Test
    public void shakingStopsOnItsOwn() throws Exception {
        AtomicBoolean shaking = new AtomicBoolean();
        LockMark[] mark = new LockMark[1];
        SwingUtilities.invokeAndWait(() -> {
            mark[0] = new LockMark(14, new JLabel());
            assertFalse(mark[0].isShaking());
            mark[0].shake();
            shaking.set(mark[0].isShaking());
        });
        assertTrue(shaking.get());

        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            SwingUtilities.invokeAndWait(() -> shaking.set(mark[0].isShaking()));
            if (!shaking.get()) {
                break;
            }
            Thread.sleep(20);
        }
        assertFalse(shaking.get());
    }
}
