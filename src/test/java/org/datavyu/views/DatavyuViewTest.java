package org.datavyu.views;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class DatavyuViewTest {

    @Test
    public void theTitleNamesTheApplicationAndTheProject() {
        assertEquals(DatavyuView.windowTitle("Watavyu", "study.opf  *", false), "Watavyu - study.opf  *");
    }

    @Test
    public void theTitleShowsQuickKeyMode() {
        assertEquals(DatavyuView.windowTitle("Watavyu", "study.opf  ", true), "Watavyu - study.opf   <QUICK KEY MODE>");
    }
}
