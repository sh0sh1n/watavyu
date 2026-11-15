package com.sh0sh1n.watavyu.controllers;

import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.Column;

import java.util.List;
import java.util.Optional;

/**
 * Where the navigation keys go (AGENTS.md › Decisions, 2026-11-13, the key mapping), worked out from model cells, columns
 * and the playhead's time alone: ⌃J and ⌃K (and ↓ and ↑) up and down a column, ⌃H and ⌃L to the column beside.
 */
public final class CellNavigation {

    private CellNavigation() {
    }

    /**
     * Returns the cell to go to down or up a column: the one after or before the current cell in time order; or with
     * no cell current, going down the first starting at or after the time, going up the last starting at or before it.
     *
     * @param cells   the column's cells, in time order
     * @param current the column's selected cell, or null
     * @param down    whether going down, to later cells
     * @param time    the playhead's time
     * @return the cell, or empty at the end of the column
     */
    public static Optional<Cell> vertical(final List<Cell> cells, final Cell current, final boolean down,
                                          final long time) {
        int at = current == null ? -1 : cells.indexOf(current);
        if (at >= 0) {
            int next = at + (down ? 1 : -1);
            return next >= 0 && next < cells.size() ? Optional.of(cells.get(next)) : Optional.empty();
        }
        if (down) {
            return cells.stream().filter(cell -> cell.getOnset() >= time).findFirst();
        }
        return cells.stream().filter(cell -> cell.getOnset() <= time).reduce((first, second) -> second);
    }

    /**
     * Returns the column to go to beside a column, passing over media columns. Navigation stops at the first and last
     * columns, and does not come round.
     *
     * @param shown   the shown columns, in spreadsheet order
     * @param current the column gone from, or null to go to the first annotation column (going right) or the last
     *                (going left)
     * @param right   whether going right
     * @return the column, or empty at the edge
     */
    public static Optional<Column> beside(final List<Column> shown, final Column current, final boolean right) {
        int at = current == null ? (right ? -1 : shown.size()) : shown.indexOf(current);
        if (current != null && at < 0) {
            return Optional.empty();
        }
        int step = right ? 1 : -1;
        for (int i = at + step; i >= 0 && i < shown.size(); i += step) {
            if (!shown.get(i).isMedia()) {
                return Optional.of(shown.get(i));
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the cell whose onset is nearest a time, and of two as near, the earlier.
     *
     * @param cells a column's cells, in time order
     * @param time  the time
     * @return the cell, or empty in a column with none
     */
    public static Optional<Cell> nearest(final List<Cell> cells, final long time) {
        Cell nearest = null;
        for (Cell cell : cells) {
            if (nearest == null || Math.abs(cell.getOnset() - time) < Math.abs(nearest.getOnset() - time)) {
                nearest = cell;
            }
        }
        return Optional.ofNullable(nearest);
    }
}
