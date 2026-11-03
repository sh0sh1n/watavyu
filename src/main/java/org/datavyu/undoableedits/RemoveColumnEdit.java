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

import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.Variable;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Deleting columns from the spreadsheet. Undo puts the same columns back where they were, so later edits that hold
 * them or their cells still find them. The spreadsheet and the media players hear of each column leaving and coming
 * back through the data store.
 */
public final class RemoveColumnEdit extends AbstractUndoableEdit {

    private final DataStore dataStore;

    /** The columns deleted, by their place in the spreadsheet order before they were deleted. */
    private final SortedMap<Integer, Variable> deleted = new TreeMap<>();

    /**
     * Records the deletion of columns that are about to be deleted.
     *
     * @param dataStore the spreadsheet holding the columns
     * @param columns   the columns, still in the spreadsheet
     * @throws IllegalArgumentException if a column is not in the spreadsheet
     */
    public RemoveColumnEdit(final DataStore dataStore, final List<Variable> columns) {
        this.dataStore = dataStore;
        for (Variable column : columns) {
            int index = dataStore.indexOf(column);
            if (index < 0) {
                throw new IllegalArgumentException("Column " + column.getName() + " is not in the spreadsheet");
            }
            deleted.put(index, column);
        }
    }

    @Override
    public String getPresentationName() {
        if (deleted.size() == 1) {
            return "Delete Column " + deleted.values().iterator().next().getName();
        }
        return "Delete " + deleted.size() + " Columns";
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        // From the first place to the last, so each column goes back between those it was between.
        deleted.forEach((index, column) -> dataStore.addVariable(column, index));
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        dataStore.deselectAll();
        deleted.values().forEach(dataStore::removeVariable);
    }
}
