package com.sh0sh1n.watavyu.controllers.project;

import com.sh0sh1n.watavyu.models.db.Cell;
import com.sh0sh1n.watavyu.undoableedits.TypeValueEdit;

import java.util.List;

/**
 * The commands for typing into one spreadsheet's cells, from their value and time editors, which own only the text:
 * each leaves locked columns and media clips alone, changes the data model and records what it did in the
 * spreadsheet's undo history.
 */
public final class TypingCommands {

    private final ProjectController spreadsheet;

    /**
     * @param spreadsheet the spreadsheet whose cells are typed into
     */
    TypingCommands(final ProjectController spreadsheet) {
        this.spreadsheet = spreadsheet;
    }

    /**
     * A time typed into a cell's onset, from its time field: each change is an edit of its own.
     *
     * @param cell  the cell
     * @param onset its new onset
     * @return whether it changed: not in a locked column, nor when it is already the onset
     */
    public boolean typeOnset(final Cell cell, final long onset) {
        return spreadsheet.getDataStore().contains(cell) && CellTimes.setOnset(spreadsheet, cell, onset);
    }

    /**
     * A time typed into a cell's offset, from its time field: each change is an edit of its own. A clip's offset
     * follows from its onset and length, and is never typed.
     *
     * @param cell   the cell
     * @param offset its new offset
     * @return whether it changed: not in a locked column or for a clip, nor when it is already the offset
     */
    public boolean typeOffset(final Cell cell, final long offset) {
        return spreadsheet.getDataStore().contains(cell) && CellTimes.setOffset(spreadsheet, cell, offset);
    }

    /**
     * Typing into a cell's value, from its editors: sets one code's value, {@code ""} emptying it, and records the
     * change. Typing that continues the last, as each key typed into a cell while it keeps the focus after the first
     * does, joins its edit, so undo puts back the values from before the typing began, in every code typed into.
     *
     * <p>The cell tells its listeners, as it does for undo and redo; the view typed in shows the typing already, so it
     * leaves its editors as they are.
     *
     * @param cell       the cell
     * @param code       the code's place among the column's codes
     * @param value      the code's new value
     * @param continuing whether this continues the last typing
     * @return whether the value changed: not in a locked column, a clip or a cell not in the spreadsheet
     */
    public boolean typeValue(final Cell cell, final int code, final String value, final boolean continuing) {
        if (!spreadsheet.getDataStore().contains(cell) || cell.getClip() != null || cell.getVariable().refusesEdits()) {
            return false;
        }
        List<String> before = cell.getValues();
        if (code < 0 || code >= before.size()) {
            return false;
        }
        cell.setValue(code, value);
        List<String> after = cell.getValues();
        if (after.equals(before)) {
            return false;
        }
        spreadsheet.postEdit(new TypeValueEdit(cell, before, after, continuing));
        return true;
    }
}
