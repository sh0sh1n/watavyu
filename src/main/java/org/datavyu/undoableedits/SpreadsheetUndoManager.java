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

import org.datavyu.undoableedits.ChangeCellEdit.Granularity;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;

/**
 * Undo manager for spreadsheet edits that merges consecutive fine-grained cell edits.
 */
public class SpreadsheetUndoManager extends UndoManager {

    @Override
    public synchronized boolean addEdit(final UndoableEdit ue) {
        if (ue == null) {
            return false;
        }
        // Compared with the last edit done: Swing's manager drops any waiting to be redone as the new edit is added.
        UndoableEdit lastDone = editToBeUndone();
        if (ue instanceof ChangeCellEdit change && lastDone != null && ue.getClass() == lastDone.getClass()) {
            if (change.getGranularity() == Granularity.COARSEGRAINED) {
                // The end of the history is the last edit done only while nothing waits to be redone.
                if (!canRedo()) {
                    dropFineEditsBefore(change);
                }
            } else if (lastDone.equals(change)) {
                // A fine edit repeating the last one done is a duplicate.
                return false;
            }
        }
        return super.addEdit(ue);
    }

    /** Drops the fine edits of the same cell, and of the same kind, that end the history: the coarse edit replaces them. */
    private void dropFineEditsBefore(final ChangeCellEdit coarse) {
        while (!edits.isEmpty() && edits.lastElement() instanceof ChangeCellEdit last
                && last.getClass() == coarse.getClass() && last.getGranularity() == Granularity.FINEGRAINED
                && last.sameCell(coarse)) {
            edits.removeElementAt(edits.size() - 1);
        }
    }

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
