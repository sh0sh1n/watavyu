/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.datavyu.undoableedits;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

/**
 * A spreadsheet's undo history, whose edits can refuse to undo or redo ({@link GuardedEdit}), leaving the history as
 * it was. Typing joins the edit it continues ({@link TypedEdit}).
 */
public class SpreadsheetUndoManager extends UndoManager {

    /**
     * Tells why the last edit cannot be undone now.
     *
     * @return the reason, for the user, or null if it can be undone or there is none
     */
    public String undoRefusal() {
        synchronized (this) {
            return editToBeUndone() instanceof GuardedEdit guarded ? guarded.undoRefusal() : null;
        }
    }

    /**
     * Tells why the last undone edit cannot be redone now.
     *
     * @return the reason, for the user, or null if it can be redone or there is none
     */
    public String redoRefusal() {
        synchronized (this) {
            return editToBeRedone() instanceof GuardedEdit guarded ? guarded.redoRefusal() : null;
        }
    }

    /** Undoes the last edit, unless it would refuse, which then leaves the history as it was. */
    @Override
    public void undo() throws CannotUndoException {
        // Locked as Swing's manager locks its own undo.
        synchronized (this) {
            // Swing's manager steps back before the edit undoes, so an edit that refused part way would be skipped.
            if (undoRefusal() != null) {
                throw new CannotUndoException();
            }
            super.undo();
        }
    }

    /** Redoes the last undone edit, unless it would refuse, which then leaves the history as it was. */
    @Override
    public void redo() throws CannotRedoException {
        synchronized (this) {
            if (redoRefusal() != null) {
                throw new CannotRedoException();
            }
            super.redo();
        }
    }
}
