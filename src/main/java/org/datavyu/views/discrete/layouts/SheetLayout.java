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
package org.datavyu.views.discrete.layouts;

import org.datavyu.views.discrete.SpreadsheetCell;
import org.datavyu.views.discrete.SpreadsheetColumn;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.Optional;

/**
 * SheetLayout - abstract class for spreadsheet layouts.
 */
public abstract class SheetLayout extends ScrollPaneLayout {

    /** Runs after each layout of the sheet. */
    private Runnable afterLayout = () -> { };

    /** The scroll pane's size when the cells were last placed, or null before they first are. */
    private Dimension placedFor;

    /**
     * SheetLayout constructor.
     */
    protected SheetLayout() {
    }

    /**
     * Lays out the scroll pane, and places the cells again if something in the sheet changed or the pane changed size.
     * Scrolling revalidates the viewport, and with it this scroll pane, but moves no cell; placing every cell on each
     * scroll step stalled scrolling and following the needle on a large sheet (B15). A change in the sheet invalidates
     * its view on the way up to the scroll pane, so a valid view means its cells are where they were.
     *
     * @param parent the spreadsheet's scroll pane
     */
    @Override
    public final void layoutContainer(final Container parent) {
        super.layoutContainer(parent);
        Component view = ((JScrollPane) parent).getViewport().getView();
        if (view != null && view.isValid() && parent.getSize().equals(placedFor)) {
            return;
        }
        placedFor = parent.getSize();
        layoutCells(parent);
    }

    /**
     * Places the sheet's cells.
     *
     * @param parent the spreadsheet's scroll pane, already laid out
     */
    protected abstract void layoutCells(Container parent);

    public abstract void reorientView(SpreadsheetCell cell);

    public abstract void reorientView(SpreadsheetColumn column);

    /**
     * @param afterLayout runs after each layout of the sheet, to follow where its cells now are
     */
    public void setAfterLayout(final Runnable afterLayout) {
        this.afterLayout = afterLayout;
    }

    /** Called by a layout once it has placed the sheet's cells. */
    protected final void laidOut() {
        afterLayout.run();
    }

    /**
     * @return where times fall down the sheet as last laid out, or empty if this layout does not place cells in time
     */
    public Optional<TimeAxis> timeAxis() {
        return Optional.empty();
    }
}
