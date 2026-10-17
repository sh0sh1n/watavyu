package org.datavyu;

import org.datavyu.util.Dialogs;
import java.util.function.Function;
import java.util.function.Predicate;

/** What happens to documents with unsaved changes when they are closed: each is saved, discarded or kept open. */
final class UnsavedWork {

    /** The user's answer to "Save changes?". */
    enum Choice { SAVE, DISCARD, CANCEL }

    private UnsavedWork() {
    }

    /**
     * Reads the answer from an option dialog by the label of the button chosen, not its position.
     *
     * @param selected what {@code JOptionPane.showOptionDialog} returned: an index into {@code options}, or
     *                 {@code CLOSED_OPTION} when the dialog was closed with Esc or its close button
     * @param options  the dialog's button labels
     * @param save     the label of the Save button
     * @param discard  the label of the Don't Save button
     * @return the answer; a closed dialog, like any button but Save and Don't Save, cancels
     */
    static Choice choice(final int selected, final String[] options, final String save, final String discard) {
        String chosen = Dialogs.chosen(selected, options);
        if (save.equals(chosen)) {
            return Choice.SAVE;
        }
        return discard.equals(chosen) ? Choice.DISCARD : Choice.CANCEL;
    }

    /**
     * Asks about each changed document in turn.
     *
     * @param documents the documents to close
     * @param changed   whether a document has unsaved changes
     * @param ask       asks the user what to do with a changed document
     * @param save      saves a document, returning whether it was saved
     * @param <T>       the kind of document
     * @return whether all may close: false as soon as one is cancelled or fails to save
     */
    static <T> boolean mayClose(final Iterable<T> documents, final Predicate<T> changed,
                                final Function<T, Choice> ask, final Predicate<T> save) {
        for (T document : documents) {
            if (!changed.test(document)) {
                continue;
            }
            Choice choice = ask.apply(document);
            if (choice == Choice.CANCEL || (choice == Choice.SAVE && !save.test(document))) {
                return false;
            }
        }
        return true;
    }
}
