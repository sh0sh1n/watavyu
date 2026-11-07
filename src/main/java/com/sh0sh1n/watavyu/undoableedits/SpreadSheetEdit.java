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
package com.sh0sh1n.watavyu.undoableedits;


import com.sh0sh1n.watavyu.controllers.project.ProjectController;
import com.sh0sh1n.watavyu.models.db.DataStore;
import com.sh0sh1n.watavyu.views.discrete.SpreadSheetPanel;

import javax.swing.undo.AbstractUndoableEdit;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * An undoable edit for altering the contents of a spreadsheet.
 */
public abstract class SpreadSheetEdit extends AbstractUndoableEdit {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** When the edit was made */
    private final LocalTime timestamp = LocalTime.now(ZoneId.systemDefault());

    /** The spreadsheet the edit was made in, which undoing and redoing change. */
    protected final ProjectController controller;
    protected final DataStore model;

    /**
     * @param controller the spreadsheet the edit was made in
     */
    protected SpreadSheetEdit(final ProjectController controller) {
        super();
        this.controller = controller;
        model = controller.getDataStore();
    }

    protected void unselectAll() {
        SpreadSheetPanel panel = controller.getSpreadSheetPanel();
        if (panel != null) {
            panel.clearCellSelection();
        }
    }


    protected String getStyle() {
        String style;
        if (this.canUndo()) {
            style = "color='#000000'";
        } else {
            style = "color='#C0C0C0'";
        }
        return style;
    }

    @Override
    public String toString() {
        return "<html><font " + getStyle() + ">" + TIME.format(timestamp)
                + "\t" + getPresentationName() + "</font></html>";
    }

}
