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
import java.util.Optional;

/**
 * SheetLayout - abstract class for spreadsheet layouts.
 */
public abstract class SheetLayout extends ScrollPaneLayout {

    /** Runs after each layout of the sheet. */
    private Runnable afterLayout = () -> { };

    /**
     * SheetLayout constructor.
     */
    protected SheetLayout() {
    }

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
