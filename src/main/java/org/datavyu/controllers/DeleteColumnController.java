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
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.Variable;
import org.datavyu.views.discrete.SpreadSheetPanel;

import java.util.List;

/**
 * Controller for deleting cells from the database.
 */
public final class DeleteColumnController {

    /** The logger instance for this class */
    private static Logger logger = LogManager.getLogger(DeleteColumnController.class);

    /**
     * Deletes columns from a spreadsheet.
     *
     * @param controller   the spreadsheet
     * @param colsToDelete The columns to remove from the database/spreadsheet.
     */
    public DeleteColumnController(final ProjectController controller, final List<Variable> colsToDelete) {
        logger.info("delete columns");

        SpreadSheetPanel view = controller.getSpreadSheetPanel();
        DataStore dataStore = controller.getDataStore();
        if (view != null) {
            view.deselectAll();
        }

        for (Variable var : colsToDelete) {
            dataStore.removeVariable(var);
        }
        if (view != null) {
            view.revalidate();
            view.repaint();
        }
    }
}
