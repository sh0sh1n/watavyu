package com.sh0sh1n.watavyu.views.spreadsheet.layouts;

import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.Column;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where a layout puts the cells of a sheet's shown columns: each cell's bounds in its column, whether it is marked as
 * overlapping the next cell (or as ending before it starts), how tall each column's cells come to, and, for a layout in
 * time, where times fall down the sheet. It is worked out from the cells' times and heights alone, so the sheet can
 * place components or draw cells from it.
 */
public final class SheetGeometry {

    /**
     * A shown column as a layout takes it.
     *
     * @param column the column
     * @param width  its width in pixels, rule included
     * @param cells  its cells in time order
     */
    public record ColumnCells(Column column, int width, List<Cell> cells) {
    }

    /**
     * Where a cell lies.
     *
     * @param bounds  its bounds in its column
     * @param overlap whether it is marked as overlapping the next cell, or as ending before it starts
     */
    public record Placed(Rectangle bounds, boolean overlap) {
    }

    /** A sheet with nothing placed. */
    public static final SheetGeometry EMPTY = new SheetGeometry(Map.of(), Map.of(), TimeAxis.NONE);

    private final Map<Cell, Placed> placed;

    private final Map<Column, Integer> cellsHeights;

    private final TimeAxis axis;

    /**
     * @param placed       where each shown cell lies
     * @param cellsHeights how tall each shown column's cells come to
     * @param timeAxis     where times fall down the sheet, or {@link TimeAxis#NONE} for a layout not in time
     */
    SheetGeometry(final Map<Cell, Placed> placed, final Map<Column, Integer> cellsHeights, final TimeAxis timeAxis) {
        this.placed = Map.copyOf(placed);
        this.cellsHeights = Map.copyOf(cellsHeights);
        this.axis = timeAxis;
    }

    /**
     * Returns where a cell lies, or empty if it was not placed, as a cell of a hidden column is not.
     *
     * @param cell a cell
     */
    public Optional<Placed> of(final Cell cell) {
        return Optional.ofNullable(placed.get(cell));
    }

    /**
     * Returns how tall a column's cells come to, or 0 for a column not placed.
     *
     * @param column a column
     */
    public int cellsHeight(final Column column) {
        return cellsHeights.getOrDefault(column, 0);
    }

    /** Returns how tall the tallest column's cells come to. */
    public int height() {
        return cellsHeights.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** Returns where times fall down the sheet, or {@link TimeAxis#NONE} for a layout not in time. */
    public TimeAxis timeAxis() {
        return axis;
    }

    /** Collects a geometry as a layout places cells. */
    static final class Builder {
        private final Map<Cell, Placed> placed = new HashMap<>();
        private final Map<Column, Integer> cellsHeights = new HashMap<>();

        void place(final Cell cell, final Rectangle bounds, final boolean overlap) {
            placed.put(cell, new Placed(bounds, overlap));
        }

        /** Returns where a cell placed earlier in this pass lies. */
        Rectangle bounds(final Cell cell) {
            return placed.get(cell).bounds();
        }

        boolean overlap(final Cell cell) {
            return placed.get(cell).overlap();
        }

        void cellsHeight(final Column column, final int height) {
            cellsHeights.put(column, height);
        }

        SheetGeometry build(final TimeAxis timeAxis) {
            return new SheetGeometry(placed, cellsHeights, timeAxis);
        }
    }
}
