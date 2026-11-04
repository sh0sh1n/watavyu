package org.datavyu.controllers.project;

import org.datavyu.models.db.Argument;
import org.datavyu.models.db.Variable;
import org.datavyu.undoableedits.AddCodeEdit;
import org.datavyu.undoableedits.DeleteCodeEdit;
import org.datavyu.undoableedits.MoveCodeEdit;
import org.datavyu.undoableedits.RenameCodeEdit;

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
     * @return the new code, or null if the column is locked
     */
    public Argument add(final Variable column) {
        if (column.refusesEdits()) {
            return null;
        }
        Argument code = column.addArgument();
        spreadsheet.postEdit(new AddCodeEdit(column, code));
        return code;
    }

    /**
     * Deletes a code and each cell's value for it; undo puts both back.
     *
     * @param column the column
     * @param index  the code's place among the column's codes
     * @return whether it was deleted, which a locked column refuses
     */
    public boolean delete(final Variable column, final int index) {
        if (column.refusesEdits()) {
            return false;
        }
        DeleteCodeEdit edit = new DeleteCodeEdit(column, index);
        column.removeArgument(index);
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
    public boolean move(final Variable column, final int from, final int to) {
        int count = column.getRootNode().childArguments.size();
        if (from == to || from < 0 || from >= count || to < 0 || to >= count || column.refusesEdits()) {
            return false;
        }
        column.moveArgument(from, to);
        spreadsheet.postEdit(new MoveCodeEdit(column, from, to));
        return true;
    }

    /**
     * Renames a code, where a name is typed a key at a time: a rename continuing the last one joins its edit, so undo
     * puts back the name from before the typing began.
     *
     * @param column     the column
     * @param index      the code's place among the column's codes
     * @param name       its new name
     * @param continuing whether this continues the last rename, as each key typed into one name after the first does
     * @return whether it was renamed: a locked column refuses, and the same name is no change
     */
    public boolean rename(final Variable column, final int index, final String name, final boolean continuing) {
        String from = column.getRootNode().childArguments.get(index).name;
        if (from.equals(name) || column.refusesEdits()) {
            return false;
        }
        column.renameArgument(index, name);
        spreadsheet.postEdit(new RenameCodeEdit(column, index, from, continuing));
        return true;
    }
}
