package org.datavyu.controllers.project;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.MediaClip;
import org.datavyu.models.db.Variable;
import org.datavyu.models.db.VariableListener;
import org.datavyu.models.project.Project;
import org.datavyu.util.ArrayDirection;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Each cell command is the one path for its user action: it leaves locked columns and clips alone, changes the cells
 * and records what it did, so undo puts it back.
 */
public class CellCommandsTest {

    private static final MediaClip CAM = new MediaClip("/study/cam.mp4", 10_000, 30, 1280, 720, 1);

    private DataStore dataStore;
    private ProjectController spreadsheet;
    private CellCommands cells;
    private Variable gaze;
    private Variable speech;

    @BeforeMethod
    public void setUp() throws Exception {
        dataStore = DataStoreFactory.newDataStore();
        spreadsheet = new ProjectController(new Project(), dataStore);
        cells = spreadsheet.cells();
        gaze = dataStore.createVariable("gaze");
        speech = dataStore.createVariable("speech");
        // A new column is selected; each test selects what it needs.
        dataStore.deselectAll();
    }

    private void undo() {
        spreadsheet.undo();
    }

    // New Cell, from the menu

    @Test
    public void newCellGoesInTheColumnLastGivenACell() {
        spreadsheet.setLastCreatedVariable(speech);
        gaze.setSelected(true);

        Cell cell = cells.newCell(2_500);

        assertSame(cell.getVariable(), speech);
        assertEquals(cell.getOnset(), 2_500);
        assertEquals(cell.getOffset(), 2_500);
        assertTrue(cell.isHighlighted());
        assertFalse(gaze.isSelected(), "the selection is cleared");
        assertSame(spreadsheet.getLastCreatedCell(), cell);

        undo();
        assertTrue(speech.getCells().isEmpty());
    }

