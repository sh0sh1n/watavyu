package com.sh0sh1n.watavyu.undoableedits;

import javax.swing.undo.UndoableEdit;

/**
 * An edit that can tell, before it starts, that undoing or redoing it would be refused: when putting a column or its
 * name back would clash with another column's name. Every user action that names a column is an edit, so only a change
 * made outside the commands can take a name, but the history is kept sound against one. The spreadsheet's undo history
 * asks first, so a refused edit stays where it was in the history rather than being stepped past, and the user is told
 * why and can rename or delete the other column.
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
