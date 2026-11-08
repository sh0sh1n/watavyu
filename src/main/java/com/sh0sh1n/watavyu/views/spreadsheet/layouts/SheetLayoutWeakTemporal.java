/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.sh0sh1n.watavyu.views.spreadsheet.layouts;

import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.views.spreadsheet.SpreadsheetCell;
import com.sh0sh1n.watavyu.views.spreadsheet.SpreadsheetColumn;
import com.sh0sh1n.watavyu.views.spreadsheet.SpreadsheetView;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
/**
 * SheetLayoutWeakTemporal - mimics the weak temporal alignment style from
 * original MacSHAPA.
 */
public class SheetLayoutWeakTemporal extends SheetLayout {
    // The of the right hand margin.

    // The maximum height of the layout in pixels.
    int maxHeight;
    private JScrollPane pane;

    /** The space between consecutive times, below the cells that start at the first. */
    static final int GAP = 15;

    /** A tail is a pixel high for each this many milliseconds of it, within the two bounds below. */
    static final long TAIL_MILLIS_PER_PIXEL = 10_000;

    /** The least height of a tail, in pixels: enough to see and to drag the needle through. */
    static final int TAIL_MIN = 60;

    /** The most height of a tail, in pixels, reached by tails of 100 minutes and more. */
    static final int TAIL_MAX = 600;

    /** Where times fell down the sheet in the last layout. */
    private TimeAxis lastAxis = TimeAxis.NONE;

    /**
     * SheetLayoutOrdinal constructor.
     *
     * @param margin The size of the margin used for this layout.
     */
    public SheetLayoutWeakTemporal(final int margin) {
    }

    @Override
    protected void layoutCells(Container parent) {
        pane = (JScrollPane) parent;

        // This layout must be applied to a Spreadsheet panel.
        SpreadsheetView mainView = (SpreadsheetView) pane.getViewport().getView();

        List<SpreadsheetColumn> visibleColumns = getVisibleColumns(mainView);
        Map<Cell, SpreadsheetCell> views = new HashMap<>();
        Map<Cell, Integer> heights = new HashMap<>();
        List<SheetGeometry.ColumnCells> columns = new ArrayList<>();
        for (SpreadsheetColumn col : visibleColumns) {
            List<Cell> cells = new ArrayList<>();
            for (SpreadsheetCell view : col.getCellsTemporally()) {
                views.put(view.getCell(), view);
                heights.put(view.getCell(), view.getPreferredSize().height);
                cells.add(view.getCell());
            }
            columns.add(new SheetGeometry.ColumnCells(col.getColumn(), col.getWidth(), cells));
        }

        SheetGeometry geometry = place(columns, heights::get);
        views.forEach((cell, view) -> geometry.of(cell).ifPresent(placed -> {
            view.setBounds(placed.bounds());
            view.setOverlapBorder(placed.overlap());
        }));
        for (SpreadsheetColumn col : visibleColumns) {
            col.setWorkingHeight(geometry.cellsHeight(col.getColumn()));
        }
        maxHeight = Math.max(parent.getHeight(), geometry.height());
        lastAxis = geometry.timeAxis();

        padColumns(mainView);
        laidOut();
    }

