package org.datavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.views.discrete.SpreadsheetTheme;
import org.testng.annotations.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;

/**
 * Glyphs are drawn in code in the colour of what they are on, inside their square, so no image files are needed.
 */
public class GlyphTest {

    private static final int SIZE = 36;

    @Test
    public void everyGlyphDrawsInsideItsSquare() {
        for (Glyph.Shape shape : Glyph.Shape.values()) {
            for (Shape line : Glyph.lines(shape)) {
                Rectangle bounds = line.getBounds();
                assertTrue(bounds.x >= 0 && bounds.y >= 0 && bounds.getMaxX() <= Glyph.GRID
                        && bounds.getMaxY() <= Glyph.GRID, shape + " " + bounds);
            }
            JLabel label = new JLabel();
            label.setForeground(Color.BLUE);
            BufferedImage image = paint(Glyph.of(shape, SIZE), label);
            assertTrue(count(image, Color.BLUE.getRGB()) > 0, shape + " draws nothing");
        }
    }

    @Test
    public void aGlyphTakesTheColourOfItsComponent() {
        JLabel label = new JLabel();
        label.setForeground(Color.MAGENTA);
        BufferedImage image = paint(Glyph.of(Glyph.Shape.STOP, SIZE), label);
        assertTrue(count(image, Color.MAGENTA.getRGB()) > 0);
    }

    @Test
    public void aGlyphGivenAColourKeepsIt() {
        JLabel label = new JLabel();
        label.setForeground(Color.MAGENTA);
        Color warning = SpreadsheetTheme.PALETTE.warning();
        BufferedImage image = paint(new Glyph(Glyph.Shape.WARNING, SIZE, warning), label);
        assertTrue(count(image, warning.getRGB()) > 0);
        assertEquals(count(image, Color.MAGENTA.getRGB()), 0);
    }

    @Test
    public void aPressedKeyShowsTheAccentAndADisabledOneTheOrdinal() {
        Color accent = SpreadsheetTheme.PALETTE.accent();
        JButton pressed = new JButton();
        pressed.getModel().setArmed(true);
        pressed.getModel().setPressed(true);
        assertTrue(count(paint(Glyph.of(Glyph.Shape.PLAY, SIZE), pressed), accent.getRGB()) > 0);

        Color ordinal = SpreadsheetTheme.PALETTE.ordinal();
        JButton disabled = new JButton();
        disabled.setEnabled(false);
        assertTrue(count(paint(Glyph.of(Glyph.Shape.PLAY, SIZE), disabled), ordinal.getRGB()) > 0);
    }

    @Test
    public void aGlyphIsAsLargeAsAsked() {
        Glyph glyph = Glyph.of(Glyph.Shape.FIND, 22);
        assertEquals(glyph.getIconWidth(), 22);
        assertEquals(glyph.getIconHeight(), 22);
    }

    @Test
    public void onlyThePointCellAndWarningHaveDots() {
        for (Glyph.Shape shape : Glyph.Shape.values()) {
            boolean dotted = shape == Glyph.Shape.POINT_CELL || shape == Glyph.Shape.WARNING;
            assertEquals(!Glyph.dots(shape).isEmpty(), dotted, shape.toString());
        }
        assertFalse(Glyph.lines(Glyph.Shape.WARNING).isEmpty());
    }

    /** Paints the glyph on a transparent square, as the component it is on would. */
    private static BufferedImage paint(final Glyph glyph, final Component on) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        glyph.paintIcon(on, g, 0, 0);
        g.dispose();
        return image;
    }

    /** Counts the pixels drawn at full strength in a colour, leaving out antialiased edges. */
    private static int count(final BufferedImage image, final int rgb) {
        int n = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                if ((pixel >>> 24) == 0xff && (pixel & 0xffffff) == (rgb & 0xffffff)) {
                    n++;
                }
            }
        }
        return n;
    }
}
