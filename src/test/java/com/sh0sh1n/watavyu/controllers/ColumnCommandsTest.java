package com.sh0sh1n.watavyu.controllers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.UserWarningException;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.ColumnListener;
import com.sh0sh1n.watavyu.models.Project;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import java.util.List;

/**
 * Each column command is the one path for its user action: it changes the columns and records what is an edit, so undo
 * puts back the same columns, in their places.
 */
public class ColumnCommandsTest {

    private DataStore dataStore;
    private ProjectController spreadsheet;
    private ColumnCommands columns;
    private Column gaze;
    private Column speech;
    private Column gesture;

    @BeforeMethod
    public void setUp() throws Exception {
        dataStore = DataStoreFactory.newDataStore();
        spreadsheet = new ProjectController(new Project(), dataStore);
        columns = spreadsheet.columns();
        gaze = dataStore.createColumn("gaze");
        speech = dataStore.createColumn("speech");
        gesture = dataStore.createColumn("gesture");
        dataStore.deselectAll();
    }

    private List<String> order() {
        return dataStore.getAllColumns().stream().map(Column::getName).toList();
    }

    private boolean canUndo() {
        return spreadsheet.getUndoManager().canUndo();
    }

    // New Column

    @Test
    public void aNewColumnGoesLastAndIsTheOneSelected() throws Exception {
        gaze.setSelected(true);

        Column notes = columns.newColumn("notes");

        assertEquals(order(), List.of("gaze", "speech", "gesture", "notes"));
        assertEquals(dataStore.getSelectedColumns(), List.of(notes));
        assertSame(spreadsheet.getLastCreatedColumn(), notes);
        assertEquals(spreadsheet.getUndoManager().getUndoPresentationName(), "Undo New Column \"notes\"");
    }

    @Test
    public void undoTakesANewColumnOutAndRedoPutsTheSameColumnBack() throws Exception {
        Column notes = columns.newColumn("notes");

        spreadsheet.undo();
        assertNull(dataStore.getColumn("notes"));

        spreadsheet.redo();
        assertSame(dataStore.getColumn("notes"), notes);
        assertEquals(order(), List.of("gaze", "speech", "gesture", "notes"));
        assertEquals(dataStore.getSelectedColumns(), List.of(notes));
    }

    /** Redo used to make a new column of the same name, so a cell redone after it went into the old, deleted one. */
    @Test
    public void aCellRedoneAfterItsNewColumnIsInTheSpreadsheet() throws Exception {
        Column notes = columns.newColumn("notes");
        Cell cell = spreadsheet.cells().newCell(1_000);
        assertSame(cell.getColumn(), notes);

        spreadsheet.undo();
        spreadsheet.undo();
        spreadsheet.redo();
        spreadsheet.redo();

        assertEquals(dataStore.getColumn("notes").getCells(), List.of(cell));
        assertSame(dataStore.getColumn(cell), notes);
    }

    @Test
    public void aTakenNameAddsNothingAndKeepsTheSelection() {
        Cell cell = gaze.createPointCell(1_000);
        cell.setSelected(true);
        speech.setSelected(true);

        assertThrows(UserWarningException.class, () -> columns.newColumn("gaze"));

        assertEquals(order(), List.of("gaze", "speech", "gesture"));
        assertFalse(canUndo());
        assertTrue(cell.isSelected());
        assertTrue(speech.isSelected());
    }

    // New Media Column

    @Test
    public void newMediaColumnCanBeUndoneAndRedone() throws Exception {
        Column media = columns.newMediaColumn();

        assertTrue(media.isMedia());
        assertEquals(dataStore.getSelectedColumns(), List.of(media));
        assertEquals(spreadsheet.getUndoManager().getUndoPresentationName(), "Undo New Media Column media_1");
        spreadsheet.undo();
        assertEquals(order(), List.of("gaze", "speech", "gesture"));

        spreadsheet.redo();
        assertSame(dataStore.getColumn("media_1"), media);
        assertEquals(order(), List.of("gaze", "speech", "gesture", "media_1"));
    }

