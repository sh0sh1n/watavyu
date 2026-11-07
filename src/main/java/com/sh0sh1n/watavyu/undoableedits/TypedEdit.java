package com.sh0sh1n.watavyu.undoableedits;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

/**
 * A change typed a key at a time, each key a change, such as a name or a cell's values. A change that continues the
 * last one done joins it while no other edit has followed it, even one since undone, so undo puts back what there was
 * before the typing began. Typing back to that leaves an edit that changes nothing, which is not significant: undo and
 * its menu item pass over it.
 *
 * @param <T> what is typed, compared by {@link Object#equals}
 */
abstract class TypedEdit<T> extends AbstractUndoableEdit {

    private final T before;
    private final boolean continuing;
    private T after;

    /** Whether another edit has followed this one, after which it joins no more changes. */
    private boolean closed;

    /**
     * @param from       what there was before
     * @param to         what there is now
     * @param continuing whether the change continues the last one, which it then joins
     */
    TypedEdit(final T from, final T to, final boolean continuing) {
        this.before = from;
        this.after = to;
        this.continuing = continuing;
    }

    /**
     * Returns another edit as one of this edit's class, which this edit may join.
     *
     * @param edit another edit
     * @return it, or null if it is of another class
     */
    abstract TypedEdit<T> sameKind(UndoableEdit edit);

    /**
     * Tells whether another change is of the same thing.
     *
     * @param other a change of the same class
     * @return whether it changes what this edit changes
     */
    abstract boolean sameTarget(TypedEdit<T> other);

    /**
     * Puts what there was, or is, back.
     *
     * @param state what to put back
     */
    abstract void apply(T state);

    /** Returns what there was before. */
    final T from() {
        return before;
    }

    /** Returns what there is now. */
    final T to() {
        return after;
    }

    @Override
    public final boolean isSignificant() {
        return !before.equals(after);
    }

    /**
     * Joins a change that continues this one. The undo history offers a new edit only to the last edit done, having
     * dropped those undone, so this edit is never joined once undone.
     */
    @Override
    public final boolean addEdit(final UndoableEdit edit) {
        TypedEdit<T> next = sameKind(edit);
        if (!closed && next != null && next.continuing && sameTarget(next) && next.before.equals(after)) {
            after = next.after;
            return true;
        }
        closed = true;
        return false;
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        apply(before);
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        apply(after);
    }
}
