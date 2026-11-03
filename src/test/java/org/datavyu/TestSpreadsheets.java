package org.datavyu;

import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.project.Project;
import org.datavyu.views.VideoController;

/** Spreadsheets for tests, wired as a tab wires its own. */
public final class TestSpreadsheets {

    private TestSpreadsheets() {
    }

    /**
     * Makes a spreadsheet holding a data store, with its playback, as a tab has it before its view is made. Its
     * controls are Swing components, so this runs on the EDT.
     *
     * @param dataStore the spreadsheet's data
     * @return the spreadsheet
     */
    public static ProjectController withPlayback(final DataStore dataStore) {
        ProjectController spreadsheet = new ProjectController(new Project(), dataStore);
        spreadsheet.setVideoController(new VideoController(spreadsheet, null));
        return spreadsheet;
    }
}
