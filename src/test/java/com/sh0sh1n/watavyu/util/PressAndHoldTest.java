package com.sh0sh1n.watavyu.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import java.util.List;

/**
 * The accent popup while a key is held is turned off for this app alone (TODO.md B2): in the app's own defaults
 * domain, never the global one, and only where the user has not set it.
 */
public class PressAndHoldTest {

    @Test
    public void theAppSetsItInItsOwnDomain() {
        assertEquals(MacOS.pressAndHoldDomain("/Applications/Watavyu.app/Contents/MacOS/Watavyu"),
            "com.sh0sh1n.watavyu");
        assertEquals(MacOS.pressAndHoldDomain(null), "java", "a bare JVM, as under ./gradlew run, is named java");
    }

    @Test
    public void itIsNeverSetForEveryApp() {
        for (String domain : List.of(MacOS.pressAndHoldDomain(null), MacOS.pressAndHoldDomain("/app"))) {
            assertEquals(MacOS.turnOffPressAndHold(domain),
                List.of("write", domain, "ApplePressAndHoldEnabled", "-bool", "false"));
            assertEquals(MacOS.readPressAndHold(domain), List.of("read", domain, "ApplePressAndHoldEnabled"));
            assertFalse(MacOS.turnOffPressAndHold(domain).contains("-g"));
            assertFalse(MacOS.turnOffPressAndHold(domain).contains("NSGlobalDomain"));
        }
    }

    /** A value the user set, on or off, is left as it is; only a missing one is written. */
    @Test
    public void onlyAnUnsetValueIsWritten() {
        assertTrue(MacOS.isUnset(List.of(
            "2026-11-27 10:00:00.000 defaults[1:2] ",
            "The domain/default pair of (com.sh0sh1n.watavyu, ApplePressAndHoldEnabled) does not exist")));
        assertFalse(MacOS.isUnset(List.of("1")));
        assertFalse(MacOS.isUnset(List.of("0")));
    }
}
