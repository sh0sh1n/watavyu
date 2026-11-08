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
package com.sh0sh1n.watavyu.views.spreadsheet.layouts;

import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.views.spreadsheet.SpreadsheetColumn;
import com.sh0sh1n.watavyu.views.spreadsheet.SpreadsheetView;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * SheetLayoutOrdinal implements the ordinal style layout of SpreadsheetCells
 * in the spreadsheet.
 */
public class SheetLayoutOrdinal extends SheetLayout {
    // The of the right hand margin.
    private int marginSize;
    private JScrollPane pane;

    /**
     * SheetLayoutOrdinal constructor.
     */
    public SheetLayoutOrdinal(final int margin) {
        marginSize = margin;
    }

    /**
     * Lays the container - positioning the cells within.
     *
     * @param parent The parent component for the container.
     */
    @Override
    protected void layoutCells(Container parent) {
        // This layout must be applied to a Spreadsheet panel.
        pane = (JScrollPane) parent;
        SpreadsheetView mainView = (SpreadsheetView) pane.getViewport().getView();
        mainView.setGeometry(place(mainView.shownColumns(), mainView::heightOf, marginSize));
    }

    /**
     * Places each column's cells one below another in time order, each as tall as it needs to be, whatever their
     * times. A cell is marked as overlapping where it ends after the next cell starts, and where its offset is before
     * its onset, unless the offset is zero.
     *
     * @param columns  the shown columns, with their cells in time order
     * @param heightOf how tall each cell needs to be
     * @param margin   the width at each column's right edge that its cells leave for the rule
     * @return where the cells lie
     */
    static SheetGeometry place(final List<SheetGeometry.ColumnCells> columns, final ToIntFunction<Cell> heightOf,
                               final int margin) {
        SheetGeometry.Builder geometry = new SheetGeometry.Builder();
        for (SheetGeometry.ColumnCells col : columns) {
            List<Cell> cells = col.cells();
            int y = 0;
            for (int i = 0; i < cells.size(); i++) {
                Cell cell = cells.get(i);
                Cell next = i == cells.size() - 1 ? null : cells.get(i + 1);
                boolean upsideDown = cell.getOffset() < cell.getOnset() && cell.getOffset() != 0;
                boolean overlapsNext = next != null && Math.max(cell.getOnset(), cell.getOffset()) > next.getOnset();
                int height = heightOf.applyAsInt(cell);
                geometry.place(cell, new Rectangle(0, y, col.width() - margin, height), upsideDown || overlapsNext);
                y += height;
            }
            geometry.cellsHeight(col.column(), y);
        }
        return geometry.build(TimeAxis.NONE);
    }

    @Override

    public void reorientView(final Rectangle cell) {
        // Set the new position of the scroll window.

        double viewMax = pane.getViewport().getViewRect().getY() + pane.getViewport().getViewRect().getHeight();
        double viewMin = pane.getViewport().getViewRect().getY();
        int cellMax = cell.y + cell.height;
        int cellMin = cell.y;

        if (viewMax < cellMax) {
            pane.getViewport().setViewPosition(
                    new Point((int) pane.getViewport().getViewRect().getX(),
                            cellMax - pane.getViewport().getHeight()));
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


}