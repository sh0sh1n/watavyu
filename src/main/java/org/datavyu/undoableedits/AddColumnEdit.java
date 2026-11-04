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
 * Adding an empty column: an annotation column, from the spreadsheet's {@code +} button or the Code Editor, or a
 * media column. Undo takes the column out and redo puts the same column back in its place, so later edits that hold it
 * or its cells still find it.
 */
public final class AddColumnEdit extends AbstractUndoableEdit implements GuardedEdit {

    private final DataStore dataStore;
    private final Variable column;
    private final int index;

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
    }

    @Override
    public String getPresentationName() {
        return column.isMedia() ? "New Media Column " + column.getName() : "New Column \"" + column.getName() + "\"";
    }

    /** Never refused: a column already taken out without an edit is left out. */
    @Override
    public String undoRefusal() {
        return null;
    }

    @Override
    public String redoRefusal() {
        return dataStore.getVariable(column.getName()) == null ? null
            : "Another column is named " + column.getName() + ". Rename or delete it to redo this.";
    }

    /** Takes the column out, unless it has already been taken out without an edit. */
    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        if (dataStore.contains(column)) {
            dataStore.deselectAll();
            dataStore.removeVariable(column);
        }
    }

    /**
     * Puts the column back, the one column selected, as it was when new; refused, leaving the edit as it was, if the
     * spreadsheet has a column of its name.
     */
    @Override
    public void redo() throws CannotRedoException {
        if (redoRefusal() != null || !canRedo()) {
            throw new CannotRedoException();
        }
        super.redo();
        dataStore.deselectAll();
        dataStore.addVariable(column, Math.min(index, dataStore.getAllVariables().size()));
        column.setSelected(true);
    }
}
