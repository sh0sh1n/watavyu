package com.sh0sh1n.watavyu.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The accent popup while a key is held is turned off for this app alone: in the app's own defaults domain, never the
 * global one, and only where the user has not set it.
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
            assertFalse(MacOS.turnOffPressAndHold(domain).contains("-g"));
            assertFalse(MacOS.turnOffPressAndHold(domain).contains("NSGlobalDomain"));
        }
    }

    /**
     * Only a missing value is written, so one the user set, on or off, is left. A value is missing both from a domain
     * that does not exist yet, as the app's does not before its first launch, and from one without the key. Checked
     * against the real {@code defaults} in a throwaway domain.
     */
    @Test
    public void aValueIsMissingFromANewDomainAndFromOneWithoutTheKey() throws Exception {
        String domain = "com.sh0sh1n.watavyu.press-and-hold-test";
        MacOS.defaults(List.of("delete", domain));
        try {
            assertFalse(MacOS.isSet(domain, "ApplePressAndHoldEnabled"), "no domain yet");
            assertEquals(MacOS.defaults(List.of("write", domain, "Other", "-bool", "true")).exitCode(), 0);
            assertFalse(MacOS.isSet(domain, "ApplePressAndHoldEnabled"), "a domain without the key");
            assertEquals(MacOS.defaults(MacOS.turnOffPressAndHold(domain)).exitCode(), 0);
            assertTrue(MacOS.isSet(domain, "ApplePressAndHoldEnabled"), "turned off");
        } finally {
            // Deleting a domain's values leaves its file, empty.
            MacOS.defaults(List.of("delete", domain));
            Files.deleteIfExists(Path.of(System.getProperty("user.home"), "Library", "Preferences", domain + ".plist"));
        }
    }
}
