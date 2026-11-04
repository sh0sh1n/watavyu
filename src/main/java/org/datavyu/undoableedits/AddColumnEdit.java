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
 * redo puts the same column back in its place, so later edits that hold it or its cells still find it.
 */
public final class AddColumnEdit extends AbstractUndoableEdit implements GuardedEdit {

    private final DataStore dataStore;
    private final Variable column;
    private final int index;
    private final String presentationName;

    /** Whether the column was the one selected when added, as new columns are, unlike those of Add Media. */
    private final boolean selected;

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

    /** Never refused: the column is in the spreadsheet, as every later edit has been undone. */
    @Override
    public String undoRefusal() {
        return null;
    }

    @Override
    public String redoRefusal() {
        return dataStore.getVariable(column.getName()) == null ? null
            : "Another column is named " + column.getName() + ". Rename or delete it to redo this.";
    }

    /** Takes the column out. */
    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        if (selected) {
            dataStore.deselectAll();
        }
        dataStore.removeVariable(column);
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
        if (selected) {
            dataStore.deselectAll();
        }
        dataStore.addVariable(column, index);
        column.setSelected(selected);
    }
}
