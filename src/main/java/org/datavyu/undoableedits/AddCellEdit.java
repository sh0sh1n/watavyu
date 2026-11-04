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

import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.Cell;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import java.util.List;

/**
 * An undoable edit for adding a cell to the spreadsheet.
 */
public class AddCellEdit extends SpreadSheetEdit {
    /**
     * Variable name *
     */
    private String varName;
    private Cell addedCell;

    /**
     * Whether undo found the cell already taken out without an edit, as a clip whose file does not open is; redo then
     * leaves it out too.
     */
    private boolean takenOut;

    public AddCellEdit(final ProjectController controller, String varName, Cell c) {
        super(controller);
        this.varName = varName;
        this.addedCell = c;
    }

    @Override
    public String getPresentationName() {
        if (addedCell.getClip() != null) {
            return "Add Media " + addedCell.getClip().fileName();
        }
        return "New Cell to " + varName;
    }

    /** @return whether undoing or redoing the edit changes anything: not once the cell is taken out without one */
    @Override
    public boolean isSignificant() {
        return canUndo() ? inSpreadsheet(addedCell) : !takenOut;
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        if (takenOut) {
            return;
        }
        addedCell.getVariable().addCell(addedCell);
        controller.setLastCreatedCell(addedCell);
        unselectAll();
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        takenOut = !inSpreadsheet(addedCell);
        controller.removeCells(List.of(addedCell));
    }
}
