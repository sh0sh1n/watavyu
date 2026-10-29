package org.datavyu.util;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

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
}
