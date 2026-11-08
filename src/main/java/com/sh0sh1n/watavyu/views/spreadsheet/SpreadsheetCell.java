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
package com.sh0sh1n.watavyu.views.spreadsheet;

import com.sh0sh1n.watavyu.util.Resources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.CellListener;
import com.sh0sh1n.watavyu.util.ClockTimer;
import com.sh0sh1n.watavyu.util.Settings;
import com.sh0sh1n.watavyu.views.spreadsheet.editors.CellValuesView;
import com.sh0sh1n.watavyu.views.spreadsheet.editors.TimeStampDataValueEditor.TimeStampSource;
import com.sh0sh1n.watavyu.views.spreadsheet.editors.TimeStampTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;


/**
 * The cell being edited: live editors for its times and values, laid out as a drawn cell is ({@link CellParts}) and
 * placed over it by the sheet ({@link SpreadsheetView#edit}). The sheet draws every other cell; this one follows its
 * cell's changes, takes the clicks and keys typed into it, and moves the selection as a cell's view always has.
 */
public final class SpreadsheetCell extends JPanel
        implements MouseListener, FocusListener, CellListener, ClockTimer.ClockListener {

    /**
     * The logger for this class.
     */
    private static Logger logger = LogManager.getLogger(SpreadsheetCell.class);
    private boolean brandNew;
    /**
     * The panel that displays the cell.
     */
    private JPanel cellPanel;
    /**
     * A panel for holding the value of the cell.
     */
    private CellValuesView dataPanel;
    /**
     * The Ordinal display component.
     */
    private JLabel ord;
    /** Where this cell stood against the needle when it was last drawn, so a clock tick changes it only on a move. */
    private CellLook.Time shownTime;
    /**
     * The Onset display component.
     */
    private TimeStampTextField onset;
    /**
     * The Offset display component.
     */
    private TimeStampTextField offset;
    /**
     * The cell that this SpreadsheetCell represents.
     */
    private Cell model;
    /**
     * Does this cell overlap another?
     */
    private boolean cellOverlap = false;
    /**
     * The spreadsheet cell selection listener.
     */
    private CellSelectionListener cellSelL;

    private final SpreadsheetColumn parentColumn;

    /** The spreadsheet the cell is in */
    private final ProjectController controller;

    public SpreadsheetCell(final SpreadsheetColumn parentColumn,
                           final Cell cell,
                           final CellSelectionListener listener) {

        this.parentColumn = parentColumn;
        controller = parentColumn.getProjectController();
        model = cell;
        setName(this.getClass().getSimpleName());

        Resources rMap = Resources.of(SpreadsheetCell.class);

        cellSelL = listener;

        ord = new JLabel();
        ord.setToolTipText(rMap.getString("ord.tooltip"));
        ord.addMouseListener(this);
        ord.setFocusable(true);

        onset = new TimeStampTextField(controller, model, TimeStampSource.Onset);
        onset.setToolTipText(rMap.getString("onset.tooltip"));
        onset.addFocusListener(this);
        onset.addMouseListener(this);
        onset.setName("onsetTextField");

        offset = new TimeStampTextField(controller, model, TimeStampSource.Offset);
        offset.setToolTipText(rMap.getString("offset.tooltip"));
        offset.addFocusListener(this);
        offset.addMouseListener(this);
        offset.setName("offsetTextField");

        dataPanel = new CellValuesView(controller, model);
        dataPanel.addFocusListener(this);
        dataPanel.addMouseListener(this);
        dataPanel.setName("cellValue");

        CellParts parts = new CellParts(this, ord, onset, offset, dataPanel);
        cellPanel = parts.body;
        cellPanel.addMouseListener(this);
        parts.top.addMouseListener(this);

        controller.getVideoController().getClockTimer().registerListener(this);

        brandNew = true;
    }

    /** Stops following the clock, once the cell's view leaves the sheet. */
    public void dispose() {
        controller.getVideoController().getClockTimer().unRegisterListener(this);
    }

    /**
     * Returns the cell that this view element represents.
     */
    public Cell getCell() {
        return model;
    }

    /**
     * Returns onset view.
     */
    public TimeStampTextField getOnset() {
        return onset;
    }

    /**
     * Returns offset view.
     */
    public TimeStampTextField getOffset() {
        return offset;
    }

    /**
     * Set the ordinal value.
     *
     * @param ordinal The new ordinal value to use with this cell.
     */
    public void setOrdinal(final Integer ordinal) {
        ord.setText(ordinal.toString());
    }

    /**
     * Set the border of the cell.
     *
     * @param overlap true if the cell overlaps with the following cell, false
     *                otherwise.
     */
    public void setOverlapBorder(final boolean overlap) {
        cellOverlap = overlap;
        updateSelectionDisplay();
    }

    /**
     * Returns the CellValuesView of this cell.
     */
    public final CellValuesView getDataView() {
        return dataPanel;
    }

    /**
     * Shows how the cell is selected, whether it overlaps the next cell, and where it stands against the needle
     * ({@link CellLook}). Where it stands shows only while cells are highlighted by time.
     */
    public void updateSelectionDisplay() {
        boolean highlightAndFocus = controller.getVideoController().getCellHighlightAndFocus();
        CellLook.Selection selection = CellLook.selectionOf(model.isSelected(), model.isHighlighted(),
                highlightAndFocus);
        CellLook.Time time = highlightAndFocus || controller.getVideoController().getCellHighlighting()
                ? CellLook.timeOf(model.getOnset(), model.getOffset(), controller.getVideoController().getCurrentTime())
                : CellLook.Time.AHEAD;
        cellPanel.setBorder(CellLook.border(SpreadsheetTheme.PALETTE, selection, cellOverlap,
                time == CellLook.Time.IN_PLAY));
        if (time != shownTime) {
            shownTime = time;
            showColours(CellLook.colours(SpreadsheetTheme.PALETTE, time, Settings.get().getPastCellDim()));
        }
    }

    /** Shows colours again after the past-cell dimming changes. */
    public void refreshColours() {
        shownTime = null;
        updateSelectionDisplay();
    }

    private void showColours(final CellLook.Colours colours) {
        cellPanel.setBackground(colours.background());
        dataPanel.setForeground(colours.text());
        onset.setForeground(colours.time());
        offset.setForeground(colours.time());
        ord.setForeground(colours.ordinal());
    }

    // *************************************************************************
    // ColumnListener Overrides
    // *************************************************************************
    @Override
    public void offsetChanged(final long newOffset) {
        offset.setValue();
        if (model.isSelected()) {
            // Update the find windows to the newly selected cell's values
            controller.getVideoController().setOnsetField(model.getOnset());
            controller.getVideoController().setOffsetField(model.getOffset());
        }
    }

    @Override
    public void onsetChanged(final long newOnset) {
        onset.setValue();
        if (model.isSelected()) {
            controller.getVideoController().setOnsetField(model.getOnset());
            controller.getVideoController().setOffsetField(model.getOffset());
        }
    }

    @Override
    public void highlightingChange(final boolean isHighlighted) {
        updateSelectionDisplay();
        if (model.isSelected()) {
            // Update the find windows to the newly selected cell's values
            controller.getVideoController().setOnsetField(model.getOnset());
            controller.getVideoController().setOffsetField(model.getOffset());
        }
    }

    @Override
    public void selectionChange(final boolean isSelected) {
        updateSelectionDisplay();
        if (model.isSelected()) {
            // Update the find windows to the newly selected cell's values
            controller.getVideoController().setOnsetField(model.getOnset());
            controller.getVideoController().setOffsetField(model.getOffset());
        } else if(getDataView().getEdTracker().indexOfCurrentEditor() > -1) {
            parentColumn.setIndexOfPreviousFocusedCell(getDataView().getEdTracker().indexOfCurrentEditor());
        }
    }

    @Override
    public void contentChanged() {
        redrawValues();
    }

    /** Shows the cell's values again, as after its values, its column's codes or its lock change. */
    public void redrawValues() {
        dataPanel.showValues();
        revalidate();
    }

    // *************************************************************************
    // MouseListener Overrides
    // *************************************************************************
    @Override
    public void mouseEntered(final MouseEvent me) {
    }

    @Override
    public void mouseExited(final MouseEvent me) {
    }

    @Override
    public void mousePressed(final MouseEvent me) {
        // A right-click, or ⌃-click, leaves the selection alone, as on a column's header.
        if (SecondaryClick.is(me)) {
            return;
        }
        boolean groupSel = (me.getModifiersEx() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()) != 0;
        boolean contSel = me.isShiftDown();

        Class<?> source = me.getSource().getClass();
        boolean isEditorSrc = (source.equals(TimeStampTextField.class)
                || source.equals(CellValuesView.class));


        // User has clicked in magic spot, without modifier. Clear
        // currently selected cells and select this cell.
        if (!isEditorSrc && !groupSel && !contSel) {
          logger.debug("User has clicked in magic spot, without modifier");
            ord.requestFocus();
            cellSelL.clearCellSelection();
            model.setHighlighted(!model.isSelected());

            if (model.isSelected()) {
                cellSelL.addCellToSelection(model);
            }

            // User has clicked on editor or magic spot with modifier. Add
            // this cell to the current selection.
        } else if (groupSel && !contSel) {
          logger.debug("User has clicked on editor or magic spot with modifier.");
            ord.requestFocus();
            model.setHighlighted(!model.isSelected());

            if (model.isSelected()) {
                cellSelL.addCellToSelection(model);
            }

            // User has clicked on editor or magic spot with shift modifier.
            // Add this cell and everything in between the current selection.
        } else if (contSel) {
          logger.debug("User has clicked on editor or magic spot with shift modifier");
            ord.requestFocus();
            cellSelL.addCellToContinousSelection(model);

            // User has clicked somewhere in the cell without modifier. This
            // cell needs to be highlighted.
        } else {
            // Only change selection if not selected.
            if (!model.isHighlighted()) {
                logger.debug("Just highlighted");
                // Deselect cells before selected cell contents.
                cellSelL.clearCellSelection();
                model.setHighlighted(true);
                cellSelL.setHighlightedCell(model);
            }
        }

        // Update the find windows to the newly selected cell's values
        controller.getVideoController().setOnsetField(model.getOnset());
        controller.getVideoController().setOffsetField(model.getOffset());
    }

    @Override
    public void mouseReleased(final MouseEvent me) {
    }

    @Override
    public void mouseClicked(final MouseEvent me) {
    }

    // *************************************************************************
    // FocusListener Overrides
    // *************************************************************************
    @Override
    public void focusGained(final FocusEvent e) {

//        if (model.isHighlighted() && (cellPanel.getBorder().equals(NORMAL_BORDER)
//                || cellPanel.getBorder().equals(OVERLAP_BORDER))) {
            model.setSelected(true);
//        }
        if (controller.getSpreadSheetPanel() != null) {
            controller.getSpreadSheetPanel().validate();
            controller.getSpreadSheetPanel().reorientView(model);
        }

        parentColumn.setSelected(true);
    }

    @Override
    public void focusLost(final FocusEvent e) {
        if (brandNew) model.setSelected(false);
        brandNew = false;

        parentColumn.setIndexOfPreviousFocusedCell(getDataView().getEdTracker().indexOfCurrentEditor());

        List<Cell> selectedCells = controller.getDataStore().getSelectedCells();
        List<Cell> newSelectedCell =  new ArrayList<>();

        if (selectedCells.size() == 0){
            newSelectedCell.add(model);
            controller.setLastSelectedCell(model);
        } else {
            newSelectedCell =  selectedCells;
            newSelectedCell.add(model);
        }

        controller.setLastSelectedCells(newSelectedCell);

        model.setSelected(false);
        parentColumn.setSelected(false);
    }

    @Override
    public boolean isFocusOwner() {
        return (onset.isFocusOwner() || offset.isFocusOwner() || dataPanel.isFocusOwner());
    }

    @Override
    public void requestFocus() {
        dataPanel.requestFocus();
    }

    // *************************************************************************
    // Parent Class Overrides
    // *************************************************************************
    @Override
    public void paint(final Graphics g) {
        // Set the size at paint time - somewhere else may have
        // altered the font.
        dataPanel.setFont(SpreadsheetTheme.dataFont());
        super.paint(g);
    }

    @Override
    public void clockForceSync(double clockTime) {
        updateSelectionDisplay();
    }

    @Override
    public void clockPeriodicSync(double clockTime) {
        updateSelectionDisplay();
    }

    @Override
    public void clockStop(double clockTime) { }

    @Override
    public void clockPause(double clockTime) { }

    @Override
    public void clockStart(double clockTime) { }

    @Override
    public void clockRate(float rate) { }

    @Override
    public void clockBoundaryCheck(double clockTime) { }

    @Override
    public void streamsBoundaryCheck(double clockTime) { }

    @Override
    public void clockSeekPlayback(double clockTime) { }
}