    /** Redoing a column hidden since leaves the selection as it is, as a hidden column cannot be selected. */
    @Test
    public void redoingAColumnHiddenSinceKeepsTheSelection() throws Exception {
        Column notes = columns.newColumn("notes");
        columns.hide(List.of(notes));
        spreadsheet.undo();
        speech.setSelected(true);

        spreadsheet.redo();

        assertSame(dataStore.getColumn("notes"), notes);
        assertEquals(dataStore.getSelectedColumns(), List.of(speech));
    }

    /** Undoing a new column clears the selection only if the column is selected then. */
    @Test
    public void undoingANewColumnClearsTheSelectionOnlyIfItIsSelected() throws Exception {
        columns.newColumn("notes");
        Cell cell = speech.createPointCell(1_000);
        dataStore.deselectAll();
        speech.setSelected(true);
        cell.setSelected(true);

        spreadsheet.undo();

        assertEquals(dataStore.getSelectedColumns(), List.of(speech));
        assertTrue(cell.isSelected());

        spreadsheet.redo();
        assertEquals(order(), List.of("gaze", "speech", "gesture", "notes"));
        assertEquals(dataStore.getSelectedColumns(), List.of(dataStore.getColumn("notes")));
        spreadsheet.undo();
        assertTrue(dataStore.getSelectedColumns().isEmpty(), "the column was the one selected");
    }

    // Delete Column

    @Test
    public void deletingColumnsIsOneEditAndClearsSelection() {
        Cell cell = gesture.createPointCell(1_000);
        cell.setSelected(true);
        gaze.setSelected(true);

        assertTrue(columns.delete(List.of(gaze, speech)));

        assertEquals(order(), List.of("gesture"));
        assertFalse(cell.isSelected());
        assertEquals(spreadsheet.getUndoManager().getUndoPresentationName(), "Undo Delete 2 Columns");
    }

    @Test
    public void undoPutsDeletedColumnsBackInTheirPlaces() {
        Cell cell = speech.createPointCell(1_000);
        columns.delete(List.of(gaze, gesture));

        spreadsheet.undo();
        assertEquals(order(), List.of("gaze", "speech", "gesture"));
        assertEquals(speech.getCells(), List.of(cell));

        spreadsheet.redo();
        assertEquals(order(), List.of("speech"));
    }

    /** A deleted column's place used to be given to the next new column, so undoing the deletion misplaced it. */
    @Test
    public void undoingADeletionAfterANewColumnKeepsEveryPlace() throws Exception {
        columns.delete(List.of(gaze));
        columns.newColumn("notes");
        assertEquals(order(), List.of("speech", "gesture", "notes"));

        spreadsheet.undo();
        spreadsheet.undo();
        assertEquals(order(), List.of("gaze", "speech", "gesture"));
    }

    /** A column made outside the commands can take a deleted column's name before its undo. */
    @Test
    public void undoingADeletionWhoseNameIsTakenIsRefusedAndStaysAsItWas() throws Exception {
        Column media = columns.newMediaColumn();
        columns.delete(List.of(gaze, media));
        Column taken = dataStore.createMediaColumn("media_1");

        assertEquals(spreadsheet.undo(), "Another column is named media_1. Rename or delete it to undo this.");
        assertThrows(CannotUndoException.class, spreadsheet.getUndoManager()::undo);
        assertEquals(order(), List.of("speech", "gesture", "media_1"));
        assertSame(dataStore.getColumn("media_1"), taken);

        dataStore.removeColumn(taken);
        assertNull(spreadsheet.undo());
        assertEquals(order(), List.of("gaze", "speech", "gesture", "media_1"));
        assertSame(dataStore.getColumn("media_1"), media);
    }

    @Test
    public void aRefusedRedoStaysWhereItWasInTheHistory() throws Exception {
        Column media = columns.newMediaColumn();
        spreadsheet.undo();
        Column taken = dataStore.createMediaColumn("media_1");

        assertEquals(spreadsheet.redo(), "Another column is named media_1. Rename or delete it to redo this.");
        assertThrows(CannotRedoException.class, spreadsheet.getUndoManager()::redo);
        dataStore.removeColumn(taken);
        assertNull(spreadsheet.redo());

        assertSame(dataStore.getColumn("media_1"), media);
    }

