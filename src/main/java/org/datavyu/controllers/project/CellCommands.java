package org.datavyu.controllers.project;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.Variable;
import org.datavyu.undoableedits.AddCellEdit;
import org.datavyu.undoableedits.ChangeCellEdit.Granularity;
import org.datavyu.undoableedits.ChangeOffsetCellEdit;
import org.datavyu.undoableedits.ChangeOnsetCellEdit;
import org.datavyu.undoableedits.RemoveCellEdit;
import org.datavyu.util.ArrayDirection;

import javax.swing.undo.UndoableEdit;
import java.util.ArrayList;
import java.util.List;

/**
 * The commands that make, delete and time one spreadsheet's cells. Each is the one path for its user action, whether it
 * comes from a menu, a key, the controls drawer or quick keys: it leaves locked columns and media clips alone, changes
 * the data model, and records what it did in the spreadsheet's undo history. Times are given by the caller, as the
 * playback's current time.
 */
public final class CellCommands {

    private static final Logger logger = LogManager.getLogger(CellCommands.class);

    private final ProjectController spreadsheet;

    /**
     * @param spreadsheet the spreadsheet whose cells the commands change
     */
    CellCommands(final ProjectController spreadsheet) {
        this.spreadsheet = spreadsheet;
    }

    private DataStore dataStore() {
        return spreadsheet.getDataStore();
    }

    /**
     * New Cell, from the Spreadsheet menu: a point cell at the time in the annotation column last given a cell or
     * edited.
     *
     * @param time the new cell's onset and offset
     * @return the new cell, or null if there is no such column or it is locked
     */
    public Cell newCell(final long time) {
        return newCellIn(spreadsheet.getLastCreatedVariable(), time);
    }

    /**
     * New Cell, from the keypad and the controls drawer: a point cell at the time in the first selected column, or
     * with none selected, as {@link #newCell(long)}.
     *
     * @param time the new cell's onset and offset
     * @return the new cell, or null if that column is a media column or is locked
     */
    public Cell newCellInSelectedColumn(final long time) {
        List<Variable> selected = selectedColumns();
        return newCellIn(selected.isEmpty() ? spreadsheet.getLastCreatedVariable() : selected.get(0), time);
    }

    /** @return the selected columns, in spreadsheet order */
    private List<Variable> selectedColumns() {
        return dataStore().getAllVariables().stream().filter(Variable::isSelected).toList();
    }

    /** @return the selected cells, by column in spreadsheet order and then by time */
    private List<Cell> selectedCells() {
        List<Cell> selected = new ArrayList<>();
        for (Variable column : dataStore().getAllVariables()) {
            column.getCellsTemporally().stream().filter(Cell::isSelected).forEach(selected::add);
        }
        return selected;
    }

    /**
     * New Cell and Set Offset, from the keypad and the controls drawer: closes the cell before the time, setting its
     * offset to a millisecond before it, and makes a point cell at the time. The cell closed is in the one selected
     * column, or else in the column of the cell last selected: of its cells starting at or before the time, the
     * latest, and among several starting then, the one last selected, or else the last of them. The new cell goes in
     * the first selected annotation column, else the column of the first selected cell, else the column last given a
     * cell, else the first annotation column.
     *
     * @param time the new cell's onset and offset
     * @return the new cell, or null if the column to close or the one for the new cell is locked
     */
    public Cell newCellClosingPrevious(final long time) {
        List<Variable> selected = selectedColumns();
        Cell lastSelected = spreadsheet.getLastSelectedCell();
        Variable closing = selected.size() == 1 ? selected.get(0) : lastSelected == null ? null
            : lastSelected.getVariable();
        if (closing != null) {
            if (closing.refusesEdits()) {
                return null;
            }
            Cell previous = cellToClose(closing, time);
            if (previous != null && previous.getClip() == null) {
                long offset = Math.max(0, time - 1);
                record(new ChangeOffsetCellEdit(spreadsheet, previous, previous.getOffset(), offset,
                    Granularity.FINEGRAINED));
                previous.setOffset(offset);
            }
        }
        return newCellIn(columnForNewCellAt(), time);
    }

    /** @return the cell to close before the time, as {@link #newCellClosingPrevious(long)} picks it, or null */
    private Cell cellToClose(final Variable column, final long time) {
        List<Cell> started = column.getCellsTemporally().stream().filter(cell -> cell.getOnset() <= time).toList();
        if (started.isEmpty()) {
            return null;
        }
        long latest = started.get(started.size() - 1).getOnset();
        List<Cell> latestCells = started.stream().filter(cell -> cell.getOnset() == latest).toList();
        Cell lastSelected = spreadsheet.getLastSelectedCell();
        return latestCells.contains(lastSelected) ? lastSelected : latestCells.get(latestCells.size() - 1);
    }

