/*
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

import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.Variable;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

/**
 * Adding a column: an empty annotation column, from the spreadsheet's {@code +} button or the Code Editor; an empty
 * media column, from New Media Column; or a media column holding a clip, from Add Media. Undo takes the column out and
 * redo puts the same column back in its place, so later edits that hold it or its cells still find it. A column taken
 * out without an edit, as a media column whose file does not open is, leaves the edit insignificant: undo and redo pass
 * over it, changing nothing.
 */
public final class AddColumnEdit extends AbstractUndoableEdit implements GuardedEdit {

    private final DataStore dataStore;
    private final Variable column;
    private final int index;
    private final String presentationName;

    /** Whether the column was the one selected when added, as new columns are, unlike those of Add Media. */
    private final boolean selected;

    /**
     * Whether undo found the column already taken out without an edit, as a media column whose file does not open is;
     * redo then leaves it out too.
     */
    private boolean takenOut;

    /**
     * Records a column that has just been added.
     *
     * @param dataStore the spreadsheet holding the column
     * @param column    the column, in the spreadsheet
     * @throws IllegalArgumentException if the column is not in the spreadsheet
     */
    public AddColumnEdit(final DataStore dataStore, final Variable column) {
        this.dataStore = dataStore;
        this.column = column;
        this.index = dataStore.indexOf(column);
        if (index < 0) {
            throw new IllegalArgumentException("Column " + column.getName() + " is not in the spreadsheet");
        }
        selected = column.isSelected();
        if (!column.isMedia()) {
            presentationName = "New Column \"" + column.getName() + "\"";
        } else if (column.getCells().isEmpty()) {
            presentationName = "New Media Column " + column.getName();
        } else {
            presentationName = "Add Media " + column.getCells().get(0).getClip().fileName();
        }
    }

    @Override
    public String getPresentationName() {
        return presentationName;
    }

    /** @return whether undoing or redoing the edit changes anything: not once the column is taken out without one */
    @Override
    public boolean isSignificant() {
        return canUndo() ? dataStore.contains(column) : !takenOut;
    }

    /** Never refused: a column already taken out without an edit is left out. */
    @Override
    public String undoRefusal() {
        return null;
    }

    @Override
    public String redoRefusal() {
        return takenOut || dataStore.getVariable(column.getName()) == null ? null
            : "Another column is named " + column.getName() + ". Rename or delete it to redo this.";
    }

    /** Takes the column out, unless it has already been taken out without an edit. */
    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        takenOut = !dataStore.contains(column);
        if (!takenOut) {
            if (selected) {
                dataStore.deselectAll();
            }
            dataStore.removeVariable(column);
        }
    }

    /**
     * Puts the column back, selected as it was when added (the one column selected, if it was); refused, leaving the
     * edit as it was, if the spreadsheet has a column of its name.
     */
    @Override
    public void redo() throws CannotRedoException {
        if (redoRefusal() != null || !canRedo()) {
            throw new CannotRedoException();
        }
        super.redo();
        if (takenOut) {
            return;
        }
        if (selected) {
            dataStore.deselectAll();
        }
        dataStore.addVariable(column, Math.min(index, dataStore.getAllVariables().size()));
        column.setSelected(selected);
    }
}
