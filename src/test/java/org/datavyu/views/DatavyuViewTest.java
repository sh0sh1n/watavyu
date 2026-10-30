package org.datavyu.views;

import static org.testng.Assert.assertEquals;

import org.datavyu.views.discrete.layouts.SheetLayoutFactory;
import org.datavyu.views.discrete.layouts.SheetLayoutFactory.SheetLayoutType;
import org.testng.annotations.Test;

public class DatavyuViewTest {

    /** F4: spreadsheets open in temporal layout, so media clips line up with the cells coded from them. */
    @Test
    public void spreadsheetsOpenInTemporalLayout() {
        assertEquals(SheetLayoutFactory.DEFAULT_LAYOUT, SheetLayoutType.WeakTemporal);
    }

    @Test
    public void theTitleNamesTheApplicationAndTheProject() {
        assertEquals(DatavyuView.windowTitle("Watavyu", "study.watavyu.json  *", false), "Watavyu - study.watavyu.json  *");
    }

    @Test
    public void theTitleShowsQuickKeyMode() {
        assertEquals(DatavyuView.windowTitle("Watavyu", "study.watavyu.json  ", true), "Watavyu - study.watavyu.json   <QUICK KEY MODE>");
    }

    /** B8: a recovered project is named after the project that was autosaved. */
    @Test
    public void aRecoveredProjectIsNamedAfterTheOriginal() {
        assertEquals(DatavyuView.recoveredName("~study_12005522346316252883.watavyu.json"), "study");
        assertEquals(DatavyuView.recoveredName("~session_04_5415962468724307690.watavyu.json"), "session_04");
        assertEquals(DatavyuView.recoveredName("~noname_42.watavyu.json"), "noname");
    }
}
