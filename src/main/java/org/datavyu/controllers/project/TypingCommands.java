package org.datavyu.controllers.project;

import org.datavyu.models.db.Cell;
import org.datavyu.models.db.CellValue;
import org.datavyu.models.db.MatrixCellValue;
import org.datavyu.undoableedits.TypeValueEdit;

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
     * <p>The cell's view is not told, as it shows the typing already and telling it would rebuild the editor being
     * typed in; undo and redo tell it.
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
        List<CellValue> codes = ((MatrixCellValue) cell.getCellValue()).getArguments();
        if (code < 0 || code >= codes.size()) {
            return false;
        }
        List<String> before = values(codes);
        codes.get(code).set(value);
        List<String> after = values(codes);
        if (after.equals(before)) {
            return false;
        }
        spreadsheet.postEdit(new TypeValueEdit(cell, before, after, continuing));
        return true;
    }

    private static List<String> values(final List<CellValue> codes) {
        return codes.stream().map(CellValue::toString).toList();
    }
}
