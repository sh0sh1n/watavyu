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
package org.datavyu.views.discrete;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SpreadsheetView implements the Scrollable interface and
 * is the view to use in the viewport of the JScrollPane in Spreadsheet.
 */
public class SpreadsheetView extends JPanel implements Scrollable {

    /**
     * Maximum unit scroll amount.
     */
    private static final int MAX_UNIT_INCREMENT = 50;

    /**
     * The columns held in this SpreadsheetView.
     */
    private List<SpreadsheetColumn> columns;

    /** The playhead's line across the columns: its needle's colour, faint enough to read the cells through. */
    static final Color PLAYHEAD_LINE = new Color(SpreadsheetTheme.PALETTE.accent().getRed(),
            SpreadsheetTheme.PALETTE.accent().getGreen(), SpreadsheetTheme.PALETTE.accent().getBlue(), 90);

    /** Where the playhead's line crosses the columns, or -1 for no line. */
    private int playheadY = -1;

    /**
     * Creates new form SpreadsheetView.
     */
    public SpreadsheetView() {
        columns = new ArrayList<SpreadsheetColumn>();
        this.setDoubleBuffered(true);
    }

    /**
     * Adds a column to this spreadsheet view.
     *
     * @param newColumn The new column to add this spreadsheet view.
     */
    public void addColumn(final SpreadsheetColumn newColumn) {
        columns.add(newColumn);
        this.add(newColumn.getDataPanel());
        newColumn.setExclusiveSelected(newColumn.isSelected()); //this will ensure visual highlighting accurately reflects variable selected state
    }
    
    /**
     * Removes a column from this spreadsheet view.
     *
     * @param delColumn The column to remove from this spreadsheet view.
     */
    public void removeColumn(final SpreadsheetColumn delColumn) {
        this.remove(delColumn.getDataPanel());
        columns.remove(delColumn);
    }

    /**
     * Moves the playhead's line across the columns.
     *
     * @param y the line's height in the sheet, or -1 for no line
     */
    public void setPlayheadY(final int y) {
        if (y != playheadY) {
            repaintLine(playheadY);
            playheadY = y;
            repaintLine(y);
        }
    }

    /** @return the playhead's line's height in the sheet, or -1 if there is none */
    int getPlayheadY() {
        return playheadY;
    }

    private void repaintLine(final int y) {
        if (y >= 0) {
            repaint(0, y - 2, getWidth(), 4);
        }
    }

    /** With a line over the cells, a cell repaints through this view, so that the line is drawn over it again. */
    @Override
    public boolean isOptimizedDrawingEnabled() {
        return playheadY < 0;
    }

    @Override
    protected boolean isPaintingOrigin() {
        return playheadY >= 0;
    }

    @Override
    public void paint(final Graphics g) {
        super.paint(g);
        if (playheadY >= 0) {
            g.setColor(PLAYHEAD_LINE);
            g.fillRect(0, playheadY - 1, getWidth(), 2);
        }
    }

    /**
     * @return All the columns held in this spreadsheet view.
     */
    public List<SpreadsheetColumn> getColumns() {
        return columns;
    }

    /**
     * Returns the preferred size of the viewport for a view component.
     * In this instance it returns getPreferredSize
     *
     * @return the preferredSize of a <code>JViewport</code> whose view
     * is this <code>SpreadsheetView</code>
     */
    @Override
    public final Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    /**
     * @return False - the spreadsheet can scroll left to right if needed.
     */
    @Override
    public final boolean getScrollableTracksViewportWidth() {
        return false;
    }

    /**
     * @return False - the spreadsheet can scroll up and down if needed.
     */
    @Override
    public final boolean getScrollableTracksViewportHeight() {
        return false;
    }

    /**
     * Temporary fix for cell-jumping behavior.
     * TODO: Make this work correctly.
     */
    @Override
    public final void scrollRectToVisible(Rectangle r) {

    }

    /**
     * Computes the scroll increment that will completely expose one new row
     * or column, depending on the value of orientation.
     *
     * @param visibleRect The view area visible within the viewport
     * @param orientation VERTICAL or HORIZONTAL.
     * @param direction   Less than zero up/left, greater than zero down/right.
     * @return The "unit" increment for scrolling in the specified direction.
     * This value should always be positive.
     */
    @Override
    public final int getScrollableUnitIncrement(final Rectangle visibleRect,
                                                final int orientation,
                                                final int direction) {
        //Get the current position.
        int currentPosition = 0;
        if (orientation == SwingConstants.HORIZONTAL) {
            currentPosition = visibleRect.x;
        } else {
            currentPosition = visibleRect.y;
        }

        //Return the number of pixels between currentPosition
        //and the nearest tick mark in the indicated direction.
        if (direction < 0) {
            int newPosition = currentPosition
                    - (currentPosition / MAX_UNIT_INCREMENT)
                    * MAX_UNIT_INCREMENT;
            if (newPosition == 0) {
                return MAX_UNIT_INCREMENT;
            } else {
                return newPosition;
            }
        } else {
            return ((currentPosition / MAX_UNIT_INCREMENT) + 1)
                    * MAX_UNIT_INCREMENT
                    - currentPosition;
        }
    }

    /**
     * Computes the block scroll increment that will completely expose a row
     * or column, depending on the value of orientation.
     *
     * @param visibleRect The view area visible within the viewport
     * @param orientation VERTICAL or HORIZONTAL.
     * @param direction   Less than zero up/left, greater than zero down/right.
     * @return The "block" increment for scrolling in the specified direction.
     * This value should always be positive.
     */
    @Override
    public final int getScrollableBlockIncrement(final Rectangle visibleRect,
                                                 final int orientation,
                                                 final int direction) {
        if (orientation == SwingConstants.HORIZONTAL) {
            return visibleRect.width - MAX_UNIT_INCREMENT;
        } else {
            return visibleRect.height - MAX_UNIT_INCREMENT;
        }
    }

}
