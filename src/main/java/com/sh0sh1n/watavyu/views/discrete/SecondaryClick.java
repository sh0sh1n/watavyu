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
package com.sh0sh1n.watavyu.views.discrete;

import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;

/**
 * A right-click, or a ⌃-click, the Mac's other way to make one. In a cell or on a column's header it leaves the
 * selection alone (AGENTS.md › Decisions, 2026-11-06).
 */
public final class SecondaryClick {

    private SecondaryClick() {
    }

    /** Returns whether the mouse event is part of a right-click or a ⌃-click. */
    public static boolean is(final MouseEvent e) {
        return SwingUtilities.isRightMouseButton(e) || e.isControlDown();
    }

    /**
     * Returns whether a cell's text drops the mouse event: the press, release and click of a right-click or ⌃-click,
     * which would otherwise move its caret and take the focus, and so select the cell.
     */
    public static boolean isDroppedByText(final MouseEvent e) {
        int id = e.getID();
        return (id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_RELEASED || id == MouseEvent.MOUSE_CLICKED)
            && is(e);
    }
}
