package org.datavyu.undoableedits;

import javax.swing.undo.UndoableEdit;

/**
 * An edit that can tell, before it starts, that undoing or redoing it would be refused, as when the column it acts on
 * has been taken out, or its name taken, by a change that is not an edit (adding media, B14; renaming in the Code
 * Editor, B21). The spreadsheet's undo history asks first, so a refused edit stays where it was in the history rather
 * than being stepped past, and the user is told why.
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
