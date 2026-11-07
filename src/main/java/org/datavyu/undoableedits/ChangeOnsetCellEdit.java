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
import org.datavyu.models.db.Cell;

/**
 * An undoable edit for changing the onset of a cell.
 */
public class ChangeOnsetCellEdit extends ChangeCellEdit {
    /** The onset to put back, swapped with the cell's on each undo and redo. */
    private long oldOnset;
    private final long newOnset;

    public ChangeOnsetCellEdit(final ProjectController controller, final Cell c, final long oldOnset,
                               final long newOnset) {
        super(controller, c);
        this.oldOnset = oldOnset;
        this.newOnset = newOnset;
    }

    @Override
    public String getPresentationName() {
        return "Changed the onset of a cell within '" + columnName + "' to " + newOnset;
    }

    @Override
    protected void updateCell(final Cell cell) {
        long current = cell.getOnset();
        cell.setOnset(oldOnset);
        oldOnset = current;
    }
}
