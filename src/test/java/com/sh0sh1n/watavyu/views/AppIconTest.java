package com.sh0sh1n.watavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * The app icon is drawn in code, so no image file is needed: the play mark in the accent between the onset and offset
 * brackets, on a dark tile inside Apple's icon grid, at any size.
 */
public class AppIconTest {

    private static final int SIZE = 1024;

    @Test
    public void theIconIsAsLargeAsAsked() {
        for (int size : new int[] {16, 32, 128, 1024}) {
            BufferedImage image = AppIcon.image(size);
            assertEquals(image.getWidth(), size);
            assertEquals(image.getHeight(), size);
        }
    }

    @Test
    public void thePlayMarkIsInTheAccentBetweenTheBrackets() {
        BufferedImage image = AppIcon.image(SIZE);
        int middle = (int) AppIcon.CENTRE;
        assertEquals(colourAt(image, (int) AppIcon.PLAY_X, middle), AppIcon.PALETTE.accent());
        int bracketMiddle = (int) ((AppIcon.BRACKET_TOP + AppIcon.BRACKET_BOTTOM) / 2);
        assertEquals(colourAt(image, (int) AppIcon.LEFT_STEM, bracketMiddle), AppIcon.PALETTE.text());
        assertEquals(colourAt(image, (int) AppIcon.RIGHT_STEM, bracketMiddle), AppIcon.PALETTE.text());
    }

    @Test
    public void theTileIsDarkWhateverTheAppearance() {
        BufferedImage image = AppIcon.image(SIZE);
        Color ground = colourAt(image, (int) AppIcon.CENTRE, (int) (AppIcon.CENTRE - AppIcon.TILE_RADIUS / 2));
        assertEquals(ground.getAlpha(), 255);
        assertTrue(ground.getRed() + ground.getGreen() + ground.getBlue() < 3 * 64, ground.toString());
    }

    @Test
    public void theTileKeepsInsideAppleIconGridWithClearCorners() {
        Rectangle tile = AppIcon.tile().getBounds();
        assertTrue(tile.x >= 100 && tile.y >= 100 && tile.getMaxX() <= 924 && tile.getMaxY() <= 924, tile.toString());

        BufferedImage image = AppIcon.image(SIZE);
        for (int[] corner : new int[][] {{0, 0}, {SIZE - 1, 0}, {0, SIZE - 1}, {SIZE - 1, SIZE - 1}, {120, 120}}) {
            assertEquals(new Color(image.getRGB(corner[0], corner[1]), true).getAlpha(), 0,
                    corner[0] + "," + corner[1]);
        }
    }

    private static Color colourAt(final BufferedImage image, final int x, final int y) {
        return new Color(image.getRGB(x, y), true);
    }
}
