package com.sh0sh1n.watavyu.controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.undoableedits.AddCellEdit;
import com.sh0sh1n.watavyu.undoableedits.RemoveCellEdit;
import com.sh0sh1n.watavyu.util.ArrayDirection;

import javax.swing.undo.UndoableEdit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The commands that make, delete and time one spreadsheet's cells; typing into them is {@link TypingCommands}. Each is
 * the one path for its user action, whether it comes from a menu, a key, the controls drawer or quick keys: it leaves
 * locked columns and media clips alone, changes the data model, and records what it did in the spreadsheet's undo
 * history. Times are given by the caller, as the playback's current time.
 */
public final class CellCommands {

    private static final Logger logger = LogManager.getLogger(CellCommands.class);

    /** Cells in time order, sorted as copies: a column's own sort reorders its cells in place. */
    private static final Comparator<Cell> BY_ONSET = Comparator.comparingLong(Cell::getOnset);

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
     * @return the new cell, or null if there is no such column or it is hidden or locked
     */
    public Cell newCell(final long time) {
        return newCellIn(spreadsheet.getLastCreatedColumn(), time);
    }

    /**
     * New Cell, from the keypad and the controls drawer: a point cell at the time in the first selected column, or
     * with none selected, as {@link #newCell(long)}.
     *
     * @param time the new cell's onset and offset
     * @return the new cell, or null if that column is a media column or is hidden or locked
     */
    public Cell newCellInSelectedColumn(final long time) {
        List<Column> selected = selectedColumns();
        return newCellIn(selected.isEmpty() ? spreadsheet.getLastCreatedColumn() : selected.get(0), time);
    }

    /**
     * Tells whether the cell commands act on a column. They act only on shown columns (owner, B22). Hiding deselects
     * a column and its cells, so the selection is always shown; what the spreadsheet remembers is checked here: a
     * hidden column takes no new cell though it was the one last given a cell, and the cell last selected in it is
     * not closed.
     *
     * @param column a column
     * @return whether it is shown
     */
    private static boolean shown(final Column column) {
        return !column.isHidden();
    }

    /** @return the selected columns, in spreadsheet order */
    private List<Column> selectedColumns() {
        return dataStore().getAllColumns().stream().filter(Column::isSelected).toList();
    }

    /** @return the selected cells, by column in spreadsheet order and then by time */
    private List<Cell> selectedCells() {
        List<Cell> selected = new ArrayList<>();
        for (Column column : dataStore().getAllColumns()) {
            column.getCells().stream().filter(Cell::isSelected).sorted(BY_ONSET).forEach(selected::add);
        }
        return selected;
    }

    /**
     * Tells whether New Cell, from the menu, has a column to put its cell in: the column last given a cell is in the
     * spreadsheet, shown and not a media column. A locked one counts, as it shows its lock when refusing.
     *
     * @return whether it has
     */
    public boolean hasColumnForNewCell() {
        Column last = spreadsheet.getLastCreatedColumn();
        return inSpreadsheet(last) && takesCells(last);
    }

    /**
     * New Cell and Set Offset, from the keypad and the controls drawer: closes the cell before the time, setting its
     * offset to a millisecond before it, and makes a point cell at the time. The cell closed is in the one selected
     * column, or else in the column of the cell last selected, unless it is hidden: of its cells starting at or before
     * the time, the latest, and among several starting then, the one last selected, or else the last of them. The new
     * cell goes in the first selected annotation column, else the column of the first selected cell, else the column
     * last given a cell, else the first annotation column, passing over hidden columns.
     *
     * @param time the new cell's onset and offset
     * @return the new cell, or null if the column to close or the one for the new cell is locked, or no shown
     *         annotation column can take it
     */
    public Cell newCellClosingPrevious(final long time) {
        List<Column> selected = selectedColumns();
        Cell lastSelected = spreadsheet.getLastSelectedCell();
        Column closing = selected.size() == 1 ? selected.get(0) : inSpreadsheet(lastSelected)
            ? lastSelected.getColumn() : null;
        if (closing != null && shown(closing)) {
            if (closing.refusesEdits()) {
                return null;
            }
            Cell previous = cellToClose(closing, time);
            if (previous != null) {
                CellTimes.setOffset(spreadsheet, previous, Math.max(0, time - 1));
            }
        }
        return newCellIn(columnForNewCellAt(), time);
    }

    /** @return the cell to close before the time, as {@link #newCellClosingPrevious(long)} picks it, or null */
    private Cell cellToClose(final Column column, final long time) {
        List<Cell> started = column.getCells().stream().filter(cell -> cell.getOnset() <= time).sorted(BY_ONSET)
            .toList();
        if (started.isEmpty()) {
            return null;
        }
        long latest = started.get(started.size() - 1).getOnset();
        List<Cell> latestCells = started.stream().filter(cell -> cell.getOnset() == latest).toList();
        Cell lastSelected = spreadsheet.getLastSelectedCell();
        return latestCells.contains(lastSelected) ? lastSelected : latestCells.get(latestCells.size() - 1);
    }

