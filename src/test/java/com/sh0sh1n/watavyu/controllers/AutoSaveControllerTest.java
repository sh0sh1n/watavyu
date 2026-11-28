package com.sh0sh1n.watavyu.controllers;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.Project;
import com.sh0sh1n.watavyu.projectfile.ProjectFileFilter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Every open spreadsheet with unsaved changes has one autosave of its own, replaced in place and written only when
 * what it would hold differs from what it holds, so each can be recovered after a crash; saving or closing one deletes
 * only its own (B18).
 */
public class AutoSaveControllerTest {

    private Path folder;

    @BeforeMethod
    public void setUp() throws IOException {
        folder = Files.createTempDirectory("autosaves");
    }

    private static ProjectController spreadsheet(final String name, final String column) throws Exception {
        ProjectController spreadsheet = new ProjectController(new Project(), DataStoreFactory.newDataStore());
        spreadsheet.setProjectName(name);
        spreadsheet.getDataStore().createColumn(column);
        return spreadsheet;
    }

    private AutoSaveController autoSave(final ProjectController spreadsheet) {
        return new AutoSaveController(spreadsheet, 1, folder);
    }

    private List<Path> projectFiles() throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.toString().endsWith(".watavyu.json")).toList();
        }
    }

    @Test
    public void autosavesAreKeptInTheApplicationsFolderNotTheTemporaryOne() {
        Path folder = AutoSaveController.defaultFolder();

        assertEquals(folder.getFileName().toString(), "Autosaves");
        assertFalse(folder.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath()), folder.toString());
    }

    @Test
    public void eachSpreadsheetIsSavedToItsOwnFile() throws Exception {
        AutoSaveController first = autoSave(spreadsheet("first", "gaze"));
        AutoSaveController second = autoSave(spreadsheet("second", "speech"));
        try {
            first.save();
            second.save();

            assertNotEquals(first.getAutosave().getFile(), second.getAutosave().getFile());
            String firstSaved = Files.readString(first.getAutosave().getFile());
            String secondSaved = Files.readString(second.getAutosave().getFile());
            assertTrue(firstSaved.contains("\"gaze\""), firstSaved);
            assertFalse(firstSaved.contains("\"speech\""), firstSaved);
            assertTrue(secondSaved.contains("\"speech\""), secondSaved);
        } finally {
            first.discard();
            second.discard();
        }
    }

    @Test
    public void aSpreadsheetKeepsOneAutosaveReplacedInPlace() throws Exception {
        ProjectController spreadsheet = spreadsheet("study", "gaze");
        AutoSaveController autoSave = autoSave(spreadsheet);
        try {
            autoSave.save();
            Path file = autoSave.getAutosave().getFile();

            spreadsheet.getDataStore().createColumn("speech");
            autoSave.save();

            assertEquals(projectFiles(), List.of(file));
            assertTrue(Files.readString(file).contains("\"speech\""));
        } finally {
            autoSave.discard();
        }
    }

    @Test
    public void anAutosaveIsWrittenOnlyWhenItsWorkChanged() throws Exception {
        ProjectController spreadsheet = spreadsheet("study", "gaze");
        AutoSaveController autoSave = autoSave(spreadsheet);
        try {
            autoSave.save();
            Path file = autoSave.getAutosave().getFile();
            // Marks the file, so writing it again would show.
            Files.writeString(file, "not written again");

            autoSave.save();
            assertEquals(Files.readString(file), "not written again");

            spreadsheet.getDataStore().createColumn("speech");
            autoSave.save();
            assertTrue(Files.readString(file).contains("\"speech\""));
        } finally {
            autoSave.discard();
        }
    }

    @Test
    public void theAutosaveSaysWhichProjectFileTheWorkCameFrom() throws Exception {
        ProjectController spreadsheet = spreadsheet("study", "gaze");
        File projectFile = new File("/Users/coder/study.watavyu.json");
        spreadsheet.setProjectDirectory(projectFile.getParent());
        spreadsheet.setDatabaseFileName(projectFile.getName());
        spreadsheet.setLastSaveOption(ProjectFileFilter.INSTANCE);
        spreadsheet.setSavedFingerprint("abc123");
        AutoSaveController autoSave = autoSave(spreadsheet);
        try {
            autoSave.save();

            assertEquals(autoSave.getAutosave().getOrigin(), new Autosave.Origin(projectFile, "abc123"));
        } finally {
            autoSave.discard();
        }
    }

    @Test
    public void untitledWorkComesFromNoProjectFile() throws Exception {
        AutoSaveController autoSave = autoSave(spreadsheet("study", "gaze"));
        try {
            autoSave.save();

            assertNull(autoSave.getAutosave().getOrigin());
        } finally {
            autoSave.discard();
        }
    }

    @Test
    public void aSpreadsheetWithoutUnsavedChangesHasNoAutosave() throws Exception {
        ProjectController spreadsheet = spreadsheet("study", "gaze");
        AutoSaveController autoSave = autoSave(spreadsheet);
        try {
            autoSave.save();
            Path unsaved = autoSave.getAutosave().getFile();
            assertTrue(Files.exists(unsaved));

            // As after saving the project.
            spreadsheet.markProjectAsUnchanged();
            spreadsheet.getDataStore().markAsUnchanged();
            autoSave.save();

            assertFalse(Files.exists(unsaved));
            assertNull(autoSave.getAutosave());
        } finally {
            autoSave.discard();
        }
    }

    @Test
    public void savingTheProjectDeletesItsAutosaveAtOnce() throws Exception {
        ProjectController spreadsheet = spreadsheet("study", "gaze");
        spreadsheet.startAutoSave(1, folder);
        try {
            spreadsheet.getAutoSave().save();
            Path unsaved = spreadsheet.getAutoSave().getAutosave().getFile();
            assertTrue(Files.exists(unsaved));

            spreadsheet.markSaved("abc123");

            assertFalse(Files.exists(unsaved));
            assertFalse(spreadsheet.isChanged());
            assertEquals(spreadsheet.getSavedFingerprint(), "abc123");
        } finally {
            spreadsheet.close();
        }
    }

    @Test
    public void anAdoptedAutosaveIsReplacedRatherThanASecondMade() throws Exception {
        ProjectController crashed = spreadsheet("study", "gaze");
        AutoSaveController before = autoSave(crashed);
        before.save();
        Autosave left = before.getAutosave();
        left.release();
        Autosave recovered = Autosave.claimAbandoned(folder).get(0);

        ProjectController spreadsheet = spreadsheet("study", "gaze");
        AutoSaveController autoSave = autoSave(spreadsheet);
        try {
            autoSave.adopt(recovered);
            spreadsheet.getDataStore().createColumn("speech");
            autoSave.save();

            assertSame(autoSave.getAutosave(), recovered);
            assertEquals(projectFiles(), List.of(left.getFile()));
            assertTrue(Files.readString(left.getFile()).contains("\"speech\""));
        } finally {
            autoSave.discard();
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void anIntervalUnderAMinuteIsRefused() throws Exception {
        new AutoSaveController(spreadsheet("study", "gaze"), 0, folder);
    }

    @Test
    public void discardingDeletesOnlyItsOwnAutosave() throws Exception {
        AutoSaveController closed = autoSave(spreadsheet("first", "gaze"));
        AutoSaveController open = autoSave(spreadsheet("second", "speech"));
        try {
            closed.save();
            open.save();
            Path closedFile = closed.getAutosave().getFile();

            closed.discard();

            assertFalse(Files.exists(closedFile));
            assertNull(closed.getAutosave());
            assertTrue(Files.exists(open.getAutosave().getFile()));
        } finally {
            open.discard();
        }
    }
}
