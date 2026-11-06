package org.datavyu.controllers.project;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.project.Project;
import org.datavyu.undoableedits.ChangeOnsetCellEdit;
import org.testng.annotations.Test;

/**
 * Each spreadsheet has its own undo history, and an edit undoes in the spreadsheet it was made in, whichever tab is
 * selected when it is undone (B17).
 */
public class SpreadsheetUndoTest {

    /** Makes a spreadsheet with a column named notes holding one cell at 1 s, as two open projects might. */
    private static ProjectController spreadsheet() throws Exception {
        ProjectController spreadsheet = new ProjectController(new Project(), DataStoreFactory.newDataStore());
        spreadsheet.getDataStore().createVariable("notes").createPointCell(1_000);
        return spreadsheet;
    }

    private static Cell cell(final ProjectController spreadsheet) {
        return spreadsheet.getDataStore().getVariable("notes").getCells().get(0);
    }

    /** Moves the cell's onset and records the change, as typing a time in it does. */
    private static void moveOnset(final ProjectController spreadsheet, final long onset) {
        Cell cell = cell(spreadsheet);
        long old = cell.getOnset();
        cell.setOnset(onset);
        spreadsheet.postEdit(new ChangeOnsetCellEdit(spreadsheet, cell, old, onset));
    }

    @Test
    public void undoChangesOnlyTheSpreadsheetTheEditWasMadeIn() throws Exception {
        ProjectController first = spreadsheet();
        ProjectController second = spreadsheet();
        moveOnset(first, 2_000);
        moveOnset(second, 3_000);

        first.undo();

        assertEquals(cell(first).getOnset(), 1_000);
        assertEquals(cell(second).getOnset(), 3_000);
        assertFalse(first.getUndoManager().canUndo());
        assertTrue(second.getUndoManager().canUndo());

        first.redo();

        assertEquals(cell(first).getOnset(), 2_000);
        assertEquals(cell(second).getOnset(), 3_000);
    }

    /** Set Onset after an undo is recorded, so undo takes it back too. */
    @Test
    public void anEditAfterAnUndoIsRecorded() throws Exception {
        ProjectController spreadsheet = spreadsheet();
        Cell cell = cell(spreadsheet);
        // A new cell is selected.
        spreadsheet.cells().setOnsets(2_000);
        assertEquals(cell.getOnset(), 2_000);
        spreadsheet.undo();
        assertEquals(cell.getOnset(), 1_000);

        cell.setSelected(true);
        spreadsheet.cells().setOnsets(3_000);
        spreadsheet.undo();

        assertEquals(cell.getOnset(), 1_000);
    }

    /** Typing after an undo, though it continues the typing undone, is an edit of its own that undo takes back. */
    @Test
    public void typingAfterAnUndoIsAnEditOfItsOwn() throws Exception {
        ProjectController spreadsheet = spreadsheet();
        Cell cell = cell(spreadsheet);
        spreadsheet.typing().typeValue(cell, 0, "smile", false);
        spreadsheet.undo();

        spreadsheet.typing().typeValue(cell, 0, "frown", true);
        spreadsheet.undo();

        assertEquals(cell.getValue(0), "");
        assertFalse(spreadsheet.getUndoManager().canUndo());
        spreadsheet.redo();
        assertEquals(cell.getValue(0), "frown");
    }

    /** Edits equal by value are still told apart, so a new edit drops only what could be redone. */
    @Test
    public void aNewEditKeepsEarlierEditsEqualToTheLast() throws Exception {
        ProjectController spreadsheet = spreadsheet();
        Cell cell = cell(spreadsheet);
        moveOnset(spreadsheet, 2_000);
        moveOnset(spreadsheet, 1_000);
        moveOnset(spreadsheet, 2_000);
        moveOnset(spreadsheet, 3_000);

        spreadsheet.undo();
        spreadsheet.undo();
        spreadsheet.undo();

        assertEquals(cell.getOnset(), 2_000);
    }

    @Test
    public void editsJoinOnlyTheirOwnSpreadsheetsHistory() throws Exception {
        ProjectController first = spreadsheet();
        ProjectController second = spreadsheet();

        moveOnset(first, 2_000);

        assertTrue(first.getUndoManager().canUndo());
        assertFalse(second.getUndoManager().canUndo());
    }
}
