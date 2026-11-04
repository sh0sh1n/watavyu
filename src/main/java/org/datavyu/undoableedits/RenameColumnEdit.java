package org.datavyu.undoableedits;

import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.db.Variable;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

/** Renaming a column, from the Code Editor, typed a key at a time. */
public final class RenameColumnEdit extends TypedRenameEdit implements GuardedEdit {

    private final Variable column;

    /**
     * Records a column that has just been renamed.
     *
     * @param column     the column
     * @param from       its name before
     * @param continuing whether the rename continues the last one, which it then joins
     */
    public RenameColumnEdit(final Variable column, final String from, final boolean continuing) {
        super(from, column.getName(), continuing);
        this.column = column;
    }

    @Override
    boolean sameTarget(final TypedRenameEdit other) {
        return other instanceof RenameColumnEdit rename && rename.column.equals(column);
    }

    @Override
    String noun() {
        return "Column";
    }

    @Override
    public String undoRefusal() {
        return refusal(from(), "undo");
    }

    @Override
    public String redoRefusal() {
        return refusal(to(), "redo");
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
    }

    @Override
    public void redo() throws CannotRedoException {
        if (redoRefusal() != null || !canRedo()) {
            throw new CannotRedoException();
        }
        super.redo();
    }

    @Override
    void rename(final String name) {
        try {
            // A name it has had may be one Datavyu allowed and Watavyu does not, kept from an imported file.
            column.setName(name, true);
        } catch (UserWarningException e) {
            // The column is unlocked, as every later edit is undone.
            throw new IllegalStateException(e);
        }
    }
}
