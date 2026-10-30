package org.datavyu.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import java.util.List;

/**
 * The palette follows the Mac's appearance, and the {@code watavyu.appearance} property overrides it.
 */
public class AppearanceTest {

    @Test
    public void darkAppearanceGivesTheDarkPalette() {
        assertTrue(Appearance.isDark(null, () -> "Dark\n"));
    }

    @Test
    public void lightAppearanceHasNoInterfaceStyle() {
        assertFalse(Appearance.isDark(null, () -> null));
        assertFalse(Appearance.isDark("", () -> null));
    }

    @Test
    public void thePropertyOverridesTheMac() {
        assertFalse(Appearance.isDark("light", () -> "Dark"));
        assertTrue(Appearance.isDark(" Dark ", () -> null));
    }

    @Test
    public void anUnknownOverrideMeansLight() {
        assertFalse(Appearance.isDark("sepia", () -> "Dark"));
    }

    @Test
    public void aCommandsOutputIsRead() {
        assertEquals(Appearance.run(List.of("echo", "Dark"), 5_000), "Dark\n");
        assertNull(Appearance.run(List.of("false"), 5_000), "a failed command says nothing");
    }

    /** Startup never waits on macOS for longer than the timeout. */
    @Test
    public void aCommandThatHangsTimesOut() {
        long start = System.nanoTime();

        assertNull(Appearance.run(List.of("sleep", "30"), 200));

        assertTrue(System.nanoTime() - start < 5_000_000_000L, "gave up in time");
    }
}
