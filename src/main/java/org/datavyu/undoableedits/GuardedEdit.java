package org.datavyu.undoableedits;

import javax.swing.undo.UndoableEdit;

/**
 * An edit that can tell, before it starts, that undoing or redoing it would be refused, as when the column it acts on
 * has gone. The spreadsheet's undo history asks first, so a refused edit stays where it was in the history rather than
 * being stepped past.
 */
public interface GuardedEdit extends UndoableEdit {

    /**
     * Tells whether undoing the edit now would be refused.
     *
     * @return whether it would be refused
     */
    boolean refusesUndo();

    /**
     * Tells whether redoing the edit now would be refused.
     *
     * @return whether it would be refused
     */
    boolean refusesRedo();
}
