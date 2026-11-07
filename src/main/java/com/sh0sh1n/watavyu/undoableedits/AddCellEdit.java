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
package com.sh0sh1n.watavyu.undoableedits;

import com.sh0sh1n.watavyu.controllers.project.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import java.util.List;

/**
 * An undoable edit for adding a cell to the spreadsheet.
 */
public class AddCellEdit extends SpreadSheetEdit {
    /**
     * Column name *
     */
    private String varName;
    private Cell addedCell;

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

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        addedCell.getColumn().addCell(addedCell);
        controller.setLastCreatedCell(addedCell);
        unselectAll();
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        controller.removeCells(List.of(addedCell));
    }
}
