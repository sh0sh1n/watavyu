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
import com.sh0sh1n.watavyu.models.db.Cell;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

/**
 * An undoable edit of one cell's time, which undo and redo swap with the cell's time then.
 */
abstract public class ChangeCellEdit extends SpreadSheetEdit {
    /**
     * The cell changed. Undo and redo put the same cell back, so it is held itself rather than found by its place in
     * its column, which changes whenever the column is sorted by time.
     */
    private final Cell cell;

    protected final String columnName;

    protected ChangeCellEdit(final ProjectController controller, final Cell c) {
        super(controller);
        cell = c;
        columnName = c.getVariable().getName();
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        updateCell(cell);
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        updateCell(cell);
    }

    /**
     * Swaps the time held with the cell's.
     *
     * @param cell the cell
     */
    abstract protected void updateCell(Cell cell);
}
