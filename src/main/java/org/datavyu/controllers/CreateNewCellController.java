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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.Variable;
import org.datavyu.undoableedits.AddCellEdit;
import org.datavyu.undoableedits.ChangeCellEdit;
import org.datavyu.undoableedits.ChangeOffsetCellEdit;
import org.datavyu.util.ArrayDirection;

import javax.swing.undo.UndoableEdit;
import java.util.ArrayList;
import java.util.List;


/**
 * Controller for creating new cell.
 */
public final class CreateNewCellController {

    /** The logger instance for this class */
    private static Logger logger = LogManager.getLogger(CreateNewCellController.class);

    /** The spreadsheet the cells are created in */
    private final ProjectController controller;

    /** The model (the database) for this controller */
    private DataStore model;

    /**
     * @param controller the spreadsheet the cells are created in
     */
    public CreateNewCellController(final ProjectController controller) {
        this.controller = controller;
        model = controller.getDataStore();
    }

    /**
     * Create New Cell Controller - creates new cells in columns adjacent to the
     * supplied cells. If no column is adjacent in the specified direction, no
     * cell will be created.
     *
     * @param controller  the spreadsheet the cells are created in
     * @param sourceCells The list of source cells that we wish to create cells
     *                    adjacent too.
     * @param direction   The direction in which we wish to create adjacent cells.
     */
    public CreateNewCellController(final ProjectController controller, final List<Cell> sourceCells,
                                   final ArrayDirection direction) {
        this(controller);

        Cell newCell = null;

        logger.info("create adjacent cells:" + direction);

        // Get the column that is the parent of the source cell.
        for (Cell sourceCell : sourceCells) {

            Variable sourceColumn = model.getVariable(sourceCell);
            //long sourceColumn = sourceCell.getItsColID();
            //Vector<Long> columnOrder = modelAsLegacyDB().getColOrderVector();

            for (int i = 0; i < model.getVisibleVariables().size(); i++) {

                // Found the source column in the order column.
                if (model.getVisibleVariables().get(i).equals(sourceColumn)) {
                    i = i + direction.getModifier();

                    // Only create the cell if a valid column exists.
                    if ((i >= 0) && (i < model.getVisibleVariables().size())
                            && !model.getVisibleVariables().get(i).isMedia()) {
                        Variable var = model.getVisibleVariables().get(i);
                        if (var.refusesEdits()) {
                            break;
                        }
                        newCell = var.createCell();
                        newCell.setOnset(sourceCell.getOnset());
                        newCell.setOffset(sourceCell.getOffset());
                        controller.setLastCreatedCell(newCell);

                        // Add the undoable action
                        UndoableEdit edit = new AddCellEdit(controller, var.getName(), newCell);

                        // notify the listeners
                        controller.postEdit(edit);
                        newCell.setHighlighted(true);
                        break;
                    }
                    break;
                }
            }
        }
    }

    /**
     * Constructor - creates new controller.
     *
     * @param controller    the spreadsheet the cell is created in
     * @param milliseconds The milliseconds to use for the onset for the new cell.
     * @param setPrevOffset Determine whether or not to set previous offset to milliseconds-1
     */
    public CreateNewCellController(final ProjectController controller, final long milliseconds,
                                   boolean setPrevOffset) {
        this(controller);

        //We see if setPrevOffset is true
        if(setPrevOffset){
            //To avoid conflict when having multiple column selected, we use the Column of the selected cell
            if(model.getSelectedVariables().size() == 1){
                //We know that we have only one selected column
                if (model.getSelectedVariables().get(0).refusesEdits()) {
                    return;
                }
                Cell cellToEdit = findClosestCell(model.getSelectedVariables().get(0),milliseconds);
                if(cellToEdit != null && cellToEdit.getClip() == null) {
                    UndoableEdit edit = new ChangeOffsetCellEdit(controller, cellToEdit, cellToEdit.getOffset(),
                            milliseconds - 1, ChangeCellEdit.Granularity.FINEGRAINED);
                    controller.postEdit(edit);
                    cellToEdit.setOffset(Math.max(0, (milliseconds - 1)));
                }
            }else{
                //There is more than one selected column; we parse the column of the selected cell
                if(controller.getLastSelectedCell() != null){
                    Variable columnToParse = model.getVariable(controller.getLastSelectedCell());
                    if (columnToParse != null && columnToParse.refusesEdits()) {
                        return;
                    }
                    Cell cellToEdit = findClosestCell(columnToParse, milliseconds);
                    if (cellToEdit == null || cellToEdit.getClip() != null) {
                        createNewCell(milliseconds);
                        return;
                    }
                    UndoableEdit edit = new ChangeOffsetCellEdit(controller, cellToEdit, cellToEdit.getOffset(),
                            milliseconds - 1, ChangeCellEdit.Granularity.FINEGRAINED);
                    controller.postEdit(edit);
                    cellToEdit.setOffset(Math.max(0, (milliseconds - 1)));
                }
            }
        }
        // Create the new cell.
        createNewCell(milliseconds);
    }

