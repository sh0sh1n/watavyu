package org.datavyu.views;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.testng.annotations.Test;

public class WindowPlacementTest {

    private static final List<Rectangle> LAPTOP = Collections.singletonList(new Rectangle(0, 0, 1728, 1117));

    private static final List<Rectangle> LAPTOP_AND_EXTERNAL_DISPLAY_ON_THE_LEFT =
        Arrays.asList(new Rectangle(0, 0, 1728, 1117), new Rectangle(-2560, 0, 2560, 1440));

    @Test
    public void aWindowOnTheScreenIsPutBack() {
        assertTrue(WindowPlacement.isOnScreen(new Rectangle(100, 100, 800, 600), LAPTOP));
    }

    @Test
    public void aWindowMostlyOffTheEdgeIsPutBackIfEnoughShows() {
        assertTrue(WindowPlacement.isOnScreen(new Rectangle(1678, 100, 800, 600), LAPTOP));
        assertFalse(WindowPlacement.isOnScreen(new Rectangle(1679, 100, 800, 600), LAPTOP));
    }

    @Test
    public void aWindowOnADisconnectedDisplayIsNotPutBack() {
        Rectangle onExternalDisplay = new Rectangle(-2000, 200, 800, 600);
        assertTrue(WindowPlacement.isOnScreen(onExternalDisplay, LAPTOP_AND_EXTERNAL_DISPLAY_ON_THE_LEFT));
        assertFalse(WindowPlacement.isOnScreen(onExternalDisplay, LAPTOP));
    }

    @Test
    public void aWindowAcrossTwoDisplaysIsPutBackIfEnoughIsOnOne() {
        assertTrue(WindowPlacement.isOnScreen(new Rectangle(-400, 100, 800, 600),
            LAPTOP_AND_EXTERNAL_DISPLAY_ON_THE_LEFT));
    }
}
