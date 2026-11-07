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

import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;

/**
 * An undoable edit for changing the offset of a cell.
 */
public class ChangeOffsetCellEdit extends ChangeCellEdit {
    /** The offset to put back, swapped with the cell's on each undo and redo. */
    private long oldOffset;
    private final long newOffset;

    public ChangeOffsetCellEdit(final ProjectController controller, final Cell c, final long oldOffset,
                               final long newOffset) {
        super(controller, c);
        this.oldOffset = oldOffset;
        this.newOffset = newOffset;
    }

    @Override
    public String getPresentationName() {
        return "Changed the offset of a cell within '" + columnName + "' to " + newOffset;
    }

    @Override
    protected void updateCell(final Cell cell) {
        long current = cell.getOffset();
        cell.setOffset(oldOffset);
        oldOffset = current;
    }
}
