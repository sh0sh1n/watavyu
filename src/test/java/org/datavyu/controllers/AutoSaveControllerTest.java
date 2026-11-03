package org.datavyu.controllers;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.project.Project;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;

/**
 * Every open spreadsheet is autosaved on its own, so each can be recovered after a crash, and closing one deletes only
 * its own autosave (B18).
 */
public class AutoSaveControllerTest {

    private static ProjectController spreadsheet(final String name, final String column) throws Exception {
        ProjectController spreadsheet = new ProjectController(new Project(), DataStoreFactory.newDataStore());
        spreadsheet.setProjectName(name);
        spreadsheet.getDataStore().createVariable(column);
        return spreadsheet;
    }

    @Test
    public void eachSpreadsheetIsSavedToItsOwnFile() throws Exception {
        AutoSaveController first = new AutoSaveController(spreadsheet("first", "gaze"), 1);
        AutoSaveController second = new AutoSaveController(spreadsheet("second", "speech"), 1);
        try {
            first.save();
            second.save();

            assertNotEquals(first.getFile(), second.getFile());
            String firstSaved = Files.readString(first.getFile().toPath());
            String secondSaved = Files.readString(second.getFile().toPath());
            assertTrue(firstSaved.contains("\"gaze\""), firstSaved);
            assertFalse(firstSaved.contains("\"speech\""), firstSaved);
            assertTrue(secondSaved.contains("\"speech\""), secondSaved);
        } finally {
            first.discard();
            second.discard();
        }
    }

    @Test
    public void aNewAutosaveReplacesTheLast() throws Exception {
        AutoSaveController autoSave = new AutoSaveController(spreadsheet("study", "gaze"), 1);
        try {
            autoSave.save();
            File last = autoSave.getFile();

            autoSave.save();

            assertFalse(last.exists());
            assertTrue(autoSave.getFile().exists());
        } finally {
            autoSave.discard();
        }
    }

    @Test
    public void discardingDeletesOnlyItsOwnAutosave() throws Exception {
        AutoSaveController closed = new AutoSaveController(spreadsheet("first", "gaze"), 1);
        AutoSaveController open = new AutoSaveController(spreadsheet("second", "speech"), 1);
        try {
            closed.save();
            open.save();
            File closedFile = closed.getFile();

            closed.discard();

            assertFalse(closedFile.exists());
            assertNull(closed.getFile());
            assertTrue(open.getFile().exists());
        } finally {
            open.discard();
        }
    }
}
