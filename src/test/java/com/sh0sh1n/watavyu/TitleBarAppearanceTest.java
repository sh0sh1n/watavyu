package com.sh0sh1n.watavyu;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

/**
 * Window title bars follow the Mac's appearance. AWT reads the property once, when it starts, and loading
 * {@link Watavyu} starts it by making the main frame, so the class sets the property as it loads, not in {@code main}.
 */
public class TitleBarAppearanceTest {

    @Test
    public void loadingTheApplicationSetsTheTitleBarAppearance() throws Exception {
        Class.forName(Watavyu.class.getName());

        assertEquals(System.getProperty(Watavyu.TITLE_BAR_APPEARANCE), "system");
    }
}
