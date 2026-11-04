package org.datavyu.undoableedits;

import org.datavyu.models.db.Argument;
import org.datavyu.models.db.Variable;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

/**
 * Adding a code to a column, from the Code Editor. Undo takes the code and its empty values out; redo puts the same
 * code back in its place, so a later rename of it finds it.
 */
public final class AddCodeEdit extends AbstractUndoableEdit {

    private final Variable column;
    private final Argument code;
    private final int index;

    /**
     * Records a code that has just been added.
     *
     * @param column the column
     * @param code   the new code, among the column's codes
     */
    public AddCodeEdit(final Variable column, final Argument code) {
        this.column = column;
        this.code = code;
        this.index = column.getRootNode().childArguments.indexOf(code);
        if (index < 0) {
            throw new IllegalArgumentException("Code " + code.name + " is not in column " + column.getName());
        }
    }

    @Override
    public String getPresentationName() {
        return "New Code " + code.name;
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        column.removeArgument(index);
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        column.insertArgument(code, index);
    }
}
