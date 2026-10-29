package org.datavyu.views.discrete;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.awt.Color;

/**
 * The palettes keep the contrast the long-session look was designed to (AGENTS.md › Decisions, U5): values at
 * 10–12.5:1, softer for times and ordinals, coloured marks that are still seen, no pure black or white behind the
 * cells, and a cell in play lifted rather than filled.
 */
public class PaletteTest {

    @DataProvider
    public Object[][] palettes() {
        return new Object[][]{{Palette.PAPER}, {Palette.GRAPHITE}};
    }

    @Test
    public void theMacsAppearanceChoosesThePalette() {
        assertSame(Palette.forAppearance(false), Palette.PAPER);
        assertSame(Palette.forAppearance(true), Palette.GRAPHITE);
        assertTrue(Palette.GRAPHITE.dark());
        assertFalse(Palette.PAPER.dark());
    }

    @Test(dataProvider = "palettes")
    public void valuesAreClearWithoutGlare(final Palette palette) {
        assertBand(palette.text(), palette.cell(), 10, 12.5);
        assertBand(palette.text(), palette.inPlay(), 10, 12.5);
    }

    @Test(dataProvider = "palettes")
    public void timesAndOrdinalsAreSofterThanValues(final Palette palette) {
        assertBand(palette.time(), palette.cell(), 5, 7.5);
        assertBand(palette.ordinal(), palette.cell(), 2.5, 4.5);
    }

    @Test(dataProvider = "palettes")
    public void marksAreSeenAgainstCellsAndChrome(final Palette palette) {
        for (Color mark : new Color[]{palette.accent(), palette.selection(), palette.warning()}) {
            assertBand(mark, palette.cell(), 3, 21);
        }
        assertBand(palette.accent(), palette.chrome(), 3, 21);
        assertBand(palette.warning(), palette.chrome(), 3, 21);
    }

    @Test(dataProvider = "palettes")
    public void theCellInPlayIsALiftNotAFill(final Palette palette) {
        assertNotEquals(palette.inPlay(), palette.cell());
        assertBand(palette.inPlay(), palette.cell(), 1.0, 1.15);
    }

    @Test(dataProvider = "palettes")
    public void rulesShowBetweenCells(final Palette palette) {
        assertBand(palette.rule(), palette.cell(), 1.15, 2);
    }

    @Test(dataProvider = "palettes")
    public void noBackgroundIsPureBlackOrWhite(final Palette palette) {
        for (Color background : new Color[]{palette.chrome(), palette.ground(), palette.cell(), palette.inPlay()}) {
            assertNotEquals(background, Color.BLACK);
            assertNotEquals(background, Color.WHITE);
            double luminance = luminance(background);
            assertTrue(luminance > 0.005 && luminance < 0.9, Palette.toHex(background) + " " + luminance);
        }
    }

    @Test(dataProvider = "palettes")
    public void theVideoSurroundIsDarkerThanTheSheet(final Palette palette) {
        assertTrue(luminance(palette.surround()) < luminance(palette.ground()));
    }

    @Test
    public void fadingBlendsTowardTheCellColour() {
        Palette paper = Palette.PAPER;
        assertEquals(paper.fade(paper.text(), 0), paper.text());
        assertEquals(paper.fade(paper.text(), 100), paper.cell());
        Color half = paper.fade(Color.BLACK, 50);
        assertEquals(half, new Color(117, 118, 116));
    }

    @Test
    public void fadingClampsToNoneAndAll() {
        Palette graphite = Palette.GRAPHITE;
        assertEquals(graphite.fade(graphite.text(), -20), graphite.text());
        assertEquals(graphite.fade(graphite.text(), 140), graphite.cell());
    }

    @Test(dataProvider = "palettes")
    public void fadingMakesTextSofterStepByStep(final Palette palette) {
        double last = contrast(palette.text(), palette.cell());
        for (int percent = 20; percent <= 100; percent += 20) {
            double now = contrast(palette.fade(palette.text(), percent), palette.cell());
            assertTrue(now < last, percent + "%: " + now + " after " + last);
            last = now;
        }
    }

    @Test
    public void hexIsLowerCaseRgb() {
        assertEquals(Palette.toHex(new Color(0x1a, 0x7a, 0x45)), "#1a7a45");
        assertEquals(Palette.toHex(new Color(255, 0, 10, 40)), "#ff000a");
    }

    private static void assertBand(final Color a, final Color b, final double min, final double max) {
        double ratio = contrast(a, b);
        assertTrue(ratio >= min && ratio <= max,
                Palette.toHex(a) + " on " + Palette.toHex(b) + " is " + ratio + ":1, outside " + min + "–" + max);
    }

    /** WCAG 2 contrast ratio. */
    static double contrast(final Color a, final Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** WCAG 2 relative luminance. */
    static double luminance(final Color colour) {
        return 0.2126 * linear(colour.getRed()) + 0.7152 * linear(colour.getGreen())
                + 0.0722 * linear(colour.getBlue());
    }

    private static double linear(final int channel) {
        double c = channel / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
