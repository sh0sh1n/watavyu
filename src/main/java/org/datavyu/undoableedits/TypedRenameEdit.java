package org.datavyu.undoableedits;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

/**
 * A rename typed a key at a time, each key a rename. A rename that continues the last one done joins it while no other
 * edit has followed it, even one since undone, so undo puts back the name from before the typing began. Typing back
 * to that name leaves an edit that changes nothing, which is not significant: undo and its menu item pass over it.
 */
abstract class TypedRenameEdit extends AbstractUndoableEdit {

    private final String from;
    private final boolean continuing;
    private String to;

    /** Whether another edit has followed this one, after which it joins no more renames. */
    private boolean closed;

    /**
     * @param from       the name before
     * @param to         the name now
     * @param continuing whether the rename continues the last one, which it then joins
     */
    TypedRenameEdit(final String from, final String to, final boolean continuing) {
        this.from = from;
        this.to = to;
        this.continuing = continuing;
    }

    /**
     * Tells whether another rename is of the same thing.
     *
     * @param other a rename, of any kind
     * @return whether it renames what this edit renames
     */
    abstract boolean sameTarget(TypedRenameEdit other);

    /**
     * Gives the thing renamed a name.
     *
     * @param name the name
     */
    abstract void rename(String name);

    /** Returns what is renamed, for the edit's name, such as "Code". */
    abstract String noun();

    /** Returns the name before. */
    final String from() {
        return from;
    }

    /** Returns the name now. */
    final String to() {
        return to;
    }

    @Override
    public final String getPresentationName() {
        return "Rename " + noun() + " " + from + " to " + to;
    }

    @Override
    public final boolean isSignificant() {
        return !from.equals(to);
    }

    @Override
    public final boolean addEdit(final UndoableEdit edit) {
        if (!closed && canUndo() && edit instanceof TypedRenameEdit next && next.continuing && sameTarget(next)
                && next.from.equals(to)) {
            to = next.to;
            return true;
        }
        closed = true;
        return false;
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        rename(from);
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        rename(to);
    }
}
