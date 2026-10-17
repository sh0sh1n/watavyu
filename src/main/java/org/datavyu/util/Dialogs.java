package org.datavyu.util;

import java.awt.Component;
import javax.swing.JOptionPane;

/** Option dialogs whose answer is read by the label of the button chosen, never by its position. */
public final class Dialogs {

    /** The label of the button that does nothing. */
    public static final String CANCEL = "Cancel";

    private Dialogs() {
    }

    /**
     * Reads which button of an option dialog was chosen.
     *
     * @param selected what {@code JOptionPane.showOptionDialog} returned: an index into {@code options}, or
     *                 {@code CLOSED_OPTION} when the dialog was closed with Esc or its close button
     * @param options  the dialog's button labels
     * @return the label of the button chosen, or null if the dialog was closed
     */
    public static String chosen(final int selected, final String[] options) {
        return selected >= 0 && selected < options.length ? options[selected] : null;
    }

    /**
     * Asks whether to go ahead with an action, offering the action and Cancel, which is the default.
     *
     * @param parent  the window the dialog belongs to
     * @param message the question
     * @param title   the dialog's title
     * @param action  the label of the button that goes ahead, such as "Overwrite"
     * @return whether the action was chosen; closing the dialog cancels
     */
    public static boolean confirm(final Component parent, final String message, final String title,
                                  final String action) {
        String[] options = {CANCEL, action};
        int selected = JOptionPane.showOptionDialog(parent, message, title, JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, options, CANCEL);
        return action.equals(chosen(selected, options));
    }
}
