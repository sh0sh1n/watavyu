package com.sh0sh1n.watavyu.projectfile;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.MediaClip;
import com.sh0sh1n.watavyu.projectfile.ProjectDocument.AnnotationCell;
import com.sh0sh1n.watavyu.projectfile.ProjectDocument.AnnotationColumn;
import com.sh0sh1n.watavyu.projectfile.ProjectDocument.Clip;
import com.sh0sh1n.watavyu.projectfile.ProjectDocument.MediaColumn;
import com.sh0sh1n.watavyu.projectfile.ProjectDocument.Source;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;

public class SpreadsheetColumnsTest {

    private static DataStore spreadsheet() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        Column text = dataStore.createColumn("Notes");
        text.renameCode(0, "note");
        Cell later = text.createCell();
        later.setOnset(5_000);
        later.setOffset(6_000);
        later.setValue(0, "second");
        Cell earlier = text.createCell();
        earlier.setOnset(1_000);
        earlier.setOffset(1_000);
        earlier.setValue(0, "first, (with) \"quotes\"");

        Column matrix = dataStore.createColumn("Mother");
        matrix.addCode();
        Cell cell = matrix.createCell();
        cell.setOnset(2_000);
        cell.setOffset(3_000);
        cell.setValue(1, "happy");

        Column hidden = dataStore.createColumn("Hidden");
        hidden.setHidden(true);
        return dataStore;
    }

    @Test
    public void columnsAreSavedInSpreadsheetOrderWithCellsInTimeOrder() throws Exception {
        List<AnnotationColumn> columns = SpreadsheetColumns.of(spreadsheet());

        assertEquals(columns, List.of(
            new AnnotationColumn("Notes", List.of("note"), false, List.of(
                new AnnotationCell(1_000, 1_000, List.of("first, (with) \"quotes\"")),
                new AnnotationCell(5_000, 6_000, List.of("second")))),
            new AnnotationColumn("Mother", List.of("code01", "code02"), false, List.of(
                new AnnotationCell(2_000, 3_000, List.of("", "happy")))),
            new AnnotationColumn("Hidden", List.of("code01"), true, List.of())));
    }

    @Test
    public void savedColumnsOpenAsTheSameColumns() throws Exception {
        List<AnnotationColumn> columns = SpreadsheetColumns.of(spreadsheet());
        DataStore reopened = DataStoreFactory.newDataStore();

        SpreadsheetColumns.addTo(reopened, columns);

        assertEquals(SpreadsheetColumns.of(reopened), columns);
    }

    @Test
    public void aColumnWithNoCodesKeepsItsTiming() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Column timing = dataStore.createColumn("Timing");
        timing.removeCode(0);
        Cell cell = timing.createCell();
        cell.setOnset(10);
        cell.setOffset(20);

        List<AnnotationColumn> columns = SpreadsheetColumns.of(dataStore);
        assertEquals(columns, List.of(
            new AnnotationColumn("Timing", List.of(), false, List.of(new AnnotationCell(10, 20, List.of())))));

        DataStore reopened = DataStoreFactory.newDataStore();
        SpreadsheetColumns.addTo(reopened, columns);
        assertEquals(SpreadsheetColumns.of(reopened), columns);
    }

    @Test
    public void namesFromOlderFilesAreKept() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        SpreadsheetColumns.addTo(dataStore, List.of(new AnnotationColumn("2nd pass (mother)", List.of("x"), false,
            List.of())));

        assertTrue(dataStore.getColumn("2nd pass (mother)") != null);
    }

    private static final Path FOLDER = Path.of("/Users/someone/study");

    /** A locked annotation column opens with its cells and values in, and locked; it saves locked. */
    @Test
    public void aLockedAnnotationColumnOpensWithItsCellsAndLocked() throws Exception {
        List<ProjectDocument.Column> columns = List.of(new AnnotationColumn("notes", List.of("mood"), false, true,
            List.of(new AnnotationCell(1_000, 2_000, List.of("calm")))));
        DataStore dataStore = DataStoreFactory.newDataStore();

        SpreadsheetColumns.addColumns(dataStore, columns, FOLDER);

        Column notes = dataStore.getColumn("notes");
        assertTrue(notes.isLocked());
        assertEquals(notes.getCells().get(0).getValue(0), "calm");
        assertEquals(notes.getCells().get(0).getOffset(), 2_000L);
        assertEquals(SpreadsheetColumns.columns(dataStore, FOLDER), columns);
    }

    /** Media and annotation columns open in the file's order, which is the spreadsheet's, and save the same. */
    @Test
    public void mediaColumnsOpenAndSaveInTheFilesOrder() throws Exception {
        List<ProjectDocument.Column> columns = List.of(
            new AnnotationColumn("notes", List.of("code01"), false, List.of()),
            new MediaColumn("media_2", true, true, List.of(
                new Clip(2_500, 0.7, new Source("media/cam 1.mp4", 60_000, 29.97, 1920, 1080)))),
            new MediaColumn("media_1", false, false, List.of(
                new Clip(1_000, 1, new Source("/Volumes/Share/room.mp4", 5_000, 25, 640, 480)))));
        DataStore dataStore = DataStoreFactory.newDataStore();

        SpreadsheetColumns.addColumns(dataStore, columns, FOLDER);

        Column media = dataStore.getColumn("media_2");
        assertTrue(media.isMedia());
        assertTrue(media.isLocked());
        assertTrue(media.isHidden());
        Cell clip = media.getCells().get(0);
        assertEquals(clip.getClip(),
            new MediaClip("/Users/someone/study/media/cam 1.mp4", 60_000, 29.97, 1920, 1080, 0.7));
        assertEquals(clip.getOffset(), 62_500L);
        assertEquals(SpreadsheetColumns.columns(dataStore, FOLDER), columns);
        assertEquals(SpreadsheetColumns.of(dataStore), List.of(columns.get(0)));
    }

    /** The spreadsheet holds media paths absolute, so a project saved to another folder still finds its media. */
    @Test
    public void mediaPathsFollowTheFolderSavedTo() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        SpreadsheetColumns.addColumns(dataStore, List.of(new MediaColumn("media_1", false, false, List.of(
            new Clip(0, 1, new Source("media/cam.mp4", 5_000, 25, 640, 480))))), FOLDER);

        MediaColumn saved = (MediaColumn) SpreadsheetColumns.columns(dataStore, Path.of("/Users/someone/copy")).get(0);

        assertEquals(saved.clips().get(0).source().file(), "/Users/someone/study/media/cam.mp4");
    }

    /** Saving never waits for media: a clip whose file has not opened is saved with what is known of it. */
    @Test
    public void aClipWhoseFileHasNotOpenedIsSaved() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        dataStore.createMediaColumn("media_1").createClip(1_000, MediaClip.of("/Users/someone/study/cam.mp4"));

        List<ProjectDocument.Column> columns = SpreadsheetColumns.columns(dataStore, FOLDER);

        assertEquals(columns, List.of(new MediaColumn("media_1", false, false, List.of(
            new Clip(1_000, 1, new Source("cam.mp4", 0, 0, 0, 0))))));
        ProjectJson.toJson(new ProjectDocument(columns));
    }

    @Test
    public void anAnnotationCellHasNoClip() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        SpreadsheetColumns.addColumns(dataStore, List.of(new AnnotationColumn("notes", List.of("x"), false,
            List.of(new AnnotationCell(0, 0, List.of("a"))))), FOLDER);

        assertNull(dataStore.getColumn("notes").getCells().get(0).getClip());
    }
}
