package org.datavyu.undoableedits;

import org.datavyu.models.db.Argument;
import org.datavyu.models.db.Variable;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

/** Moving a code among its column's codes, from the Code Editor's arrows; the cells' values move with it. */
public final class MoveCodeEdit extends AbstractUndoableEdit {

    private final Variable column;
    private final Argument code;
    private final int from;
    private final int to;

    /**
     * Records a code that has just been moved.
     *
     * @param column the column
     * @param from   the code's place before
     * @param to     its place now
     */
    public MoveCodeEdit(final Variable column, final int from, final int to) {
        this.column = column;
        this.from = from;
        this.to = to;
        this.code = column.getRootNode().childArguments.get(to);
    }

    @Override
    public String getPresentationName() {
        return "Move Code " + code.name;
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        column.moveArgument(to, from);
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        column.moveArgument(from, to);
    }
}