    /**
     * Places cells in time. The times their cells start and end are spaced down the sheet ({@link #tops}). A cell
     * starts at its onset's place, below the cells of its column that start at the same time, and reaches down to its
     * offset's place, or to the next cell's top where it overlaps that cell, and is never shorter than it needs to be.
     * A cell that ends a millisecond before the next starts reaches down to it, as one stretch of time. A cell is
     * marked as overlapping where its offset reaches the next cell's onset, where it shares its onset with the next
     * cell unless both are the same point, and where it ends before it starts.
     *
     * @param columns  the shown columns, with their cells in time order
     * @param heightOf how tall each cell needs to be
     * @return where the cells lie
     */
    static SheetGeometry place(final List<SheetGeometry.ColumnCells> columns, final ToIntFunction<Cell> heightOf) {
        // Each onset gets the height of the cells that start then, in the column where they are tallest.
        SortedSet<Long> times = new TreeSet<>();
        Map<Long, Integer> heightMap = new HashMap<>();
        for (SheetGeometry.ColumnCells col : columns) {
            Map<Long, Integer> inColumn = new HashMap<>();
            for (Cell cell : col.cells()) {
                long onset = cell.getOnset();
                inColumn.merge(onset, heightOf.applyAsInt(cell), Integer::sum);
                times.add(onset);
                if (cell.getOffset() > onset) {
                    times.add(cell.getOffset());
                }
            }
            inColumn.forEach((time, height) -> heightMap.merge(time, height, Integer::max));
        }
        Map<Long, Integer> onsetMap = tops(times, heightMap);

        // Each time's place is where the cells starting then begin; cells sharing an onset stack below one another,
        // so each column moves its own copy of those places down as it places them.
        SheetGeometry.Builder geometry = new SheetGeometry.Builder();
        Map<Long, Integer> offsetMap = new HashMap<>();
        for (SheetGeometry.ColumnCells col : columns) {
            List<Cell> cells = col.cells();
            int width = col.width() - 1;
            int colHeight = 0;
            Map<Long, Integer> onsetMapLocal = new HashMap<>(onsetMap);
            Cell prevCell = null;
            for (int i = 0; i < cells.size(); i++) {
                Cell curCell = cells.get(i);
                Cell nextCell = i == cells.size() - 1 ? null : cells.get(i + 1);
                long onset = curCell.getOnset();
                long offset = curCell.getOffset();
                int cellTopY = onsetMapLocal.get(onset);
                int preferred = heightOf.applyAsInt(curCell);

                int cellHeight;
                boolean overlap;
                if (onset > offset) {
                    overlap = true;
                    cellHeight = preferred;
                } else if (nextCell != null && onset == nextCell.getOnset()) {
                    overlap = onset != offset || offset == nextCell.getOffset();
                    cellHeight = preferred;
                } else if (nextCell != null && offset >= nextCell.getOnset()) {
                    overlap = true;
                    cellHeight = onsetMapLocal.get(nextCell.getOnset()) - cellTopY;
                } else {
                    overlap = false;
                    cellHeight = offsetMap.getOrDefault(offset, onsetMap.get(offset)) - cellTopY;
                }

                // Cells a millisecond apart are one stretch of time: the previous cell reaches down to this one.
                if (prevCell != null && onset - end(prevCell) == 1) {
                    Rectangle prev = geometry.bounds(prevCell);
                    geometry.place(prevCell, new Rectangle(0, prev.y, width, cellTopY - prev.y),
                            geometry.overlap(prevCell));
                    offsetMap.merge(offset, cellTopY, Integer::max);
                }

                cellHeight = Math.max(cellHeight, preferred);
                geometry.place(curCell, new Rectangle(0, cellTopY, width, cellHeight), overlap);

                onsetMapLocal.merge(onset, cellHeight, Integer::sum);
                if (!overlap) {
                    offsetMap.merge(offset, cellTopY + cellHeight, Integer::max);
                }
                colHeight = cellTopY + cellHeight;
                prevCell = curCell;
            }
            geometry.cellsHeight(col.column(), colHeight);
        }

        // A cell that does not overlap reaches down to where the cells ending at its offset end.
        for (SheetGeometry.ColumnCells col : columns) {
            for (Cell cell : col.cells()) {
                Rectangle bounds = geometry.bounds(cell);
                int mapHeight = offsetMap.getOrDefault(end(cell), -1) - bounds.y;
                if (!geometry.overlap(cell) && bounds.height < mapHeight) {
                    geometry.place(cell, new Rectangle(0, bounds.y, bounds.width, mapHeight), false);
                }
            }
        }

        // Each time spans from where its cells start to where the cells that end then end; a cell whose offset is
        // before its onset ends at its onset.
        Map<Long, Integer> bottoms = new HashMap<>();
        for (SheetGeometry.ColumnCells col : columns) {
            for (Cell cell : col.cells()) {
                Rectangle bounds = geometry.bounds(cell);
                bottoms.merge(end(cell), bounds.y + bounds.height, Integer::max);
            }
        }
        return geometry.build(new TimeAxis(onsetMap, bottoms));
    }