    /** @return the column for a new cell at a time, as {@link #newCellClosingPrevious(long)} picks it, or null */
    private Variable columnForNewCellAt() {
        for (Variable column : selectedColumns()) {
            if (!column.isMedia()) {
                return column;
            }
        }
        for (Cell cell : selectedCells()) {
            if (!cell.getVariable().isMedia()) {
                return cell.getVariable();
            }
        }
        Variable last = spreadsheet.getLastCreatedVariable();
        if (inSpreadsheet(last) && !last.isMedia()) {
            return last;
        }
        return dataStore().getAllVariables().stream().filter(column -> !column.isMedia()).findFirst().orElse(null);
    }

    /** Makes a point cell in a column, which is then the one last given a cell, and records it; null if refused. */
    private Cell newCellIn(final Variable column, final long time) {
        Cell cell = createPointCell(column, time);
        if (cell != null) {
            dataStore().deselectAll();
            cell.setHighlighted(true);
        }
        return cell;
    }

    /** @return whether a column is in the spreadsheet, as one remembered as last given a cell may have been deleted */
    private boolean inSpreadsheet(final Variable column) {
        return column != null && dataStore().getAllVariables().contains(column);
    }

    private Cell createPointCell(final Variable column, final long time) {
        if (!inSpreadsheet(column) || column.isMedia() || column.refusesEdits()) {
            return null;
        }
        logger.info("New cell in " + column.getName() + " at " + time);
        Cell cell = column.createPointCell(time);
        spreadsheet.setLastCreatedCell(cell);
        spreadsheet.setLastCreatedVariable(column);
        record(new AddCellEdit(spreadsheet, column.getName(), cell));
        return cell;
    }

    /**
     * New Cell to Left or Right: for each selected cell, a cell with its onset and offset in the visible column beside
     * its own, unless that column is a media column or is locked.
     *
     * @param direction which side
     * @return the new cells
     */
    public List<Cell> newCellsBeside(final ArrayDirection direction) {
        List<Variable> visible = dataStore().getVisibleVariables();
        List<Cell> made = new ArrayList<>();
        for (Cell source : selectedCells()) {
            int own = visible.indexOf(source.getVariable());
            int beside = own + direction.getModifier();
            if (own < 0 || beside < 0 || beside >= visible.size()) {
                continue;
            }
            Variable column = visible.get(beside);
            if (column.isMedia() || column.refusesEdits()) {
                continue;
            }
            Cell cell = column.createPointCell(source.getOnset());
            cell.setOffset(source.getOffset());
            spreadsheet.setLastCreatedCell(cell);
            record(new AddCellEdit(spreadsheet, column.getName(), cell));
            cell.setHighlighted(true);
            made.add(cell);
        }
        return made;
    }

    /**
     * A key typed in a selected column in quick-key mode: a point cell at the time whose first code holds the key.
     *
     * @param column the selected column
     * @param typed  the key's character
     * @param time   the new cell's onset and offset
     * @return the new cell, or null in a media column, a locked one or one with no codes
     */
    public Cell newCellTyped(final Variable column, final char typed, final long time) {
        if (column != null && column.getRootNode().childArguments.isEmpty()) {
            // A column with no codes holds timing only, with nowhere to put the key.
            return null;
        }
        Cell cell = createPointCell(column, time);
        if (cell != null) {
            cell.setMatrixValue(0, String.valueOf(typed));
        }
        return cell;
    }

    /**
     * Set Onset: sets the onset of the selected cells, or with none selected the cells last selected, except those in
     * locked columns; each is an edit of its own in the undo history.
     *
     * @param time the new onset
     */
    public void setOnsets(final long time) {
        for (Cell cell : cellsToTime()) {
            if (cell.getVariable().refusesEdits()) {
                continue;
            }
            record(new ChangeOnsetCellEdit(spreadsheet, cell, cell.getOnset(), time, Granularity.FINEGRAINED));
            cell.setOnset(time);
        }
    }

    /**
     * Set Offset: sets the offset of the selected cells, or with none selected the cells last selected, except those in
     * locked columns and clips, whose offset follows from their onset and length; each is an edit of its own.
     *
     * @param time the new offset
     */
    public void setOffsets(final long time) {
        for (Cell cell : cellsToTime()) {
            if (cell.getVariable().refusesEdits() || cell.getClip() != null) {
                continue;
            }
            record(new ChangeOffsetCellEdit(spreadsheet, cell, cell.getOffset(), time, Granularity.FINEGRAINED));
            cell.setOffset(time);
        }
    }

    private List<Cell> cellsToTime() {
        List<Cell> selected = selectedCells();
        return List.copyOf(selected.isEmpty() ? spreadsheet.getLastSelectedCells() : selected);
    }

    /** Delete Cells: deletes the selected cells, except those in locked columns, as one edit in the undo history. */
    public void deleteSelected() {
        List<Cell> deleted = new ArrayList<>();
        for (Cell cell : selectedCells()) {
            if (!cell.getVariable().refusesEdits()) {
                deleted.add(cell);
            }
        }
        if (deleted.isEmpty()) {
            return;
        }
        logger.info("Delete cells: " + deleted);
        RemoveCellEdit edit = new RemoveCellEdit(spreadsheet, deleted);
        spreadsheet.removeCells(deleted);
        record(edit);
    }

    private void record(final UndoableEdit edit) {
        spreadsheet.postEdit(edit);
    }
}
