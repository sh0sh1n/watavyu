package org.datavyu.undoableedits;

import org.datavyu.models.db.Variable;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

/**
 * Renaming a code, from the Code Editor. A name is typed a key at a time, each key a rename, so a rename that continues
 * the last one done joins it, and undo puts back the name from before the typing began.
 */
public final class RenameCodeEdit extends AbstractUndoableEdit {

    private final Variable column;
    private final int index;
    private final String from;
    private final boolean continuing;
    private String to;

    /** Whether another edit has followed this one, after which it joins no more renames. */
    private boolean closed;

    /**
     * Records a code that has just been renamed.
     *
     * @param column     the column
     * @param index      the code's place among the column's codes
     * @param from       its name before
     * @param continuing whether the rename continues the last one, which it then joins
     */
    public RenameCodeEdit(final Variable column, final int index, final String from, final boolean continuing) {
        this.column = column;
        this.index = index;
        this.from = from;
        this.to = column.getRootNode().childArguments.get(index).name;
        this.continuing = continuing;
    }

    @Override
    public String getPresentationName() {
        return "Rename Code " + from + " to " + to;
    }

    /**
     * Joins a rename of the same code that continues from this one, while this one is done and no other edit has
     * followed it, even one since undone.
     */
    @Override
    public boolean addEdit(final UndoableEdit edit) {
        if (!closed && edit instanceof RenameCodeEdit next && next.continuing && canUndo() && next.column.equals(column)
                && next.index == index && next.from.equals(to)) {
            to = next.to;
            return true;
        }
        closed = true;
        return false;
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        column.renameArgument(index, from);
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        column.renameArgument(index, to);
    }
}