    /** Should a column be taken out without an edit, a deleted column whose place is past the end comes back last. */
    @Test
    public void aDeletedColumnWhosePlaceHasGoneComesBackLast() {
        columns.delete(List.of(gesture));
        dataStore.removeColumn(speech);

        spreadsheet.undo();

        assertEquals(order(), List.of("gaze", "gesture"));
    }

    @Test
    public void aLockedColumnStopsTheWholeDeletionAndShowsIt() {
        ColumnListener speechLock = mock(ColumnListener.class);
        ColumnListener gestureLock = mock(ColumnListener.class);
        speech.addListener(speechLock);
        gesture.addListener(gestureLock);
        speech.setLocked(true);
        gesture.setLocked(true);

        assertFalse(columns.delete(List.of(gaze, speech, gesture)));

        assertEquals(order(), List.of("gaze", "speech", "gesture"));
        verify(speechLock, times(1)).editRefused();
        verify(gestureLock, times(1)).editRefused();
        assertFalse(canUndo());
    }

    @Test
    public void deletingNothingInTheSpreadsheetIsNoEdit() {
        columns.delete(List.of(gaze));

        assertFalse(columns.delete(List.of(gaze)));
        assertFalse(columns.delete(List.of()));
        assertEquals(spreadsheet.getUndoManager().getUndoPresentationName(), "Undo Delete Column gaze");
    }

    // Lock Column

    @Test
    public void lockingIsAnEditThatUndoReverses() {
        columns.setLocked(speech, true);
        assertTrue(speech.isLocked());
        assertEquals(spreadsheet.getUndoManager().getUndoPresentationName(), "Undo Lock Column speech");

        spreadsheet.undo();
        assertFalse(speech.isLocked());
    }

    @Test
    public void aLockLeftAsItWasIsNoEdit() {
        columns.setLocked(speech, false);

        assertFalse(canUndo());
    }

    // Hide and show

    @Test
    public void hidingDeselectsTheHiddenColumnsAndTheirCellsOnly() {
        Cell hidden = speech.createPointCell(1_000);
        Cell shown = gaze.createPointCell(1_000);
        hidden.setSelected(true);
        shown.setSelected(true);
        speech.setSelected(true);
        gaze.setSelected(true);

        columns.hide(List.of(speech));

        assertTrue(speech.isHidden());
        assertFalse(speech.isSelected());
        assertFalse(hidden.isSelected());
        assertTrue(gaze.isSelected());
        assertTrue(shown.isSelected());
        assertFalse(canUndo(), "hiding is not an edit");
    }

    @Test
    public void showAllShowsEveryHiddenColumn() {
        columns.hide(List.of(gaze, gesture));
        columns.show(gaze);
        assertFalse(gaze.isHidden());
        assertTrue(gesture.isHidden());

        columns.showAll();
        assertTrue(dataStore.getAllColumns().stream().noneMatch(Column::isHidden));
    }

    // Move

    @Test
    public void aColumnMovesToAnothersPlaceShiftingThoseBetween() {
        columns.move(gesture, gaze);
        assertEquals(order(), List.of("gesture", "gaze", "speech"));

        columns.move(gesture, speech);
        assertEquals(order(), List.of("gaze", "speech", "gesture"));
        assertFalse(canUndo(), "moving is not an edit");
    }

    @Test
    public void aLockedColumnMoves() {
        gaze.setLocked(true);

        columns.move(gaze, gesture);

        assertEquals(order(), List.of("speech", "gesture", "gaze"));
    }

    /** Moving after a deletion and its undo used to move a column next to the one asked for. */
    @Test
    public void movingAfterUndoingADeletionMovesTheColumnAsked() {
        columns.delete(List.of(gaze));
        spreadsheet.undo();

        columns.move(gesture, speech);

        assertEquals(order(), List.of("gaze", "gesture", "speech"));
    }
}
