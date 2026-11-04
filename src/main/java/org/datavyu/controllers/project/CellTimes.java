package org.datavyu.controllers.project;

import org.datavyu.models.db.Cell;
import org.datavyu.undoableedits.ChangeOffsetCellEdit;
import org.datavyu.undoableedits.ChangeOnsetCellEdit;

/** Sets a cell's time as the commands that time cells do: Set Onset, Set Offset and typing a time. */
final class CellTimes {

    private CellTimes() {
    }

    /**
     * Sets a cell's onset or offset and records it, unless its column is locked, which shows it, or it is a clip's
     * offset, or the time is the cell's already, which would be an edit that changes nothing.
     *
     * @param spreadsheet the spreadsheet the cell is in
     * @param cell        the cell
     * @param onset       whether the onset is set, rather than the offset
     * @param time        the time
     * @return whether the time changed
     */
    static boolean set(final ProjectController spreadsheet, final Cell cell, final boolean onset, final long time) {
        if (cell.getVariable().refusesEdits() || (!onset && cell.getClip() != null)) {
            return false;
        }
        long before = onset ? cell.getOnset() : cell.getOffset();
        if (before == time) {
            return false;
        }
        if (onset) {
            cell.setOnset(time);
            spreadsheet.postEdit(new ChangeOnsetCellEdit(spreadsheet, cell, before, time));
        } else {
            cell.setOffset(time);
            spreadsheet.postEdit(new ChangeOffsetCellEdit(spreadsheet, cell, before, time));
        }
        return true;
    }
}