    /** @return the column for a new cell at a time, as {@link #newCellClosingPrevious(long)} picks it, or null */
    private Column columnForNewCellAt() {
        for (Column column : selectedColumns()) {
            if (takesCells(column)) {
                return column;
            }
        }
        for (Cell cell : selectedCells()) {
            if (takesCells(cell.getColumn())) {
                return cell.getColumn();
            }
        }
        if (hasColumnForNewCell()) {
            return spreadsheet.getLastCreatedColumn();
        }
        return dataStore().getAllColumns().stream().filter(CellCommands::takesCells).findFirst().orElse(null);
    }

    /** @return whether a column can be given a new cell, locks aside: a shown annotation column */
    private static boolean takesCells(final Column column) {
        return !column.isMedia() && shown(column);
    }

    /** Makes a point cell in a column, which is then the one last given a cell, and records it; null if refused. */
    private Cell newCellIn(final Column column, final long time) {
        return refusesCells(column) ? null : selectNewCell(column, time);
    }

    /** Makes a point cell in a column that takes it, selected alone with its column, and records it. */
    private Cell selectNewCell(final Column column, final long time) {
        // The selection is cleared first, so the views see the new cell selected alone, in its selected column, when
        // it is made.
        dataStore().deselectAll();
        column.setSelected(true);
        Cell cell = addPointCell(column, time);
        cell.setHighlighted(true);
        return cell;
    }

    /** @return whether a column is in the spreadsheet, as one remembered as last given a cell may have been deleted */
    private boolean inSpreadsheet(final Column column) {
        return dataStore().contains(column);
    }

    /** @return whether a cell is in the spreadsheet, as one remembered as last selected may have gone with its column */
    private boolean inSpreadsheet(final Cell cell) {
        return dataStore().contains(cell);
    }

    /**
     * Tells whether a column refuses a new cell: it has gone from the spreadsheet, is a media column or hidden, which
     * refuse quietly, as they cannot be seen to, or is locked, which shows its lock.
     */
    private boolean refusesCells(final Column column) {
        return !inSpreadsheet(column) || !takesCells(column) || column.refusesEdits();
    }

    /** Makes a point cell in a column that takes it, which is then the one last given a cell, and records it. */
    private Cell addPointCell(final Column column, final long time) {
        logger.info("New cell in " + column.getName() + " at " + time);
        Cell cell = column.createPointCell(time);
        spreadsheet.setLastCreatedCell(cell);
        spreadsheet.setLastCreatedColumn(column);
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
        List<Column> visible = dataStore().getVisibleColumns();
        List<Cell> made = new ArrayList<>();
        for (Cell source : selectedCells()) {
            int own = visible.indexOf(source.getColumn());
            int beside = own + direction.getModifier();
            if (own < 0 || beside < 0 || beside >= visible.size()) {
                continue;
            }
            Column column = visible.get(beside);
            if (column.isMedia() || column.refusesEdits()) {
                continue;
            }
            if (made.isEmpty()) {
                // The new cells are selected in place of their sources, each with its column; making none leaves
                // the selection as it was.
                dataStore().deselectAll();
            }
            column.setSelected(true);
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
     * @return the new cell, or null in a media column, a hidden or locked one or one with no codes
     */
    public Cell newCellTyped(final Column column, final char typed, final long time) {
        if (refusesCells(column) || column.getCodes().isEmpty()) {
            // A column with no codes holds timing only, with nowhere to put the key.
            return null;
        }
        Cell cell = selectNewCell(column, time);
        cell.setValue(0, String.valueOf(typed));
        return cell;
    }

    /**
     * Set Onset: sets the onset of the selected cells, except those in locked columns; with none selected, nothing
     * changes. Each is an edit of its own in the undo history.
     *
     * @param time the new onset
     */
    public void setOnsets(final long time) {
        for (Cell cell : selectedCells()) {
            CellTimes.setOnset(spreadsheet, cell, time);
        }
    }

    /**
     * Set Offset: sets the offset of the selected cells, except those in locked columns and clips, whose offset
     * follows from their onset and length; with none selected, nothing changes. Each is an edit of its own.
     *
     * @param time the new offset
     */
    public void setOffsets(final long time) {
        for (Cell cell : selectedCells()) {
            CellTimes.setOffset(spreadsheet, cell, time);
        }
    }

    /** Delete Cells: deletes the selected cells, except those in locked columns, as one edit in the undo history. */
    public void deleteSelected() {
        List<Cell> deleted = new ArrayList<>();
        for (Cell cell : selectedCells()) {
            if (!cell.getColumn().refusesEdits()) {
                deleted.add(cell);
            }
        }
        if (deleted.isEmpty()) {
            return;
        }
        logger.info("Delete cells: " + deleted);
        RemoveCellEdit edit = new RemoveCellEdit(spreadsheet, deleted);
        // The deleted cells' columns stay selected, with no cell (owner, 2026-11-13).
        dataStore().clearCellSelection();
        spreadsheet.removeCells(deleted);
        record(edit);
    }

    private void record(final UndoableEdit edit) {
        spreadsheet.postEdit(edit);
    }
}
