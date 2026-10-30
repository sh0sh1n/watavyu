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
    private final String columnName;
    private final boolean locked;

    /**
     * Records a lock change that has already been made.
     *
     * @param dataStore  the spreadsheet holding the column
     * @param columnName the column's name, which finds it again after it has been deleted and restored
     * @param locked     whether the column was locked (true) or unlocked
     */
    public LockColumnEdit(final DataStore dataStore, final String columnName, final boolean locked) {
        this.dataStore = dataStore;
        this.columnName = columnName;
        this.locked = locked;
    }

    @Override
    public String getPresentationName() {
        return (locked ? "Lock Column " : "Unlock Column ") + columnName;
    }

    /** Undoes the lock change; refused, leaving the edit as it was, if the column is gone. */
    @Override
    public void undo() throws CannotUndoException {
        Variable column = dataStore.getVariable(columnName);
        if (column == null || !canUndo()) {
            throw new CannotUndoException();
        }
        super.undo();
        column.setLocked(!locked);
    }

    /** Redoes the lock change; refused, leaving the edit as it was, if the column is gone. */
    @Override
    public void redo() throws CannotRedoException {
        Variable column = dataStore.getVariable(columnName);
        if (column == null || !canRedo()) {
            throw new CannotRedoException();
        }
        super.redo();
        column.setLocked(locked);
    }
}
