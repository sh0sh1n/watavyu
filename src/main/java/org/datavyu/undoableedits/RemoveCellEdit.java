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

import org.datavyu.controllers.project.ProjectController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.models.db.Cell;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import java.util.List;

/**
 * Deleting cells, which undo puts back in their columns: the edit holds the cells themselves.
 */
public class RemoveCellEdit extends SpreadSheetEdit {
    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LogManager.getLogger(RemoveCellEdit.class);

    private final List<Cell> cells;

    /**
     * Constructor.
     *
     * @param cells The cells that are being removed.
     */
    public RemoveCellEdit(final ProjectController controller, final List<Cell> cells) {
        super(controller);
        this.cells = List.copyOf(cells);
    }

    @Override
    public String getPresentationName() {
        if (cells.size() > 1) {
            return "Delete " + cells.size() + " Cells";
        }
        return "Delete Cell (" + String.join(",", cells.get(0).getValues()) + ")";
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        LOGGER.info("Redoing remove cells");
        // Undo put these very cells back, so they are removed themselves, not looked up by their times and value.
        model.deselectAll();
        controller.removeCells(cells);
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        LOGGER.info("Undoing remove cells");
        for (Cell cell : cells) {
            cell.getVariable().addCell(cell);
        }
    }
}
