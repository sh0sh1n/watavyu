package com.sh0sh1n.watavyu.undoableedits;

import com.sh0sh1n.watavyu.models.Column;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

/** Moving a code among its column's codes, from the Code Editor's arrows; the cells' values move with it. */
public final class MoveCodeEdit extends AbstractUndoableEdit {

    private final Column column;
    private final String code;
    private final int from;
    private final int to;

    /**
     * Records a code that has just been moved.
     *
     * @param column the column
     * @param from   the code's place before
     * @param to     its place now
     */
    public MoveCodeEdit(final Column column, final int from, final int to) {
        this.column = column;
        this.from = from;
        this.to = to;
        this.code = column.getCodes().get(to);
    }

    @Override
    public String getPresentationName() {
        return "Move Code " + code;
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        column.moveCode(to, from);
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        column.moveCode(from, to);
    }
}