    //need to refactor this
    private Cell findClosestCell(final Variable column,final long timeInMillis) {
        if (column.getCells().size() == 0) return null;

        //find the closest onset to the current time of the VideoController
        List<Cell> cells = new ArrayList<>();
        List<Cell> cellsToEdit = new ArrayList<>();
        //Go through the cells temporally and add them to a list of cell
        for (Cell cell : column.getCellsTemporally() ){
            if(cell.getOnset() <= timeInMillis){
                cells.add(cell);
            }
        }

        //Go backward through the list of cells and get the last cells with the same onset
        long lastOnSet = cells.get(cells.size()-1).getOnset();
        for(int i = cells.size()-1; i >= 0; i--){
            if(cells.get(i).getOnset() == lastOnSet){
                cellsToEdit.add(cells.get(i));
            }
        }

        //If there is more than one cell with the same onset and close to the current time
        //and if one of the cell is selected, we will use the selected cell
        //if none of the closest cell is selected
        if(cellsToEdit.size() == 1){
            return cellsToEdit.get(0);
        } else if (cellsToEdit.size() > 1) {
            //find the selected cell if there is any
            for(Cell cell : cellsToEdit){
                if(cell.equals(controller.getLastSelectedCell())){
                    return cell;
                }
            }
        }

        return controller.getLastSelectedCell();
    }

    /**
     * Inserts a cell into the end of the supplied variable.
     *
     * @param v The variable that we want to add a cell too.
     * @return The cell that was just inserted.
     */
    /**
     * @return a new point cell at the current time, or null in a media column, whose cells are clips, or in a locked
     * column
     */
    public Cell createCell(final Variable v) {
        if (v.isMedia() || v.refusesEdits()) {
            return null;
        }
        logger.info("create cell in selected column");

        // perform the operation
        List<Cell> cells = v.getCellsTemporally();

        long newOnset = 0;
        newOnset = controller.getVideoController().getCurrentTime();

        Cell newCell = v.createPointCell(newOnset);
        controller.setLastCreatedCell(newCell);
        controller.setLastCreatedVariable(v);

        return newCell;
    }

    /**
     * Create a default cell
     *
     * @param preferFirstSelected prefer the first selected variable
     */
    public void createDefaultCell(boolean preferFirstSelected) {
        Cell newCell = null;
        Variable v = controller.getLastCreatedVariable();
        if (preferFirstSelected) {
            List<Variable> vlist = controller.getDataStore().getSelectedVariables();
            if (!vlist.isEmpty()) {
                v = vlist.get(0);
            }
        }

        if (v != null && !v.isMedia()) {
            newCell = createCell(v);
        }
        if (newCell != null) {
            // record the effect
            UndoableEdit edit = new AddCellEdit(controller, v.getName(), newCell);
            if (controller.getSpreadSheetPanel() != null) {
                controller.getSpreadSheetPanel().revalidate();
            }
            controller.postEdit(edit);
            model.deselectAll();
            newCell.setHighlighted(true);
        }
    }

    /**
     * Create a default cell
     */
    public void createDefaultCell() {
        createDefaultCell(false);
    }

