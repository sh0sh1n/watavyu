package org.datavyu.undoableedits;

import org.datavyu.models.db.Variable;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

/**
 * Adding a code to a column, from the Code Editor. Undo takes the code and its empty values out; redo puts it back in
 * its place, where a later rename of it finds it.
 */
public final class AddCodeEdit extends AbstractUndoableEdit {

    private final Variable column;
    private final String code;
    private final int index;

    /**
     * Records a code that has just been added.
     *
     * @param column the column
     * @param index  the new code's place among the column's codes
     */
    public AddCodeEdit(final Variable column, final int index) {
        this.column = column;
        this.code = column.getCodes().get(index);
        this.index = index;
    }

    @Override
    public String getPresentationName() {
        return "New Code " + code;
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        column.removeCode(index);
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        column.insertCode(index, code);
    }
}
