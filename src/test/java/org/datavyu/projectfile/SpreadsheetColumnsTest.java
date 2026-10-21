package org.datavyu.projectfile;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.Variable;
import org.datavyu.models.db.MediaClip;
import org.datavyu.projectfile.ProjectDocument.AnnotationCell;
import org.datavyu.projectfile.ProjectDocument.AnnotationColumn;
import org.datavyu.projectfile.ProjectDocument.Clip;
import org.datavyu.projectfile.ProjectDocument.Column;
import org.datavyu.projectfile.ProjectDocument.MediaColumn;
import org.datavyu.projectfile.ProjectDocument.Source;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;

public class SpreadsheetColumnsTest {

    private static DataStore spreadsheet() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        // The spreadsheet panel numbers columns as it shows them.
        Variable text = dataStore.createVariable("Notes");
        text.getRootNode().childArguments.get(0).name = "note";
        text.setOrderIndex(0);
        Cell later = text.createCell();
        later.setOnset(5_000);
        later.setOffset(6_000);
        later.setMatrixValue(0, "second");
        Cell earlier = text.createCell();
        earlier.setOnset(1_000);
        earlier.setOffset(1_000);
        earlier.setMatrixValue(0, "first, (with) \"quotes\"");

        Variable matrix = dataStore.createVariable("Mother");
        matrix.setOrderIndex(1);
        matrix.addArgument();
        Cell cell = matrix.createCell();
        cell.setOnset(2_000);
        cell.setOffset(3_000);
        cell.setMatrixValue(1, "happy");

        Variable hidden = dataStore.createVariable("Hidden");
        hidden.setOrderIndex(2);
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
        Variable timing = dataStore.createVariable("Timing");
        timing.removeArgument("code01");
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

        assertTrue(dataStore.getVariable("2nd pass (mother)") != null);
    }

    private static final Path FOLDER = Path.of("/Users/someone/study");

    /** Media and annotation columns open in the file's order, which is the spreadsheet's, and save the same. */
    @Test
    public void mediaColumnsOpenAndSaveInTheFilesOrder() throws Exception {
        List<Column> columns = List.of(
            new AnnotationColumn("notes", List.of("code01"), false, List.of()),
            new MediaColumn("media_2", true, true, List.of(
                new Clip(-2_500, 0.7, new Source("media/cam 1.mp4", 60_000, 29.97, 1920, 1080)))),
            new MediaColumn("media_1", false, false, List.of(
                new Clip(1_000, 1, new Source("/Volumes/Share/room.mp4", 5_000, 25, 640, 480)))));
        DataStore dataStore = DataStoreFactory.newDataStore();

        SpreadsheetColumns.addColumns(dataStore, columns, FOLDER);

        Variable media = dataStore.getVariable("media_2");
        assertTrue(media.isMedia());
        assertTrue(media.isLocked());
        assertTrue(media.isHidden());
        Cell clip = media.getCells().get(0);
        assertEquals(clip.getClip(),
            new MediaClip("/Users/someone/study/media/cam 1.mp4", 60_000, 29.97, 1920, 1080, 0.7));
        assertEquals(clip.getOffset(), 57_500L);
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
        dataStore.createMediaVariable("media_1").createClip(1_000, MediaClip.of("/Users/someone/study/cam.mp4"));

        List<Column> columns = SpreadsheetColumns.columns(dataStore, FOLDER);

        assertEquals(columns, List.of(new MediaColumn("media_1", false, false, List.of(
            new Clip(1_000, 1, new Source("cam.mp4", 0, 0, 0, 0))))));
        ProjectJson.toJson(new ProjectDocument(columns));
    }

    @Test
    public void anAnnotationCellHasNoClip() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        SpreadsheetColumns.addColumns(dataStore, List.of(new AnnotationColumn("notes", List.of("x"), false,
            List.of(new AnnotationCell(0, 0, List.of("a"))))), FOLDER);

        assertNull(dataStore.getVariable("notes").getCells().get(0).getClip());
    }
}
