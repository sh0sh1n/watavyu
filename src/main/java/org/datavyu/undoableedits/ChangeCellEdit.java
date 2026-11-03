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
import org.datavyu.util.Constants;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

/**
 * An undoable edit for altering the contents of a cell.
 */
abstract public class ChangeCellEdit extends SpreadSheetEdit {
    /**
     * The cell changed. Undo and redo put the same cell back, so it is held itself rather than found by its place in
     * its column, which changes whenever the column is sorted by time.
     */
    private final Cell cell;

    protected String columnName;

    /**
     * The granularity of the edit - fine or coarse.
     */
    protected Granularity granularity;

    protected ChangeCellEdit(final ProjectController controller, Cell c, Granularity granularity) {
        super(controller);
        this.granularity = granularity;

        cell = c;
        columnName = c.getVariable().getName();
    }

    protected ChangeCellEdit(final ProjectController controller, Cell c) {
        this(controller, c, Granularity.COARSEGRAINED);
    }

    public Granularity getGranularity() {
        return granularity;
    }

    @Override
    public String getPresentationName() {
        return "Change " + " ";
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        updateCell();
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        updateCell();
    }

    protected void updateCell() {
        updateCell(cell);
    }

    /**
     * @param other another edit of a cell
     * @return whether it changed the same cell; edits of different cells are never duplicates of each other
     */
    protected boolean sameCell(final ChangeCellEdit other) {
        return other.cell == cell;
    }

    abstract protected void updateCell(Cell cell);

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof ChangeCellEdit other) && other.granularity == this.granularity && sameCell(other);
    }

    @Override
    public int hashCode() {
        double hash = super.hashCode();
        hash += granularity.hashCode() * Constants.SEED1;
        long val = Double.doubleToLongBits(hash);
        return (int) (val ^ (val >>> 32));
    }

    public enum Granularity {
        FINEGRAINED,
        COARSEGRAINED
    }
}
