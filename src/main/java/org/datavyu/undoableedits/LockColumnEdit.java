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
public final class LockColumnEdit extends AbstractUndoableEdit implements GuardedEdit {

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

    @Override
    public String undoRefusal() {
        return dataStore.contains(column) ? null : "Column " + column.getName() + " is no longer in the spreadsheet.";
    }

    @Override
    public String redoRefusal() {
        return undoRefusal();
    }

    /** Undoes the lock change; refused, leaving the edit as it was, if the column is not in the spreadsheet. */
    @Override
    public void undo() throws CannotUndoException {
        if (undoRefusal() != null || !canUndo()) {
            throw new CannotUndoException();
        }
        super.undo();
        column.setLocked(!locked);
    }

    /** Redoes the lock change; refused, leaving the edit as it was, if the column is not in the spreadsheet. */
    @Override
    public void redo() throws CannotRedoException {
        if (redoRefusal() != null || !canRedo()) {
            throw new CannotRedoException();
        }
        super.redo();
        column.setLocked(locked);
    }
}