    @Test
    public void newCellNeedsAColumnLastGivenACell() {
        assertNull(cells.newCell(2_500));
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    @Test
    public void aLockedColumnRefusesANewCellAndRecordsNothing() {
        spreadsheet.setLastCreatedVariable(speech);
        speech.setLocked(true);

        assertNull(cells.newCell(2_500));
        assertTrue(speech.getCells().isEmpty());
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    /** B22: a hidden column takes no new cell, though it was the one last given a cell. */
    @Test
    public void aHiddenColumnTakesNoNewCell() {
        spreadsheet.setLastCreatedVariable(speech);
        spreadsheet.columns().hide(List.of(speech));

        assertNull(cells.newCell(2_500));
        assertNull(cells.newCellInSelectedColumn(2_500));
        assertNull(cells.newCellTyped(speech, 'a', 2_500));
        assertTrue(speech.getCells().isEmpty());
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    /** The menu's New Cell can run when the column last given a cell is shown, in the spreadsheet and not media. */
    @Test
    public void newCellHasAColumnOnlyWhileTheLastGivenACellIsShown() throws Exception {
        assertFalse(cells.hasColumnForNewCell());
        spreadsheet.setLastCreatedVariable(speech);
        assertTrue(cells.hasColumnForNewCell());
        speech.setLocked(true);
        assertTrue(cells.hasColumnForNewCell(), "a locked column shows its lock");

        spreadsheet.columns().hide(List.of(speech));
        assertFalse(cells.hasColumnForNewCell());
        spreadsheet.columns().show(speech);
        dataStore.removeVariable(speech);
        assertFalse(cells.hasColumnForNewCell());
        spreadsheet.setLastCreatedVariable(dataStore.createMediaVariable("media_1"));
        assertFalse(cells.hasColumnForNewCell());
    }

    /** A hidden column is not selected, however it was hidden, as closing its media window hides it. */
    @Test
    public void aSelectedColumnHiddenSinceIsPassedOver() {
        gaze.setSelected(true);
        speech.setSelected(true);
        gaze.setHidden(true);

        assertSame(cells.newCellInSelectedColumn(1_000).getVariable(), speech);
    }

    // New Cell, from the keypad and drawer

    @Test
    public void theKeypadsNewCellPrefersTheSelectedColumn() {
        spreadsheet.setLastCreatedVariable(speech);
        gaze.setSelected(true);

        assertSame(cells.newCellInSelectedColumn(1_000).getVariable(), gaze);
        // With no column selected, it goes where New Cell would.
        assertSame(cells.newCellInSelectedColumn(2_000).getVariable(), gaze);
    }

    @Test
    public void theFirstSelectedColumnIsTheLeftmost() {
        gaze.setSelected(true);
        speech.setSelected(true);
        assertSame(cells.newCellInSelectedColumn(1_000).getVariable(), gaze);

        dataStore.moveVariable(speech, 0);
        gaze.setSelected(true);
        speech.setSelected(true);
        assertSame(cells.newCellInSelectedColumn(2_000).getVariable(), speech);
    }

    /** A deleted column is forgotten as the one last given a cell. */
    @Test
    public void aDeletedColumnTakesNoNewCell() {
        spreadsheet.setLastCreatedVariable(speech);
        dataStore.removeVariable(speech);

        assertNull(cells.newCell(1_000));
        assertSame(cells.newCellClosingPrevious(2_000).getVariable(), gaze, "the first annotation column instead");
    }

    @Test
    public void undoingANewCellKeepsTheSelectionAndRedoingMakesItTheLastCreatedAgain() {
        spreadsheet.setLastCreatedVariable(gaze);
        Cell cell = cells.newCell(1_000);
        speech.setSelected(true);

        undo();
        assertTrue(speech.isSelected());

        spreadsheet.redo();
        assertSame(spreadsheet.getLastCreatedCell(), cell);
        assertEquals(gaze.getCells(), List.of(cell));
    }

    /** Cells of a deleted column, remembered as last selected, are neither closed nor timed. */
    @Test
    public void aDeletedColumnsCellsAreNotTimed() {
        Cell gone = speech.createPointCell(1_000);
        spreadsheet.setLastSelectedCell(gone);
        spreadsheet.setLastSelectedCells(List.of(gone));
        dataStore.removeVariable(speech);
        dataStore.deselectAll();

        cells.setOnsets(3_000);
        cells.newCellClosingPrevious(5_000);

        assertEquals(gone.getOnset(), 1_000);
        assertEquals(gone.getOffset(), 1_000);
    }

    @Test
    public void undoingANewCellForgetsIt() {
        spreadsheet.setLastCreatedVariable(gaze);
        Cell cell = cells.newCell(1_000);
        spreadsheet.setLastSelectedCell(cell);
        spreadsheet.setLastSelectedCells(List.of(cell));

        undo();

        assertNull(spreadsheet.getLastCreatedCell());
        assertNull(spreadsheet.getLastSelectedCell());
        assertTrue(spreadsheet.getLastSelectedCells().isEmpty());
    }

    @Test
    public void aSelectedMediaColumnTakesNoNewCell() throws Exception {
        Variable media = dataStore.createMediaVariable("media_1");
        dataStore.deselectAll();
        media.setSelected(true);
        spreadsheet.setLastCreatedVariable(speech);

        assertNull(cells.newCellInSelectedColumn(1_000));
        assertTrue(media.getCells().isEmpty());
    }

    // New Cell and Set Offset

    @Test
    public void newCellClosingPreviousClosesTheCellBeforeInTheSelectedColumn() {
        Cell earlier = gaze.createPointCell(1_000);
        Cell previous = gaze.createPointCell(3_000);
        Cell later = gaze.createPointCell(9_000);
        gaze.setSelected(true);

        Cell cell = cells.newCellClosingPrevious(5_000);

        assertEquals(previous.getOffset(), 4_999);
        assertEquals(earlier.getOffset(), 1_000);
        assertEquals(later.getOffset(), 9_000);
        assertSame(cell.getVariable(), gaze);
        assertEquals(cell.getOnset(), 5_000);

        // The new cell and the closing are undone one at a time.
        undo();
        assertEquals(gaze.getCells().size(), 3);
        assertEquals(previous.getOffset(), 4_999);
        undo();
        assertEquals(previous.getOffset(), 3_000);
    }

    @Test
    public void withNoCellBeforeTheTimeOnlyTheNewCellIsMade() {
        Cell later = gaze.createPointCell(9_000);
        gaze.setSelected(true);

        Cell cell = cells.newCellClosingPrevious(5_000);

        assertEquals(cell.getOnset(), 5_000);
        assertEquals(later.getOffset(), 9_000);
    }

    @Test
    public void amongCellsStartingTogetherTheLastSelectedIsClosed() {
        Cell first = gaze.createPointCell(3_000);
        Cell second = gaze.createPointCell(3_000);
        gaze.setSelected(true);
        spreadsheet.setLastSelectedCell(first);

        cells.newCellClosingPrevious(5_000);

        assertEquals(first.getOffset(), 4_999);
        assertEquals(second.getOffset(), 3_000);
    }

    @Test
    public void amongCellsStartingTogetherWithNoneSelectedTheLastIsClosed() {
        List<Cell> together = List.of(gaze.createPointCell(3_000), gaze.createPointCell(3_000));
        gaze.setSelected(true);

        cells.newCellClosingPrevious(5_000);

        List<Cell> temporal = gaze.getCellsTemporally();
        Cell last = temporal.get(1) == together.get(0) || temporal.get(1) == together.get(1) ? temporal.get(1) : null;
        assertEquals(last.getOffset(), 4_999);
        assertEquals(temporal.get(0).getOffset(), 3_000);
    }

    @Test
    public void withoutASelectedColumnTheLastSelectedCellsColumnIsClosed() {
        Cell previous = speech.createPointCell(3_000);
        spreadsheet.setLastSelectedCell(previous);
        previous.setSelected(true);

        Cell cell = cells.newCellClosingPrevious(5_000);

        assertEquals(previous.getOffset(), 4_999);
        assertSame(cell.getVariable(), speech, "the new cell goes in the selected cell's column");
    }

    @Test
    public void aLockedColumnIsNotClosedAndTakesNoNewCell() {
        Cell previous = gaze.createPointCell(3_000);
        gaze.setSelected(true);
        gaze.setLocked(true);

        assertNull(cells.newCellClosingPrevious(5_000));
        assertEquals(previous.getOffset(), 3_000);
        assertEquals(gaze.getCells().size(), 1);
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    /** B22: a cell last selected in a column since hidden is not closed, and the new cell goes in a shown column. */
    @Test
    public void aHiddenColumnIsNotClosedAndTakesNoNewCell() {
        Cell previous = gaze.createPointCell(3_000);
        spreadsheet.setLastSelectedCell(previous);
        spreadsheet.setLastCreatedVariable(gaze);
        spreadsheet.columns().hide(List.of(gaze));

        Cell cell = cells.newCellClosingPrevious(5_000);

        assertEquals(previous.getOffset(), 3_000);
        assertSame(cell.getVariable(), speech, "the first shown annotation column");
    }

    @Test
    public void withEveryAnnotationColumnHiddenNewCellAndSetOffsetMakesNothing() {
        spreadsheet.columns().hide(List.of(gaze, speech));

        assertNull(cells.newCellClosingPrevious(5_000));
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    @Test
    public void aClipIsNotClosedAndTheNewCellGoesInTheFirstAnnotationColumn() throws Exception {
        Variable media = dataStore.createMediaVariable("media_1");
        Cell clip = media.createClip(0, CAM);
        dataStore.deselectAll();
        media.setSelected(true);

        Cell cell = cells.newCellClosingPrevious(5_000);

        assertEquals(clip.getOffset(), 10_000);
        assertSame(cell.getVariable(), gaze);
    }

    // New Cell to Left and Right

    @Test
    public void newCellsBesideCopyTheSelectedCellsTimesIntoTheNextVisibleColumn() {
        Cell source = gaze.createCell();
        source.setOnset(1_000);
        source.setOffset(2_000);
        source.setSelected(true);

        List<Cell> made = cells.newCellsBeside(ArrayDirection.RIGHT);

        assertEquals(made.size(), 1);
        Cell cell = made.get(0);
        assertSame(cell.getVariable(), speech);
        assertEquals(cell.getOnset(), 1_000);
        assertEquals(cell.getOffset(), 2_000);
        assertTrue(cell.isHighlighted());

        undo();
        assertTrue(speech.getCells().isEmpty());
    }

    @Test
    public void newCellsBesideStopAtTheEdgeAndAtLockedAndMediaColumns() throws Exception {
        Cell source = gaze.createPointCell(1_000);
        source.setSelected(true);
        assertTrue(cells.newCellsBeside(ArrayDirection.LEFT).isEmpty(), "nothing is left of the first column");

        speech.setLocked(true);
        assertTrue(cells.newCellsBeside(ArrayDirection.RIGHT).isEmpty());

        Variable media = dataStore.createMediaVariable("media_1");
        dataStore.moveVariable(media, 0);
        assertTrue(cells.newCellsBeside(ArrayDirection.LEFT).isEmpty());
        assertTrue(media.getCells().isEmpty());
    }

    // Quick keys

    @Test
    public void aTypedKeyMakesACellHoldingIt() {
        Cell cell = cells.newCellTyped(gaze, 'k', 4_000);

        assertEquals(cell.getOnset(), 4_000);
        assertEquals(cell.getMatrixValue(0).toString(), "k");
        assertSame(spreadsheet.getLastCreatedVariable(), gaze);

        undo();
        assertTrue(gaze.getCells().isEmpty());
    }

    /** A column whose codes have all been deleted holds timing only, with nowhere for the key. */
    @Test
    public void aColumnWithNoCodesTakesNoTypedKey() {
        gaze.getRootNode().clearChildArguments();

        assertNull(cells.newCellTyped(gaze, 'k', 4_000));
        assertTrue(gaze.getCells().isEmpty());
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    @Test
    public void aLockedColumnWithNoCodesStillShowsItRefusesATypedKey() {
        VariableListener header = mock(VariableListener.class);
        gaze.addListener(header);
        gaze.getRootNode().clearChildArguments();
        gaze.setLocked(true);

        assertNull(cells.newCellTyped(gaze, 'k', 4_000));

        verify(header).editRefused();
    }

    @Test
    public void aLockedColumnRefusesATypedKey() {
        gaze.setLocked(true);

        assertNull(cells.newCellTyped(gaze, 'k', 4_000));
        assertTrue(gaze.getCells().isEmpty());
    }

    // Set Onset and Set Offset

    @Test
    public void settingTimesChangesTheSelectedCellsExceptInLockedColumns() {
        Cell open = gaze.createPointCell(1_000);
        Cell locked = speech.createPointCell(1_000);
        open.setSelected(true);
        locked.setSelected(true);
        speech.setLocked(true);

        cells.setOnsets(3_000);
        cells.setOffsets(4_000);

        assertEquals(open.getOnset(), 3_000);
        assertEquals(open.getOffset(), 4_000);
        assertEquals(locked.getOnset(), 1_000);
        assertEquals(locked.getOffset(), 1_000);

        undo();
        assertEquals(open.getOffset(), 1_000);
        undo();
        assertEquals(open.getOnset(), 1_000);
    }

    /** The view sorts a column's cells by time whenever it lays them out; undo still finds the cell it changed. */
    @Test
    public void undoingAnOnsetFindsItsCellAfterTheColumnIsSorted() {
        Cell moved = gaze.createPointCell(1_000);
        Cell other = gaze.createPointCell(3_000);
        dataStore.deselectAll();
        moved.setSelected(true);

        cells.setOnsets(5_000);
        gaze.getCellsTemporally();
        undo();

        assertEquals(moved.getOnset(), 1_000);
        assertEquals(other.getOnset(), 3_000);
    }

    /** Cells with the same times are each an edit of their own, not duplicates of one another. */
    @Test
    public void eachCellTimedIsAnUndoOfItsOwn() {
        Cell first = gaze.createPointCell(1_000);
        Cell second = speech.createPointCell(1_000);
        first.setSelected(true);
        second.setSelected(true);

        cells.setOnsets(3_000);
        undo();
        undo();

        assertEquals(first.getOnset(), 1_000);
        assertEquals(second.getOnset(), 1_000);
    }

    @Test
    public void aDeletedCellIsNoLongerTimedAsLastSelected() {
        Cell cell = gaze.createPointCell(1_000);
        dataStore.deselectAll();
        cell.setSelected(true);
        spreadsheet.setLastSelectedCells(List.of(cell));
        cells.deleteSelected();

        cells.setOnsets(3_000);

        undo();
        assertEquals(gaze.getCells(), List.of(cell), "only the delete was recorded");
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    @Test
    public void withNothingSelectedTheCellsLastSelectedAreTimed() {
        Cell cell = gaze.createPointCell(1_000);
        spreadsheet.setLastSelectedCells(List.of(cell));

        cells.setOnsets(3_000);

        assertEquals(cell.getOnset(), 3_000);
    }

    /** B22: the cells last selected in a column hidden since are not timed, as they cannot be seen. */
    @Test
    public void cellsLastSelectedInAHiddenColumnAreNotTimed() {
        Cell hidden = gaze.createPointCell(1_000);
        Cell shown = speech.createPointCell(1_000);
        spreadsheet.setLastSelectedCells(List.of(hidden, shown));
        spreadsheet.columns().hide(List.of(gaze));
        // A new cell starts selected; with nothing selected, the cells last selected are timed.
        shown.setSelected(false);

        cells.setOnsets(5_000);
        cells.setOffsets(6_000);

        assertEquals(hidden.getOnset(), 1_000);
        assertEquals(hidden.getOffset(), 1_000);
        assertEquals(shown.getOnset(), 5_000);
        assertEquals(shown.getOffset(), 6_000);
    }

    /** Redoing a new column puts it back selected; hidden since, it is not where the keypad's New Cell goes. */
    @Test
    public void aColumnPutBackHiddenAndSelectedIsPassedOver() throws Exception {
        Variable notes = spreadsheet.columns().newColumn("notes");
        spreadsheet.columns().hide(List.of(notes));
        undo();
        spreadsheet.redo();
        spreadsheet.setLastCreatedVariable(speech);

        assertSame(cells.newCellInSelectedColumn(1_000).getVariable(), speech);
    }

    @Test
    public void selectedCellsInAColumnHiddenSinceAreNotDeleted() {
        Cell cell = gaze.createPointCell(1_000);
        cell.setSelected(true);
        gaze.setHidden(true);

        cells.deleteSelected();

        assertEquals(gaze.getCells(), List.of(cell));
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    @Test
    public void aClipsOffsetFollowsItsLengthNotSetOffset() throws Exception {
        Cell clip = dataStore.createMediaVariable("media_1").createClip(0, CAM);
        clip.setSelected(true);

        cells.setOffsets(4_000);

        assertEquals(clip.getOffset(), 10_000);
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    // Delete Cells

    @Test
    public void deletingTakesTheSelectedCellsExceptLockedOnesAsOneEdit() {
        Cell first = gaze.createPointCell(1_000);
        Cell second = gaze.createPointCell(2_000);
        Cell locked = speech.createPointCell(1_000);
        first.setSelected(true);
        second.setSelected(true);
        locked.setSelected(true);
        speech.setLocked(true);
        spreadsheet.setLastCreatedCell(first);
        spreadsheet.setLastSelectedCell(second);

        cells.deleteSelected();

        assertTrue(gaze.getCells().isEmpty());
        assertEquals(speech.getCells(), List.of(locked));
        assertNull(spreadsheet.getLastCreatedCell());
        assertNull(spreadsheet.getLastSelectedCell());

        undo();
        assertEquals(gaze.getCells().size(), 2);
        assertFalse(spreadsheet.getUndoManager().canUndo(), "one edit");
    }

    /** Redo deletes the very cell deleted, not another with the same times and value. */
    @Test
    public void redoingADeleteRemovesTheCellDeleted() {
        Cell deleted = gaze.createPointCell(1_000);
        Cell kept = gaze.createPointCell(1_000);
        dataStore.deselectAll();
        deleted.setSelected(true);

        cells.deleteSelected();
        undo();
        spreadsheet.redo();

        assertEquals(gaze.getCells(), List.of(kept));
    }

    @Test
    public void deletingNothingRecordsNothing() {
        cells.deleteSelected();

        assertFalse(spreadsheet.getUndoManager().canUndo());
    }
}
