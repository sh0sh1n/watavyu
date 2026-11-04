package org.datavyu.controllers.project;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.project.Project;
import org.datavyu.undoableedits.ChangeOnsetCellEdit;
import org.datavyu.undoableedits.ChangeCellEdit.Granularity;
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
        spreadsheet.postEdit(new ChangeOnsetCellEdit(spreadsheet, cell, old, onset, Granularity.COARSEGRAINED));
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

    /** A coarse edit of one cell replaces that cell's fine edits before it, not another cell's. */
    @Test
    public void aCoarseEditKeepsAnotherCellsFineEdits() throws Exception {
        ProjectController spreadsheet = spreadsheet();
        Cell first = cell(spreadsheet);
        Cell second = spreadsheet.getDataStore().getVariable("notes").createPointCell(4_000);
        first.setOnset(2_000);
        spreadsheet.postEdit(new ChangeOnsetCellEdit(spreadsheet, first, 1_000, 2_000, Granularity.FINEGRAINED));
        second.setOnset(5_000);
        spreadsheet.postEdit(new ChangeOnsetCellEdit(spreadsheet, second, 4_000, 5_000, Granularity.COARSEGRAINED));

        spreadsheet.undo();
        spreadsheet.undo();

        assertEquals(first.getOnset(), 1_000);
        assertEquals(second.getOnset(), 4_000);
    }

    /**
     * An edit made after an undo is compared with the last edit done, not the one waiting to be redone. It used to be
     * dropped as that one's duplicate, so it could not be undone.
     */
    @Test
    public void anEditAfterAnUndoIsRecorded() throws Exception {
        ProjectController spreadsheet = spreadsheet();
        Cell cell = cell(spreadsheet);
        spreadsheet.cells().setOnsets(2_000);
        spreadsheet.undo();
        assertEquals(cell.getOnset(), 1_000);

        cell.setSelected(true);
        spreadsheet.cells().setOnsets(3_000);
        spreadsheet.undo();

        assertEquals(cell.getOnset(), 1_000);
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
