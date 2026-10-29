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

import org.datavyu.util.Resources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.Datavyu;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.CellListener;
import org.datavyu.models.db.CellValue;
import org.datavyu.util.ClockTimer;
import org.datavyu.util.Settings;
import org.datavyu.views.discrete.datavalues.MatrixRootView;
import org.datavyu.views.discrete.datavalues.TimeStampDataValueEditor.TimeStampSource;
import org.datavyu.views.discrete.datavalues.TimeStampTextField;

import javax.swing.*;
import javax.swing.Box.Filler;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;


/**
 * Visual representation of a spreadsheet cell.
 */
public class SpreadsheetCell extends JPanel
        implements MouseListener, FocusListener, CellListener, ClockTimer.ClockListener {

    /**
     * Width of spacer between onset and offset timestamps.
     */
    private static final int TIME_SPACER = 5;
    /**
     * The logger for this class.
     */
    private static Logger logger = LogManager.getLogger(SpreadsheetCell.class);
    boolean isLaid = false;
    private boolean brandNew;
    /**
     * The panel that displays the cell.
     */
    private JPanel cellPanel;
    /**
     * A panel for holding the header to the cell.
     */
    private JPanel topPanel;
    /**
     * A panel for holding the value of the cell.
     */
    private MatrixRootView dataPanel;
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
     * Component that sets the width of the cell.
     */
    private Filler stretcher;
    /**
     * strut creates the gap between this cell and the previous cell.
     */
    private Filler strut;
    /**
     * Does this cell overlap another?
     */
    private boolean cellOverlap = false;
    /**
     * The spreadsheet cell selection listener.
     */
    private CellSelectionListener cellSelL;

    private final SpreadsheetColumn parentColumn;

    public SpreadsheetCell(final SpreadsheetColumn parentColumn,
                           final Cell cell,
                           final CellSelectionListener listener) {

        this.parentColumn = parentColumn;
        model = cell;
        setName(this.getClass().getSimpleName());

        Resources rMap = Resources.of(SpreadsheetCell.class);

        // Check the selected state of the datacell
        // If it is already selected in the database, we need to inform
        // the selector, but not trigger a selection change or deselect others.
        cellSelL = listener;

        cellPanel = new JPanel();
        cellPanel.addMouseListener(this);
        strut = new Filler(new Dimension(0, 0),
                new Dimension(0, 0),
                new Dimension(Short.MAX_VALUE, 0));

        setLayout(new BorderLayout());
        this.add(strut, BorderLayout.NORTH);
        this.add(cellPanel, BorderLayout.CENTER);

        // Build components used for the spreadsheet cell.
        topPanel = new JPanel();
        topPanel.addMouseListener(this);
        ord = new JLabel();
        ord.setFont(SpreadsheetTheme.labelFont());
        ord.setForeground(SpreadsheetTheme.PALETTE.ordinal());
        ord.setToolTipText(rMap.getString("ord.tooltip"));
        ord.addMouseListener(this);
        ord.setFocusable(true);

        onset = new TimeStampTextField(model, TimeStampSource.Onset);
        onset.setFont(SpreadsheetTheme.labelFont());
        onset.setForeground(SpreadsheetTheme.PALETTE.time());
        onset.setToolTipText(rMap.getString("onset.tooltip"));
        onset.addFocusListener(this);
        onset.addMouseListener(this);
        onset.setName("onsetTextField");

        offset = new TimeStampTextField(model, TimeStampSource.Offset);
        offset.setFont(SpreadsheetTheme.labelFont());
        offset.setForeground(SpreadsheetTheme.PALETTE.time());
        offset.setToolTipText(rMap.getString("offset.tooltip"));
        offset.addFocusListener(this);
        offset.addMouseListener(this);
        offset.setName("offsetTextField");

        dataPanel = new MatrixRootView(model, cell.getCellValue());
        dataPanel.setFont(SpreadsheetTheme.dataFont());
        dataPanel.setForeground(SpreadsheetTheme.PALETTE.text());

        dataPanel.setOpaque(false);
        dataPanel.addFocusListener(this);
        dataPanel.addMouseListener(this);
        dataPanel.setName("cellValue");

        // Set the appearance of the spreadsheet cell.
        cellPanel.setBackground(SpreadsheetTheme.PALETTE.cell());
        cellPanel.setBorder(CellLook.border(SpreadsheetTheme.PALETTE, CellLook.Selection.NONE, false, false));
        cellPanel.setLayout(new BorderLayout());

        // Set the apperance of the top panel and add child elements (ord, onset
        // and offset).
        topPanel.setOpaque(false);
        topPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
        cellPanel.add(topPanel, BorderLayout.NORTH);
        topPanel.add(ord);

        Component strut1 = Box.createHorizontalStrut(TIME_SPACER);
        topPanel.add(strut1);

        Component glue = Box.createGlue();
        topPanel.add(glue);

        topPanel.add(onset);

        Component strut2 = Box.createHorizontalStrut(TIME_SPACER);
        topPanel.add(strut2);
        topPanel.add(offset);

        // Set the apperance of the data panel - add elements for dis6playing
        // the actual data of the panel.
        cellPanel.add(dataPanel, BorderLayout.CENTER);

        Dimension d = new Dimension(229, 0);
        stretcher = new Filler(d, d, d);
        cellPanel.add(stretcher, BorderLayout.SOUTH);

        Datavyu.getVideoController().getClockTimer().registerListener(this);

        brandNew = true;
    }

    /**
     * @return The cell that this view element represents.
     */
    public Cell getCell() {
        model = model.getFreshCell();
        return model;
    }

    /**
     * @return onset view
     */
    public TimeStampTextField getOnset() {
        return onset;
    }

    /**
     * @return offset view
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
     * Get the onset ticks
     *
     * @return Onset time as a long.
     */
    public long getOnsetTicks() {
        return model.getOnset();
    }

    /**
     * Get the offset ticks
     *
     * @return Offset ticks as a long.
     */
    public long getOffsetTicks() {
        if (model.getOffset() < model.getOnset()) {
            return model.getOnset();
        }
        return model.getOffset();
    }

    /**
     * Get the actual offset ticks (even if less than onset).
     *
     * @return Offset ticks as a long.
     */
    public long getOffsetTicksActual() { return model.getOffset(); }
    
    public boolean isUpsideDown() {
        return model.getOffset() < model.getOnset() && model.getOffset() != 0;  
    }

    /**
     * Set the width of the SpreadsheetCell.
     *
     * @param width New width of the SpreadsheetCell.
     */
    public void setWidth(final int width) {
        Dimension d = new Dimension(width, 0);
        stretcher.changeShape(d, d, d);
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
     * Get overlap.
     */
    public boolean getOverlapBorder(){
        return cellOverlap;
    }

    /**
     * @return The MatrixRootView of this cell.
     */
    public final MatrixRootView getDataView() {
        return dataPanel;
    }

    /**
     * Shows how the cell is selected, whether it overlaps the next cell, and where it stands against the needle
     * ({@link CellLook}). Where it stands shows only while cells are highlighted by time.
     */
    public void updateSelectionDisplay() {
        boolean highlightAndFocus = Datavyu.getVideoController().getCellHighlightAndFocus();
        CellLook.Selection selection;
        if (model.isHighlighted() && !highlightAndFocus) {
            selection = CellLook.Selection.HIGHLIGHTED;
        } else if (model.isSelected()) {
            selection = CellLook.Selection.SELECTED;
        } else {
            selection = CellLook.Selection.NONE;
        }
        CellLook.Time time = highlightAndFocus || Datavyu.getVideoController().getCellHighlighting()
                ? CellLook.timeOf(model.getOnset(), model.getOffset(), Datavyu.getVideoController().getCurrentTime())
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
    // VariableListener Overrides
    // *************************************************************************
    @Override
    public void offsetChanged(final long newOffset) {
        offset.setValue();
        if (model.isSelected()) {
            // Update the find windows to the newly selected cell's values
            Datavyu.getVideoController().setOnsetField(model.getOnset());
            Datavyu.getVideoController().setOffsetField(model.getOffset());
        }
    }

    @Override
    public void onsetChanged(final long newOnset) {
        onset.setValue();
        if (model.isSelected()) {
            Datavyu.getVideoController().setOnsetField(model.getOnset());
            Datavyu.getVideoController().setOffsetField(model.getOffset());
        }
    }

    @Override
    public void highlightingChange(final boolean isHighlighted) {
        updateSelectionDisplay();
        if (model.isSelected()) {
            // Update the find windows to the newly selected cell's values
            Datavyu.getVideoController().setOnsetField(model.getOnset());
            Datavyu.getVideoController().setOffsetField(model.getOffset());
        }
    }

    @Override
    public void selectionChange(final boolean isSelected) {
        updateSelectionDisplay();
        if (model.isSelected()) {
            // Update the find windows to the newly selected cell's values
            Datavyu.getVideoController().setOnsetField(model.getOnset());
            Datavyu.getVideoController().setOffsetField(model.getOffset());
        } else if(getDataView().getEdTracker().indexOfCurrentEditor() > -1) {
            parentColumn.setIndexOfPreviousFocusedCell(getDataView().getEdTracker().indexOfCurrentEditor());
        }
    }

    @Override
    public void valueChange(final CellValue newCellValue) {
        dataPanel.setMatrix(newCellValue);
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
        int keyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        boolean groupSel = (me.getModifiers() & keyMask) != 0;
        boolean contSel = (me.getModifiers() & ActionEvent.SHIFT_MASK) != 0;

        Class source = me.getSource().getClass();
        boolean isEditorSrc = (source.equals(TimeStampTextField.class)
                || (source.equals(MatrixRootView.class)));

        model = model.getFreshCell();

        // User has clicked in magic spot, without modifier. Clear
        // currently selected cells and select this cell.
        if (!isEditorSrc && !groupSel && !contSel) {
          logger.debug("User has clicked in magic spot, without modifier");
            ord.requestFocus();
            cellSelL.clearCellSelection();
            model.setHighlighted(!model.isSelected());

            if (model.isSelected()) {
                cellSelL.addCellToSelection(this);
            }

            // User has clicked on editor or magic spot with modifier. Add
            // this cell to the current selection.
        } else if (groupSel && !contSel) {
          logger.debug("User has clicked on editor or magic spot with modifier.");
            ord.requestFocus();
            model.setHighlighted(!model.isSelected());

            if (model.isSelected()) {
                cellSelL.addCellToSelection(this);
            }

            // User has clicked on editor or magic spot with shift modifier.
            // Add this cell and everything in between the current selection.
        } else if (contSel) {
          logger.debug("User has clicked on editor or magic spot with shift modifier");
            ord.requestFocus();
            cellSelL.addCellToContinousSelection(this);

            // User has clicked somewhere in the cell without modifier. This
            // cell needs to be highlighted.
        } else {
            // Only change selection if not selected.
            if (!model.isHighlighted()) {
                logger.debug("Just highlighted");
                // Deselect cells before selected cell contents.
                cellSelL.clearCellSelection();
                model.setHighlighted(true);
                cellSelL.setHighlightedCell(this);
            }
        }

        // Update the find windows to the newly selected cell's values
        Datavyu.getVideoController().setOnsetField(model.getOnset());
        Datavyu.getVideoController().setOffsetField(model.getOffset());
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
        model = model.getFreshCell();

//        if (model.isHighlighted() && (cellPanel.getBorder().equals(NORMAL_BORDER)
//                || cellPanel.getBorder().equals(OVERLAP_BORDER))) {
            model.setSelected(true);
//        }
        Datavyu.getView().getSpreadsheetPanel().validate();
        Datavyu.getView().getSpreadsheetPanel().reorientView(this);

        parentColumn.setSelected(true);
    }

    @Override
    public void focusLost(final FocusEvent e) {
        if (brandNew) model.setSelected(false);
        brandNew = false;

        parentColumn.setIndexOfPreviousFocusedCell(getDataView().getEdTracker().indexOfCurrentEditor());

        List<Cell> selectedCells = Datavyu.getProjectController().getDataStore().getSelectedCells();
        List<Cell> newSelectedCell =  new ArrayList<>();

        if (selectedCells.size() == 0){
            newSelectedCell.add(model);
            Datavyu.getProjectController().setLastSelectedCell(model);
        } else {
            newSelectedCell =  selectedCells;
            newSelectedCell.add(model);
        }

        Datavyu.getProjectController().setLastSelectedCells(newSelectedCell);

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
