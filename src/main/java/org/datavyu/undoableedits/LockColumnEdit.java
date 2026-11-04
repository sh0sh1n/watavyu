package org.datavyu.undoableedits;

import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.Variable;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

/**
 * Locking or unlocking a column. Being in the undo history keeps it in order with the edits around
 * it: undoing past a lock first unlocks the column, so an earlier change to it can be undone too.
 */
public final class LockColumnEdit extends AbstractUndoableEdit {

    private final DataStore dataStore;
    private final Variable column;
    private final boolean locked;

    /**
     * Records a lock change that has already been made.
     *
     * @param dataStore the spreadsheet holding the column
     * @param column    the column, which undo of its deletion puts back as the same object
     * @param locked    whether the column was locked (true) or unlocked
     */
    public LockColumnEdit(final DataStore dataStore, final Variable column, final boolean locked) {
        this.dataStore = dataStore;
        this.column = column;
        this.locked = locked;
    }

    @Override
    public String getPresentationName() {
        return (locked ? "Lock Column " : "Unlock Column ") + column.getName();
    }

    /** Undoes the lock change, unless the column has been taken out without an edit, leaving nothing to change. */
    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        if (dataStore.contains(column)) {
            column.setLocked(!locked);
        }
    }

    /** Redoes the lock change, unless the column has been taken out without an edit, leaving nothing to change. */
    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        if (dataStore.contains(column)) {
            column.setLocked(locked);
        }
    }
}
