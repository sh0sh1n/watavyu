package org.datavyu.undoableedits;

import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.db.Variable;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

/**
 * Renaming a column, from the Code Editor. A name is typed a key at a time, each key a rename, so a rename that
 * continues the last one done joins it, and undo puts back the name from before the typing began.
 */
public final class RenameColumnEdit extends AbstractUndoableEdit implements GuardedEdit {

    private final Variable column;
    private final String from;
    private final boolean continuing;
    private String to;

    /** Whether another edit has followed this one, after which it joins no more renames. */
    private boolean closed;

    /**
     * Records a column that has just been renamed.
     *
     * @param column     the column
     * @param from       its name before
     * @param continuing whether the rename continues the last one, which it then joins
     */
    public RenameColumnEdit(final Variable column, final String from, final boolean continuing) {
        this.column = column;
        this.from = from;
        this.to = column.getName();
        this.continuing = continuing;
    }

    @Override
    public String getPresentationName() {
        return "Rename Column " + from + " to " + to;
    }

    /**
     * Joins a rename of the same column that continues from this one, while this one is done and no other edit has
     * followed it, even one since undone.
     */
    @Override
    public boolean addEdit(final UndoableEdit edit) {
        if (!closed && edit instanceof RenameColumnEdit next && next.continuing && canUndo()
                && next.column.equals(column) && next.from.equals(to)) {
            to = next.to;
            return true;
        }
        closed = true;
        return false;
    }

    @Override
    public String undoRefusal() {
        return refusal(from, "undo");
    }

    @Override
    public String redoRefusal() {
        return refusal(to, "redo");
    }

    private String refusal(final String name, final String action) {
        Variable named = column.getOwningDatastore().getVariable(name);
        return named == null || named.equals(column) ? null
            : "Another column is named " + name + ". Rename or delete it to " + action + " this.";
    }

    @Override
    public void undo() throws CannotUndoException {
        if (undoRefusal() != null || !canUndo()) {
            throw new CannotUndoException();
        }
        super.undo();
        rename(from);
    }

    @Override
    public void redo() throws CannotRedoException {
        if (redoRefusal() != null || !canRedo()) {
            throw new CannotRedoException();
        }
        super.redo();
        rename(to);
    }

    private void rename(final String name) {
        try {
            // A name it has had may be one Datavyu allowed and Watavyu does not, kept from an imported file.
            column.setName(name, true);
        } catch (UserWarningException e) {
            // The column is unlocked, as every later edit is undone.
            throw new IllegalStateException(e);
        }
    }
}
