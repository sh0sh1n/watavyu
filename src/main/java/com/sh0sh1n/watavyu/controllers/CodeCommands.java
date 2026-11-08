package com.sh0sh1n.watavyu.controllers;

import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.undoableedits.AddCodeEdit;
import com.sh0sh1n.watavyu.undoableedits.DeleteCodeEdit;
import com.sh0sh1n.watavyu.undoableedits.MoveCodeEdit;
import com.sh0sh1n.watavyu.undoableedits.RenameCodeEdit;

import java.util.List;

/**
 * The commands that add, delete, move and rename the codes of one spreadsheet's columns, from the Code Editor. Each
 * changes the column and each cell's values with it, and records an edit in the spreadsheet's undo history. A locked
 * column refuses them, and shows it.
 */
public final class CodeCommands {

    private final ProjectController spreadsheet;

    /**
     * @param spreadsheet the spreadsheet whose columns' codes the commands change
     */
    CodeCommands(final ProjectController spreadsheet) {
        this.spreadsheet = spreadsheet;
    }

    /**
     * Adds a code after a column's others, with an empty value in each cell.
     *
     * @param column the column
     * @return the new code's place among the column's codes, or -1 if the column is locked
     */
    public int add(final Column column) {
        if (column.refusesEdits()) {
            return -1;
        }
        int index = column.addCode();
        spreadsheet.postEdit(new AddCodeEdit(column, index));
        return index;
    }

    /**
     * Deletes a code and each cell's value for it; undo puts both back.
     *
     * @param column the column
     * @param index  the code's place among the column's codes
     * @return whether it was deleted: a place out of range or a locked column leaves it
     */
    public boolean delete(final Column column, final int index) {
        if (!inRange(column, index) || column.refusesEdits()) {
            return false;
        }
        DeleteCodeEdit edit = new DeleteCodeEdit(column, index);
        column.removeCode(index);
        spreadsheet.postEdit(edit);
        return true;
    }

    /**
     * Moves a code to another place among a column's codes, shifting those between; the cells' values move with it.
     *
     * @param column the column
     * @param from   the code's place
     * @param to     its new place
     * @return whether it moved: a place out of range or a locked column leaves it
     */
    public boolean move(final Column column, final int from, final int to) {
        if (from == to || !inRange(column, from) || !inRange(column, to) || column.refusesEdits()) {
            return false;
        }
        column.moveCode(from, to);
        spreadsheet.postEdit(new MoveCodeEdit(column, from, to));
        return true;
    }

    /**
     * Renames a code, where a name is typed a key at a time: a rename continuing the last one joins its edit, so undo
     * puts back the name from before the typing began. A code's name is not empty and no other code of its column has
     * it, as a project file requires.
     *
     * @param column     the column
     * @param index      the code's place among the column's codes
     * @param name       its new name
     * @param continuing whether this continues the last rename, as each key typed into one name after the first does
     * @return whether it was renamed: the same name is no change, and a place out of range, an empty name, another
     *     code's name and a locked column are refused
     */
    public boolean rename(final Column column, final int index, final String name, final boolean continuing) {
        if (!inRange(column, index)) {
            return false;
        }
        List<String> codes = column.getCodes();
        String from = codes.get(index);
        if (from.equals(name) || name.isEmpty() || codes.contains(name)
                || column.refusesEdits()) {
            return false;
        }
        column.renameCode(index, name);
        spreadsheet.postEdit(new RenameCodeEdit(column, index, from, continuing));
        return true;
    }

    private static boolean inRange(final Column column, final int index) {
        return index >= 0 && index < column.getCodes().size();
    }
}
