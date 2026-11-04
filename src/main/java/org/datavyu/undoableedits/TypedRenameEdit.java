package org.datavyu.undoableedits;

import javax.swing.undo.UndoableEdit;

/** A rename typed a key at a time, each key a rename, which joins the renames it continues ({@link TypedEdit}). */
abstract class TypedRenameEdit extends TypedEdit<String> {

    /**
     * @param from       the name before
     * @param to         the name now
     * @param continuing whether the rename continues the last one, which it then joins
     */
    TypedRenameEdit(final String from, final String to, final boolean continuing) {
        super(from, to, continuing);
    }

    @Override
    final TypedEdit<String> sameKind(final UndoableEdit edit) {
        return edit != null && edit.getClass() == getClass() ? (TypedRenameEdit) edit : null;
    }

    /** Returns what is renamed, for the edit's name, such as "Code". */
    abstract String noun();

    @Override
    public final String getPresentationName() {
        return "Rename " + noun() + " " + from() + " to " + to();
    }
}
