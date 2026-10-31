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

/**
 * Deleting columns from the spreadsheet. The spreadsheet and the media players hear of each column leaving and
 * coming back through the data store.
 */
public final class RemoveVariableEdit extends AbstractUndoableEdit {

    private final DataStore dataStore;
    private final List<Variable> deletedVariables;

    /**
     * Records the deletion of columns, made before or after the edit is made.
     *
     * @param dataStore    the spreadsheet that held the columns
     * @param varsToDelete the columns deleted
     */
    public RemoveVariableEdit(final DataStore dataStore, final List<Variable> varsToDelete) {
        this.dataStore = dataStore;
        this.deletedVariables = List.copyOf(varsToDelete);
    }

    @Override
    public String getPresentationName() {
        if (deletedVariables.size() == 1) {
            return "Delete Column " + deletedVariables.get(0).getName();
        }
        return "Delete " + deletedVariables.size() + " Columns";
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        for (Variable var : deletedVariables) {
            dataStore.addVariable(var);
        }
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        for (Variable var : deletedVariables) {
            dataStore.removeVariable(var);
        }
    }
}
