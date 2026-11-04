package org.datavyu.controllers.project;

import org.datavyu.models.db.Cell;
import org.datavyu.undoableedits.ChangeOffsetCellEdit;
import org.datavyu.undoableedits.ChangeOnsetCellEdit;

/**
 * Sets a cell's time as the commands that time cells do, Set Onset, Set Offset and typing a time, and records it,
 * unless its column is locked, which shows it, or the time is the cell's already, which would be an edit that changes
 * nothing.
 */
final class CellTimes {

    private CellTimes() {
    }

    /**
     * Sets a cell's onset and records it.
     *
     * @param spreadsheet the spreadsheet the cell is in
     * @param cell        the cell
     * @param onset       its new onset
     * @return whether it changed
     */
    static boolean setOnset(final ProjectController spreadsheet, final Cell cell, final long onset) {
        long before = cell.getOnset();
        if (cell.getVariable().refusesEdits() || before == onset) {
            return false;
        }
        cell.setOnset(onset);
        spreadsheet.postEdit(new ChangeOnsetCellEdit(spreadsheet, cell, before, onset));
        return true;
    }

    /**
     * Sets a cell's offset and records it. A clip's offset follows from its onset and length, and is never set.
     *
     * @param spreadsheet the spreadsheet the cell is in
     * @param cell        the cell
     * @param offset      its new offset
     * @return whether it changed
     */
    static boolean setOffset(final ProjectController spreadsheet, final Cell cell, final long offset) {
        long before = cell.getOffset();
        if (cell.getVariable().refusesEdits() || cell.getClip() != null || before == offset) {
            return false;
        }
        cell.setOffset(offset);
        spreadsheet.postEdit(new ChangeOffsetCellEdit(spreadsheet, cell, before, offset));
        return true;
    }
}
