package com.sh0sh1n.watavyu.controllers;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.controllers.project.ProjectController;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.Project;
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
        spreadsheet.getDataStore().createColumn(column);
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
    public void aSpreadsheetWithoutUnsavedChangesHasNoAutosave() throws Exception {
        ProjectController spreadsheet = spreadsheet("study", "gaze");
        AutoSaveController autoSave = new AutoSaveController(spreadsheet, 1);
        try {
            autoSave.save();
            File unsaved = autoSave.getFile();
            assertTrue(unsaved.exists());

            // As after saving the project.
            spreadsheet.markProjectAsUnchanged();
            spreadsheet.getDataStore().markAsUnchanged();
            autoSave.save();

            assertFalse(unsaved.exists());
            assertNull(autoSave.getFile());
        } finally {
            autoSave.discard();
        }
    }

    @Test
    public void savingTheProjectDeletesItsAutosaveAtOnce() throws Exception {
        ProjectController spreadsheet = spreadsheet("study", "gaze");
        spreadsheet.startAutoSave(1);
        try {
            spreadsheet.getAutoSave().save();
            File unsaved = spreadsheet.getAutoSave().getFile();
            assertTrue(unsaved.exists());

            spreadsheet.markSaved();

            assertFalse(unsaved.exists());
            assertFalse(spreadsheet.isChanged());
        } finally {
            spreadsheet.close();
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void anIntervalUnderAMinuteIsRefused() throws Exception {
        new AutoSaveController(spreadsheet("study", "gaze"), 0);
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
