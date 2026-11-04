package org.datavyu.undoableedits;

import javax.swing.undo.UndoableEdit;

/**
 * An edit that can tell, before it starts, that undoing or redoing it would be refused: when putting a column back
 * would clash with another column's name, which a change that is not an edit can take (renaming in the Code Editor,
 * B21). The spreadsheet's undo history asks first, so a refused edit stays where it was in the history
 * rather than being stepped past, and the user is told why and can rename or delete the other column. An edit whose
 * column has been taken out without an edit, as a media column whose file fails to open is, is not refused: there is
 * nothing for it to change, so it changes nothing, and the history is never stuck.
 */
public interface GuardedEdit extends UndoableEdit {

    /**
     * Tells why undoing the edit now would be refused.
     *
     * @return the reason, for the user, or null if it would not be refused
     */
    String undoRefusal();

    /**
     * Tells why redoing the edit now would be refused.
     *
     * @return the reason, for the user, or null if it would not be refused
     */
    String redoRefusal();
}
