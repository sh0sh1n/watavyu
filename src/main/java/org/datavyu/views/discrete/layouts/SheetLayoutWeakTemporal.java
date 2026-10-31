/**
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
package org.datavyu.views.discrete.layouts;

import org.datavyu.views.discrete.SpreadsheetCell;
import org.datavyu.views.discrete.SpreadsheetColumn;
import org.datavyu.views.discrete.SpreadsheetView;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
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
    private TimeAxis timeAxis = TimeAxis.NONE;

    /**
     * SheetLayoutOrdinal constructor.
     *
     * @param margin The size of the margin used for this layout.
     */
    public SheetLayoutWeakTemporal(final int margin) {
    }

    @Override
    protected void layoutCells(Container parent) {
        //TODO: figure out how to make this whole algorithm neater.
        pane = (JScrollPane) parent;

        // This layout must be applied to a Spreadsheet panel.
        SpreadsheetView mainView = (SpreadsheetView) pane.getViewport().getView();

        maxHeight = parent.getHeight();

        // Get visible columns.
        List<SpreadsheetColumn> visible_columns = getVisibleColumns(mainView);

        // Map columns to temporally ordered cells from column.
        HashMap<SpreadsheetColumn, List<SpreadsheetCell>> cellMap = new HashMap<>();
        for(SpreadsheetColumn col : visible_columns){
            cellMap.put(col, col.getCellsTemporally());
        }

        TreeSet<Long> times = new TreeSet<Long>();
        HashMap<Long, Integer> heightMap = new HashMap<>();

        /* Go through each column and assign "height" values to onset times.
           This will determine how much space to allocate between onset times.
           For each column, create an intermediate map and then merge with the
           actual map using maximum values.  This is required since a column can have
           multiple cells with the same onset.
           Also accumulate all unique times.
         */
        for( SpreadsheetColumn col : visible_columns){
            HashMap<Long, Integer> intermediateMap = new HashMap<>();
            for( SpreadsheetCell curCell : cellMap.get(col)){
                long onset = curCell.getOnsetTicks();
                int height = curCell.getPreferredSize().height;
                intermediateMap.compute(onset, (k, v) -> (v == null) ? height : v + height);

                // Add the onset and offset times to our master set.
                times.add(onset);
                long offset = curCell.getOffsetTicksActual();
                if(offset > onset) times.add(offset);
            }
            // Merge the intermediate map entries.
            intermediateMap.forEach( (k, v) -> heightMap.merge(k, v, Integer::max));
        }

        Map<Long, Integer> onsetMap = tops(times, heightMap);

        /* Iterate over all spreadsheet cells and set boundaries using onset and offset maps.
           Keep a local copy of the position maps.  Since each time value gets a range of positions
           (starting from onset map's value and ending at the offset's value), update the local copy of
           the onset map to get positions for cells sharing onsets.
         */
        HashMap<Long, Integer> offsetMap = new HashMap<>();
        for( SpreadsheetColumn col : visible_columns){
            List<SpreadsheetCell> orderedCells = cellMap.get(col);
            int colWidth = col.getWidth();
            int colHeight = 0;
            HashMap<Long, Integer> onsetMapLocal = new HashMap<>(onsetMap);
            SpreadsheetCell prevCell = null;
            for (int i = 0; i < orderedCells.size(); i++) {
                SpreadsheetCell curCell = orderedCells.get(i);
                SpreadsheetCell nextCell = (i == orderedCells.size() - 1) ? null : orderedCells.get(i + 1);

                long onset = curCell.getOnsetTicks();
                long offset = curCell.getOffsetTicksActual();
                int cellTopY = onsetMapLocal.get(onset);

                // Get height for cell
                int cellHeight;
                // Figure out height by looking at next cell's onset and offset times.
                if (onset > offset) { // cell is reversed
                    curCell.setOverlapBorder(true);
                    cellHeight = curCell.getPreferredSize().height;
                }
                // Current onset equals next onset
                else if (nextCell != null && onset == nextCell.getOnsetTicks()) {
                    if (onset != offset || offset == nextCell.getOffsetTicksActual())
                        curCell.setOverlapBorder(true);
                    cellHeight = curCell.getPreferredSize().height;
                }
                // Current offset greater than or equal to next onset
                else if (nextCell != null && offset >= nextCell.getOnsetTicks()) {
                    curCell.setOverlapBorder(true);
                    cellHeight = onsetMapLocal.get(nextCell.getOnsetTicks()) - cellTopY;
                } else {
                    curCell.setOverlapBorder(false);
                    cellHeight = offsetMap.getOrDefault(offset, onsetMap.get(offset)) - cellTopY;
                }

                // Treat cells with 1ms interval as continuous. Stretch bottom of previous cell to top of current cell.
                if (prevCell != null && onset - prevCell.getOffsetTicks() == 1) {
                    prevCell.setBounds(0, prevCell.getY(), colWidth - 1, cellTopY - prevCell.getY());
                    offsetMap.compute(offset, (k, v) -> (v == null) ? cellTopY : Math.max(v, cellTopY));
                }

                cellHeight = Math.max(cellHeight, curCell.getPreferredSize().height); // fix for edge cases...maybe investigate later
                // Set cell boundary
                curCell.setBounds(0, cellTopY, colWidth - 1, cellHeight);

                // Update local onset map
                int adjOn = cellHeight;
                onsetMapLocal.compute(onset, (k, v) -> v + adjOn);

                // Update offset map
                if(!curCell.getOverlapBorder()) {
                    int adjOff = cellTopY + cellHeight;
                    offsetMap.compute(offset, (k, v) -> (v == null) ? adjOff : Math.max(v, adjOff));
                }

                // Update vars
                colHeight = cellTopY + cellHeight;
                prevCell = curCell;
            }

            // Set column working height
            col.setWorkingHeight(colHeight);
            maxHeight = Math.max(maxHeight, colHeight);
        }

        /* Do a second pass to update the offsets again. */
        for (SpreadsheetColumn col : visible_columns) {
            int colWidth = col.getWidth();
            for (SpreadsheetCell sc : cellMap.get(col)) {
                int mapHeight = offsetMap.getOrDefault(sc.getOffsetTicks(), -1) - sc.getY();
                if (!sc.getOverlapBorder() && sc.getSize().getHeight() < mapHeight)
                    sc.setBounds(0, sc.getY(), colWidth - 1, mapHeight);
            }
        }

        // Each time spans from where its cells start to where the cells that end then end; a cell whose offset is
        // before its onset ends at its onset.
        HashMap<Long, Integer> bottoms = new HashMap<>();
        for (SpreadsheetColumn col : visible_columns) {
            for (SpreadsheetCell sc : cellMap.get(col)) {
                long end = Math.max(sc.getOnsetTicks(), sc.getOffsetTicksActual());
                bottoms.merge(end, sc.getY() + sc.getHeight(), Integer::max);
            }
        }
        timeAxis = new TimeAxis(onsetMap, bottoms);

        padColumns(mainView);
        laidOut();
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
        return Optional.of(timeAxis);
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
