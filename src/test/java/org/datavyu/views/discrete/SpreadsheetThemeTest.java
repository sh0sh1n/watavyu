package org.datavyu.views.discrete;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.awt.Color;
import java.awt.Font;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The look and feel takes its colours from the palette, and the values font switches between Plex Sans and Plex Mono
 * at the size zoom gave it.
 */
public class SpreadsheetThemeTest {

    @AfterMethod
    public void restore() {
        SpreadsheetTheme.setMonospacedValues(false);
        SpreadsheetTheme.setDataFontSize(SpreadsheetTheme.DEFAULT_DATA_FONT_SIZE);
    }

    @Test
    public void theSystemPartsTakeThePalette() {
        for (Palette palette : new Palette[]{Palette.PAPER, Palette.GRAPHITE}) {
            Map<String, String> defaults = SpreadsheetTheme.lookAndFeelDefaults(palette);
            assertEquals(defaults.get("@background"), Palette.toHex(palette.chrome()));
            assertEquals(defaults.get("@foreground"), Palette.toHex(palette.text()));
            assertEquals(defaults.get("@accentColor"), Palette.toHex(palette.accent()));
            assertEquals(defaults.get("TextField.background"), Palette.toHex(palette.cell()));
            assertEquals(defaults.get("Viewport.background"), Palette.toHex(palette.ground()));
            assertEquals(defaults.get("Component.borderColor"), Palette.toHex(palette.rule()));
        }
    }

    @Test
    public void everyLookAndFeelColourComesFromThePalette() {
        Palette palette = Palette.GRAPHITE;
        Set<String> tokens = new HashSet<>();
        for (Color colour : new Color[]{palette.chrome(), palette.ground(), palette.cell(),
                palette.rule(), palette.text(), palette.accent()}) {
            tokens.add(Palette.toHex(colour));
        }
        SpreadsheetTheme.lookAndFeelDefaults(palette).forEach((key, value) ->
                assertTrue(tokens.contains(value), key + " = " + value));
    }

    @Test
    public void theValuesFontSwitchesFaceAndKeepsItsSize() {
        SpreadsheetTheme.setDataFontSize(19);
        Font sans = SpreadsheetTheme.dataFont();
        SpreadsheetTheme.setMonospacedValues(true);
        Font mono = SpreadsheetTheme.dataFont();
        assertEquals(mono.getSize(), 19);
        assertEquals(mono.getFamily(), SpreadsheetTheme.labelFont().getFamily());
        assertNotEquals(mono.getFamily(), sans.getFamily());

        SpreadsheetTheme.setDataFontSize(12);
        assertEquals(SpreadsheetTheme.dataFont().getFamily(), mono.getFamily());
    }

    @Test
    public void timesAreInPlexMono() {
        assertEquals(SpreadsheetTheme.labelFont().getFamily(), "IBM Plex Mono");
        assertEquals(SpreadsheetTheme.timeFont(11F).getFamily(), "IBM Plex Mono");
        assertEquals(SpreadsheetTheme.uiFont(11F).getFamily(), "IBM Plex Sans");
    }

    /** Bundled fonts fall back to nothing, so a character they lack shows as a box. */
    @Test
    public void theFontsHaveEveryCharacterTheyAreGiven() {
        assertEquals(SpreadsheetTheme.labelFont().canDisplayUpTo("00:00:40:000 paused 0.25× 1.5× stopped"), -1);
        assertEquals(SpreadsheetTheme.uiFont(9.5F).canDisplayUpTo("new + prev offset · Jump by Onset"), -1);
        assertEquals(SpreadsheetTheme.headerFont().canDisplayUpTo("media_1 infant_gaze"), -1);
    }
}
