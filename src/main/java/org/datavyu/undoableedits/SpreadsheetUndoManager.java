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
    public synchronized boolean addEdit(UndoableEdit ue) {
        boolean addEdit = true;
        boolean result;
        boolean cond;
        // A new edit ends what could be redone. Dropping it first compares the new edit with the last edit done, not
        // one waiting to be redone. Every edit here is significant, so the last done is the one to be undone.
        trimEdits(edits.indexOf(editToBeUndone()) + 1, edits.size() - 1);
        if ((edits != null) && (edits.size() > 0)) {
            UndoableEdit prevEdit = edits.lastElement();
            // Do not insert duplicated edits
            if ((ue instanceof ChangeCellEdit)
                    && (ue.getClass() == prevEdit.getClass())) {
                ChangeCellEdit cce = (ChangeCellEdit) ue;
                // Remove previous finegrained ChangeCellEdit of the same type
                if (cce.getGranularity() == Granularity.COARSEGRAINED) {
                    // Take the last element
                    do {
                        Object o = edits.lastElement();
                        // Only the same cell's fine edits give way to its coarse one.
                        cond = (ue.getClass() == o.getClass())
                                && (((ChangeCellEdit) o).granularity == Granularity.FINEGRAINED)
                                && ((ChangeCellEdit) o).sameCell(cce);
                        if (cond) {
                            edits.removeElementAt(edits.size() - 1);
                        }
                    } while (cond && edits.size() > 0);
                } else if (prevEdit.equals(cce)) { // FINEGRAINED
                    addEdit = false;
                }
            }
        }
        if (addEdit && ue != null) {
            result = super.addEdit(ue);
        } else {
            result = false;
        }

        return result;
    }

    /** Undoes the last edit, unless it would refuse, which then leaves the history as it was. */
    @Override
    public void undo() throws CannotUndoException {
        // Locked as Swing's manager locks its own undo.
        synchronized (this) {
            // Swing's manager steps back before the edit undoes, so an edit that refused part way would be skipped.
            if (editToBeUndone() instanceof GuardedEdit guarded && guarded.refusesUndo()) {
                throw new CannotUndoException();
            }
            super.undo();
        }
    }

    /** Redoes the last undone edit, unless it would refuse, which then leaves the history as it was. */
    @Override
    public void redo() throws CannotRedoException {
        synchronized (this) {
            if (editToBeRedone() instanceof GuardedEdit guarded && guarded.refusesRedo()) {
                throw new CannotRedoException();
            }
            super.redo();
        }
    }
}
