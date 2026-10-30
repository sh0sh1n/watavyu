package org.datavyu;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

/**
 * Window title bars follow the Mac's appearance. AWT reads the property once, when it starts, and loading
 * {@link Datavyu} starts it by making the main frame, so the class sets the property as it loads, not in {@code main}.
 */
public class TitleBarAppearanceTest {

    @Test
    public void loadingTheApplicationSetsTheTitleBarAppearance() throws Exception {
        Class.forName(Datavyu.class.getName());

        assertEquals(System.getProperty(Datavyu.TITLE_BAR_APPEARANCE), "system");
    }
}
