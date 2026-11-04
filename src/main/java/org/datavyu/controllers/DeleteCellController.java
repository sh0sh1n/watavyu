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
package org.datavyu.controllers;

import org.datavyu.controllers.project.ProjectController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.models.db.Cell;
import org.datavyu.views.discrete.SpreadSheetPanel;

import java.util.List;

/**
 * Controller for deleting cells from the database.
 */
public final class DeleteCellController {

    /** The logger for this class. */
    private static final Logger logger = LogManager.getLogger(DeleteCellController.class);

    /**
     * Deletes cells from a spreadsheet.
     *
     * @param controller    the spreadsheet
     * @param cellsToDelete Cells to delete from the spreadsheet.
     */
    public DeleteCellController(final ProjectController controller, final List<Cell> cellsToDelete) {
        logger.info("Delete cells: " + cellsToDelete);

        SpreadSheetPanel view = controller.getSpreadSheetPanel();
        if (view != null) {
            view.deselectAll();
        }

        for (Cell cell : cellsToDelete) {
            // The last created and selected cells are forgotten if they go.
            if (cell.equals(controller.getLastCreatedCell())) {
                controller.setLastCreatedCell(null);
            }
            if (cell.equals(controller.getLastSelectedCell())) {
                controller.setLastSelectedCell(null);
            }
            controller.getDataStore().removeCell(cell);
        }
    }
}
