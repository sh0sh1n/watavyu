package com.sh0sh1n.watavyu.undoableedits;

import com.sh0sh1n.watavyu.models.db.Column;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

/**
 * Locking or unlocking a column. Being in the undo history keeps it in order with the edits around
 * it: undoing past a lock first unlocks the column, so an earlier change to it can be undone too.
 */
public final class LockColumnEdit extends AbstractUndoableEdit {

    private final Column column;
    private final boolean locked;

    /**
     * Records a lock change that has already been made.
     *
     * @param column the column, which undo of its deletion puts back as the same object
     * @param locked whether the column was locked (true) or unlocked
     */
    public LockColumnEdit(final Column column, final boolean locked) {
        this.column = column;
        this.locked = locked;
    }

    @Override
    public String getPresentationName() {
        return (locked ? "Lock Column " : "Unlock Column ") + column.getName();
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        column.setLocked(!locked);
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        column.setLocked(locked);
    }
}
