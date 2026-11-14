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
package com.sh0sh1n.watavyu.views.spreadsheet;

import com.sh0sh1n.watavyu.models.Cell;

/**
 * A listener for listening to cell selection changes.
 */
public interface CellSelectionListener {

    /**
     * Adds a cell to the selected cells.
     *
     * @param cell The cell to add to the selection.
     */
    void addCellToSelection(Cell cell);

    /**
     * Adds the cell and everything in between to the selected
     * cells.
     *
     * @param cell The end point cell to use for the continuous selection.
     */
    void addCellToContinousSelection(Cell cell);

    /**
     * Sets the currently selected cell.
     *
     * @param cell The cell to use as the highlight.
     */
    void setHighlightedCell(Cell cell);

    /**
     * Takes a cell out of the selection, and out of what the spreadsheet remembers as selected, as on a ⌘-click on a
     * selected cell or on deleting it.
     *
     * @param cell the cell
     */
    void removeCellFromSelection(Cell cell);

    /**
     * Notifiers the listeners to clear all the currently selected cells.
     */
    void clearCellSelection();

    void clearColumnSelection();
}