    /**
     * Create a new cell with given onset. Currently just appends to the
     * selected column or the column that last had a cell added to it.
     *
     * @param milliseconds The number of milliseconds since the origin of the
     *                     spreadsheet to create a new cell from.
     */
    public void createNewCell(final long milliseconds) {

        /*
         * Concept of operation: Creating a new cell.
         *
         * Situation 1: Spreadsheet has one or more selected columns For each
         * selected column do Create a new cell with the supplied onset and
         * insert into db.
         *
         * Situation 2: Spreadsheet has one or more selected cells For each
         * selected cell do Create a new cell with the selected cell onset and
         * offset and insert into the db.
         *
         * Situation 3: User has set focus on a particular cell in the
         * spreadsheet - the caret is or has been in one of the editable parts
         * of a spreadsheet cell. First check this request has not come from the
         * video controller. For the focussed cell do Create a new cell with the
         * focussed cell onset and offset and insert into the db.
         *
         * Situation 4: Request has come from the video controller and there is
         * no currently selected column. Create a new cell in the same column as
         * the last created cell or the last focussed cell.
         */
        long onset = milliseconds;

        // If not coming from video controller (milliseconds < 0) allow
        // multiple adds
        boolean multiadd = (milliseconds < 0);

        if (milliseconds < 0) {
            onset = 0;
        }

        Cell newCell = null;
        boolean newcelladded = false;
        // A locked column refuses the new cell, and no other column takes it instead.
        boolean refused = false;

        // check for Situation 1: one or more selected columns

        for (Variable var : model.getSelectedVariables()) {
            if (var.isMedia()) {
                continue;
            }
            if (var.refusesEdits()) {
                refused = true;
                if (!multiadd) {
                    break;
                }
                continue;
            }
            logger.info("create cell in selected column");
            newCell = var.createPointCell(onset);
            controller.setLastCreatedCell(newCell);
            controller.setLastCreatedVariable(var);

            // Add the undoable action
            UndoableEdit edit = new AddCellEdit(controller, var.getName(), newCell);
            controller.postEdit(edit);

            newcelladded = true;

            if (!multiadd) {
                break;
            }
        }

        if (!newcelladded && !refused) {
            for (Cell cell : model.getSelectedCells()) {
                logger.info("create cell below selected cell");

                // reget the selected cell from the database using its identifier
                // in case a previous insert has changed its ordinal.
                // recasting to DataCell without checking as the iterator
                // only returns DataCells (no ref cells allowed so far)
                Variable var = model.getVariable(cell);
                if (var.isMedia()) {
                    continue;
                }
                if (var.refusesEdits()) {
                    refused = true;
                    if (!multiadd) {
                        break;
                    }
                    continue;
                }
                newCell = var.createPointCell(onset);
                controller.setLastCreatedCell(newCell);
                controller.setLastCreatedVariable(var);

                // Add the undoable action
                UndoableEdit edit = new AddCellEdit(controller, var.getName(), newCell);
                controller.postEdit(edit);

                newcelladded = true;

                if (!multiadd) {
                    break;
                }
            }
        }

        // else check for Situation 3: User is or was editing an existing cell
        // and has requested a new cell
        if (!newcelladded && !refused && multiadd) {
            if (controller.getLastSelectedCell() != null) {
                logger.info("create cell while editing existing cell");
                Variable var = model.getVariable(controller.getLastCreatedCell());
                if (var != null && var.refusesEdits()) {
                    refused = true;
                } else if (var != null && !var.isMedia()) {
                    newCell = var.createPointCell(onset);
                    controller.setLastCreatedCell(newCell);
                    controller.setLastCreatedVariable(var);

                    // Add the undoable action
                    UndoableEdit edit = new AddCellEdit(controller, var.getName(), newCell);
                    controller.postEdit(edit);

                    newcelladded = true;
                }
            }
        }

        // else go with Situation 4: Video controller requested - create in the
        // same column as the last created cell or the last focused cell.
        if (!newcelladded && !refused) {
            logger.info("create cell in same location as last created cell");

            // Check for presence of columns, else return
            if (model.getAllVariables().isEmpty()) {
                return;
            }

            Variable last = controller.getLastCreatedVariable();
            if (last == null || last.isMedia()) {
                // Media columns' cells are clips; a new cell goes in the first annotation column.
                last = model.getAllVariables().stream().filter(variable -> !variable.isMedia()).findFirst()
                    .orElse(null);
                if (last == null) {
                    return;
                }
                controller.setLastCreatedVariable(last);
            }
            if (last.refusesEdits()) {
                return;
            }

            newCell = controller.getLastCreatedVariable().createPointCell(onset);
            controller.setLastCreatedCell(newCell);

            // Add the undoable action
            UndoableEdit edit = new AddCellEdit(controller, controller.getLastCreatedVariable().getName(), newCell);
            controller.postEdit(edit);
        }

        if (newCell == null) {
            return;
        }
        model.deselectAll();
        newCell.setHighlighted(true);
    }
}
