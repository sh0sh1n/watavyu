package com.sh0sh1n.watavyu.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The accent popup while a key is held is turned off for this app alone: in the app's own defaults domain, never the
 * global one, and only where the user has not set it.
 */
public class PressAndHoldTest {

    @Test
    public void theAppSetsItInItsOwnDomain() {
        assertEquals(MacOS.pressAndHoldDomain("com.sh0sh1n.watavyu"), "com.sh0sh1n.watavyu");
        assertEquals(MacOS.pressAndHoldDomain(null), "net.java.openjdk.java");
    }

    /**
     * Outside the app, as under {@code ./gradlew run}, the domain is the bundle identifier the JDK's {@code java}
     * launcher embeds, read here from the launcher of the JDK running the tests, the one {@code run} uses.
     */
    @Test
    public void outsideTheAppTheDomainIsTheJavaLaunchers() throws Exception {
        String launcher = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process otool = new ProcessBuilder("otool", "-s", "__TEXT", "__info_plist", "-V", launcher)
            .redirectErrorStream(true).start();
        String plist = new String(otool.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        otool.waitFor();
        Matcher id = Pattern.compile("<key>CFBundleIdentifier</key>\\s*<string>([^<]+)</string>").matcher(plist);
        assertTrue(id.find(), plist);
        assertEquals(MacOS.JAVA_LAUNCHER_DOMAIN, id.group(1));
    }

    @Test
    public void itIsNeverSetForEveryApp() {
        for (String domain : List.of(MacOS.pressAndHoldDomain(null), MacOS.pressAndHoldDomain("com.sh0sh1n.watavyu"))) {
            assertEquals(MacOS.turnOffPressAndHold(domain),
                List.of("write", domain, "ApplePressAndHoldEnabled", "-bool", "false"));
            assertFalse(MacOS.turnOffPressAndHold(domain).contains("-g"));
            assertFalse(MacOS.turnOffPressAndHold(domain).contains("NSGlobalDomain"));
        }
    }

    /**
     * Only a missing value is written, so one the user set, on or off, is left. A value is missing both from a domain
     * that does not exist yet, as the app's does not before its first launch, and from one without the key. Checked
     * against the real {@code defaults}, with a property list file of its own as the domain, outside the user's.
     */
    @Test
    public void aValueIsMissingFromANewDomainAndFromOneWithoutTheKey() throws Exception {
        Path dir = Files.createTempDirectory("press-and-hold");
        String domain = dir.resolve("test.plist").toString();
        try {
            assertFalse(MacOS.isSet(domain, "ApplePressAndHoldEnabled"), "no domain yet");
            assertEquals(MacOS.defaults(List.of("write", domain, "Other", "-bool", "true")).exitCode(), 0);
            assertFalse(MacOS.isSet(domain, "ApplePressAndHoldEnabled"), "a domain without the key");
            assertEquals(MacOS.defaults(MacOS.turnOffPressAndHold(domain)).exitCode(), 0);
            assertTrue(MacOS.isSet(domain, "ApplePressAndHoldEnabled"), "turned off");
        } finally {
            Files.deleteIfExists(Path.of(domain));
            Files.deleteIfExists(dir);
        }
    }

    /** The domain for a file is the file, so the test above stays out of the user's preferences. */
    @Test
    public void aFileDomainIsWrittenAsTheFile() throws Exception {
        Path dir = Files.createTempDirectory("press-and-hold");
        File plist = dir.resolve("file.plist").toFile();
        try {
            MacOS.defaults(MacOS.turnOffPressAndHold(plist.getPath()));
            assertTrue(plist.isFile());
        } finally {
            Files.deleteIfExists(plist.toPath());
            Files.deleteIfExists(dir);
        }
    }
}
