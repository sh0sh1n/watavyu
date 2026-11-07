package com.sh0sh1n.watavyu;

import com.sh0sh1n.watavyu.controllers.project.ProjectController;
import com.sh0sh1n.watavyu.models.db.DataStore;
import com.sh0sh1n.watavyu.models.project.Project;
import com.sh0sh1n.watavyu.views.MainWindow;
import com.sh0sh1n.watavyu.views.VideoController;

import javax.swing.JFrame;

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

    /**
     * Fills a window with the main window's contents, as the app does at launch before showing it. Runs on the EDT.
     *
     * @param frame the window
     * @return the main window's contents, with one untitled spreadsheet
     */
    public static MainWindow mainWindow(final JFrame frame) {
        return new MainWindow(new Watavyu(), frame);
    }
}