    /** Returns when a cell ends: its offset, or its onset if the offset is before it. */
    private static long end(final Cell cell) {
        return Math.max(cell.getOnset(), cell.getOffset());
    }

    /**
     * Where the cells of each time start down the sheet. Times are spaced by the cells between them rather than by
     * duration: each time is below the previous one by the height of the cells that start at the previous one and a
     * gap. The tail, the stretch after the last time any cell starts, has no such cells, so on these terms a clip
     * running on past every annotation would end in a sliver (F15). Instead the tail grows with its duration, within
     * bounds, and its times lie in it in proportion; a tail with several times keeps at least a gap between each.
     *
     * @param times   every onset and offset, in order
     * @param heights the height of the cells that start at each onset, in the column where they are tallest
     * @return the height in the sheet of each time
     */
    static Map<Long, Integer> tops(final SortedSet<Long> times, final Map<Long, Integer> heights) {
        Map<Long, Integer> tops = new HashMap<>();
        if (times.isEmpty()) {
            return tops;
        }
        long lastOnset = heights.keySet().stream().max(Long::compare).orElse(times.first());
        long duration = times.last() - lastOnset;
        int tail = Math.clamp(duration / TAIL_MILLIS_PER_PIXEL, TAIL_MIN, TAIL_MAX);
        int pos = 0;
        int tailTop = 0;
        int steps = 0;
        for (long time : times) {
            if (time <= lastOnset) {
                tops.put(time, pos);
                tailTop = pos + heights.getOrDefault(time, 0);
                pos = tailTop + GAP;
            } else {
                steps++;
                int share = (int) Math.round((double) tail * (time - lastOnset) / duration);
                tops.put(time, tailTop + Math.max(GAP * steps, share));
            }
        }
        return tops;
    }

    @Override
    public Optional<TimeAxis> timeAxis() {
        return Optional.of(lastAxis);
    }

    @Override
    public void reorientView(SpreadsheetCell cell) {
        double viewMax = pane.getViewport().getViewRect().getY() + pane.getViewport().getViewRect().getHeight();
        double viewMin = pane.getViewport().getViewRect().getY();
        int cellMax = cell.getY() + cell.getHeight();
        int cellMin = cell.getY();

        if (viewMax < cellMax && viewMin < cellMin) {
            int delta = (int)Math.min(cellMax - viewMax,
                    cellMin - viewMin);
            pane.getViewport().setViewPosition(
                    new Point((int) pane.getViewport().getViewRect().getX(),
                            (int)viewMin + delta));
            //                pane.getVerticalScrollBar().setValue(cellMax);
        } else if (viewMin > cellMin) {
            pane.getViewport().setViewPosition(
                    new Point((int) pane.getViewport().getViewRect().getX(),
                            cellMin));
        }

    }

    @Override
    public void reorientView(SpreadsheetColumn column) {
        double viewMax = pane.getViewport().getViewRect().getX() + pane.getViewport().getViewRect().getWidth();
        double viewMin = pane.getViewport().getViewRect().getX();
        int columnMax = column.getX() + column.getWidth();
        int columnMin = column.getX();

        if (viewMax < columnMax){
            pane.getViewport().setViewPosition(
                    new Point(columnMax - pane.getViewport().getWidth(), (int) pane.getViewport().getViewRect().getY()));
        }else if (viewMin > columnMin){
            pane.getViewport().setViewPosition(
                    new Point(columnMin, (int) pane.getViewport().getViewRect().getY()));
        }
    }

    /**
     * Pads all the columns so that they all fill out to the maximum height of
     * the spreadsheet.
     *
     * @param mainView The actual spreadsheet that we are populating.
     */
    private void padColumns(SpreadsheetView mainView) {
        for (SpreadsheetColumn col : mainView.getColumns()) {
            Integer colHeight = col.getWorkingHeight();

            col.getDataPanel().setHeight(maxHeight);
            col.getDataPanel().getPadding().setBounds(0,
                    colHeight,
                    col.getWidth(),
                    (maxHeight - colHeight));
        }
    }

    private List<SpreadsheetColumn> getVisibleColumns(SpreadsheetView mainView) {
        return mainView.getColumns().parallelStream()
                .filter(c -> c.isVisible())
                .collect(Collectors.toList());
    }
}
