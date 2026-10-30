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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.Datavyu;
import org.datavyu.models.db.*;
import org.datavyu.undoableedits.ChangeNameVariableEdit;
import org.datavyu.undoableedits.LockColumnEdit;
import org.datavyu.util.ClockTimer;
import org.datavyu.util.Constants;
import org.datavyu.util.DragAndDrop.TransparentPanel;
import org.datavyu.views.Glyph;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.undo.UndoableEdit;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;

/**
 * This class maintains the visual representation of the column in the
 * Spreadsheet window.
 */
public final class SpreadsheetColumn extends JLabel implements VariableListener, MouseListener, MouseMotionListener,
        ClockTimer.ClockListener {

    /** The logger for this class */
    private static Logger logger = LogManager.getLogger(SpreadsheetColumn.class);

    /**
     * Default column width.
     */
    public static final int DEFAULT_COLUMN_WIDTH = 230;

    /**
     * Default column height.
     */
    public static final int DEFAULT_HEADER_HEIGHT = 25;

    /**
     * The logger for this class.
     */
    private static Logger LOGGER = LogManager.getLogger(SpreadsheetColumn.class);

    /**
     * Database reference.
     */
    private DataStore dataStore;

    /**
     * Reference to the variable.
     */
    private Variable variable;

    /**
     * ColumnDataPanel this column manages.
     */
    private ColumnDataPanel datapanel;

    /**
     * Width of the column in pixels.
     */
    private int width = DEFAULT_COLUMN_WIDTH;

    /**
     * Selected state.
     */
    private boolean selected = false;

    /**
     * Can the column be dragged?
     */
    private boolean draggable;

    /**
     * Can the column be moved?
     */
    private boolean moveable;

    /**
     * cell selection listener to notify of cell selection changes.
     */
    private CellSelectionListener cellSelList;

    /**
     * column selection listener to notify of column selection changes.
     */
    private ColumnSelectionListener columnSelList;

    /**
     * column visibility listener to notify of column visibility changes.
     */
    private ColumnVisibilityListener columnVisList;

    /**
     * Layout state: The height of the column data area in pixels.
     */
    private int dataHeight = 0;

    private TransparentPanel transparentPanel = (TransparentPanel) Datavyu.getView().getFrame().getGlassPane();

    private int previouslyFocusedCellIdx = -1;

    /**
     * Creates new SpreadsheetColumn.
     *
     * @param db       Database reference.
     * @param var    the variable this column displays.
     * @param cellSelL Spreadsheet cell selection listener to notify
     * @param colSelL  Column selection listener to notify.
     */
    public SpreadsheetColumn(final DataStore db,
                             final Variable var,
                             final CellSelectionListener cellSelL,
                             final ColumnSelectionListener colSelL,
                             final ColumnVisibilityListener colVisL) {
        this.dataStore = db;
        this.variable = var;
        this.cellSelList = cellSelL;
        this.columnSelList = colSelL;
        this.columnVisList = colVisL;

        setOpaque(true);
        setHorizontalAlignment(JLabel.CENTER);
        setFont(SpreadsheetTheme.headerFont());
        setBackground(SpreadsheetTheme.PALETTE.chrome());
        setForeground(SpreadsheetTheme.PALETTE.text());
        setBorder(headerBorder(false));
        setMinimumSize(this.getHeaderSize());
        setPreferredSize(this.getHeaderSize());
        setMaximumSize(this.getHeaderSize());

        setText(var.getName());
        updateLockMark();

        datapanel = new ColumnDataPanel(this, width, var, cellSelL);
        this.setVisible(!var.isHidden());
        datapanel.setVisible(!var.isHidden());

        Datavyu.getVideoController().getClockTimer().registerListener(this);
    }

    /** Height of the bar under a selected column's header. */
    private static final int SELECTION_BAR = 3;

    /**
     * @param selected whether the column is selected
     * @return the header's border: a rule on its right, and a bar in the selection colour along its bottom when
     * selected, so the name stays on the chrome and keeps its contrast
     */
    private static Border headerBorder(final boolean selected) {
        Border rule = BorderFactory.createMatteBorder(0, 0, 0, Constants.BORDER_SIZE,
                SpreadsheetTheme.PALETTE.rule());
        Border bar = selected
                ? BorderFactory.createMatteBorder(0, 0, SELECTION_BAR, 0, SpreadsheetTheme.PALETTE.selection())
                : BorderFactory.createEmptyBorder(0, 0, SELECTION_BAR, 0);
        return BorderFactory.createCompoundBorder(rule, bar);
    }

    /** The mark on a locked media column's header. */
    private static final Icon LOCK_MARK = Glyph.of(Glyph.Shape.LOCK, 14);

    /** Shows the lock mark while a media column is locked. */
    private void updateLockMark() {
        setIcon(variable.isLocked() ? LOCK_MARK : null);
        setToolTipText(variable.isLocked() ? "Locked: its clips do not move" : null);
    }

    /**
     * Makes the Lock Clips menu item of a media column's header: it shows whether the column is locked and toggles
     * it, as an undoable edit.
     *
     * @param dataStore the spreadsheet holding the column
     * @param column    a media column
     * @param post      takes the edit for the undo history
     * @return the menu item
     */
    static JCheckBoxMenuItem lockItem(final DataStore dataStore, final Variable column,
                                      final Consumer<UndoableEdit> post) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem("Lock Clips", column.isLocked());
        item.setName("lockClips");
        item.addActionListener(e -> {
            column.setLocked(item.isSelected());
            post.accept(new LockColumnEdit(dataStore, column.getName(), item.isSelected()));
        });
        return item;
    }

    /** Shows a media column's header menu, if this event asks for one. */
    private boolean showHeaderMenu(final MouseEvent me) {
        if (!me.isPopupTrigger() || !variable.isMedia()) {
            return false;
        }
        JPopupMenu menu = new JPopupMenu();
        menu.add(lockItem(dataStore, variable, Datavyu.getView().getUndoSupport()::postEdit));
        menu.show(this, me.getX(), me.getY());
        me.consume();
        return true;
    }

    /**
     * @return The height in pixels of the cells that have been laid in this
     * column
     */
    public int getWorkingHeight() {
        return dataHeight;
    }

    /**
     * @param newHeight The height in pixels of the cells that have been laid
     *                  in this column.
     */
    public void setWorkingHeight(final int newHeight) {
        dataHeight = newHeight;
    }

    public Variable getVariable() {
        return variable;
    }

    /**
     * Registers this spreadsheet column with everything that needs to notify
     * this class of events.
     */
    public void registerListeners() {
        addMouseListener(this);
        addMouseMotionListener(this);
        variable.addListener(this);
        datapanel.registerListeners();
    }

    /**
     * Deregisters this spreadsheet column with everything that is currently
     * notiying it of events.
     */
    public void deregisterListeners() {
        removeMouseListener(this);
        removeMouseMotionListener(this);
        variable.removeListener(this);
        datapanel.deregisterListeners();
    }

    /**
     * Opens an input dialog to change a variable name.
     *
     * @throws HeadlessException
     */
    public void showChangeVarNameDialog() throws HeadlessException {
        //Edit variable name on double click
        String newName = "";

        boolean exitFlag = false;
        while (!exitFlag) {
            newName = (String) JOptionPane.showInputDialog(null, null, "New column name",
                    JOptionPane.PLAIN_MESSAGE, null, null, getColumnName());

            if (newName != null) {
                try {
                    setColumnName(newName);
                    exitFlag = true;

                } catch (UserWarningException ex) {
                    exitFlag = false;
                }

            } else {
                exitFlag = true;
            }
        }
    }

    /**
     * @return Column Header size as a dimension.
     */
    public Dimension getHeaderSize() {
        return new Dimension(getWidth(), DEFAULT_HEADER_HEIGHT);
    }

    /**
     * Clears the display components from the spreadsheet column.
     */
    public void clear() {
        datapanel.clear();
    }

    /**
     * @return Column Width in pixels.
     */
    @Override
    public int getWidth() {
        return width;
    }

    /**
     * @param colWidth Column width to set in pixels.
     */
    public void setWidth(final int colWidth) {
        LOGGER.info("set column width");
        width = colWidth;

        Dimension dim = getHeaderSize();
        setPreferredSize(dim);
        setMaximumSize(dim);
        revalidate();

        datapanel.setWidth(width);
        datapanel.revalidate();
    }

    /**
     * @return The datapanel.
     */
    public ColumnDataPanel getDataPanel() {
        return datapanel;
    }

    /**
     * Set the selected state for the DataColumn this displays and clear all other cells and columns.
     *
     * @param isSelected Selected state.
     */
    public void setExclusiveSelected(final boolean isSelected) {
        LOGGER.info("select column and clear selections");
        cellSelList.clearCellSelection();
        columnSelList.clearColumnSelection();
        setSelected(isSelected);
    }

    /**
     * @return selection status of underlying variable
     */
    public boolean isSelected() {
        return variable.isSelected();
    }

    /**
     * Set the selected state for the DataColumn this displays.
     *
     * @param isSelected Selected state.
     */
    public void setSelected(final boolean isSelected) {
        LOGGER.info("select column");
        variable.setSelected(isSelected);
        this.selected = isSelected;

        setBorder(headerBorder(selected));
        repaint();
    }

    /**
     * Save the Index of the {@link EditorTracker} when a cell is deselected or lost focus.
     * {@link SpreadsheetColumn#previouslyFocusedCellIdx} is used only in the Highlight
     * and focus mode to keep track of the editor to be selected in the next cell.
     * @param index of the previous Editor
     */
    public void setIndexOfPreviousFocusedCell(final int index) {
        previouslyFocusedCellIdx = index;
    }

    /**
     * @return The SpreadsheetCells in this column.
     */
    public List<SpreadsheetCell> getCells() {
        return datapanel.getCells();
    }

    /**
     * Gets a specific spreadsheet cell temporally.
     *
     * @param index The index of the spreadsheet cell you wish to retrieve.
     * @return The nth spreadsheet cell temporally.
     */
    public SpreadsheetCell getCellTemporally(final int index) {
        return datapanel.getCellTemporally(index);
    }

    /**
     * @return The Spreadsheet cells in this column temporally.
     */
    public List<SpreadsheetCell> getCellsTemporally() {
        return datapanel.getCellsTemporally();
    }

    public SpreadsheetCell getNearestCellTemporally(SpreadsheetCell sc) {
        SpreadsheetCell closestCell = null;
        for(SpreadsheetCell c : datapanel.getCellsTemporally()) {
            if(closestCell == null) {
                closestCell = c;
            } else {
                if(Math.abs(sc.getOnsetTicks() - c.getOnsetTicks()) < Math.abs(sc.getOnsetTicks()
                        - closestCell.getOnsetTicks())) {
                    closestCell = c;
                }
            }
        }
        return closestCell;
    }

    /**
     * @return The header name of this SpreadsheetColumn.
     */
    public String getColumnName() {
        return variable.getName();
    }

    private void focusNextCell() {
        long time = Datavyu.getVideoController().getCurrentTime();
        List<SpreadsheetCell> tempCells = getCellsTemporally();
        for(int i = 0; i < tempCells.size(); i++) {
            SpreadsheetCell c = tempCells.get(i);
            if(c.getCell().isInTimeWindow(time)) {
                if(!c.isFocusOwner()) {
                    if(c.getCell().getCellValue() instanceof MatrixCellValue) {
                        int firstEmpty = -1;
                        List<CellValue> args = ((MatrixCellValue) c.getCell().getCellValue()).getArguments();
                        for(int j = 0; j < args.size(); j++) {
                            if(args.get(j).isEmpty()) {
                                firstEmpty = j;
                                break;
                            }
                        }
                        if(firstEmpty > -1) {
                            c.requestFocus();
                            c.getDataView().getEdTracker().setEditor(c.getDataView().getEdTracker().getEditorAtArgIndex(firstEmpty));
                        } else {
                            c.requestFocus();
                            c.getDataView().getEdTracker().setEditor(c.getDataView().getEdTracker().getEditorAtIndex(previouslyFocusedCellIdx > -1 ? previouslyFocusedCellIdx : 0));
                        }
                    } else {
                        if(c.getCell().getCellValue().isEmpty()) {
                            c.requestFocus();
                        }
                    }
                }
                if(c.getCell().getOnset() > time) {
                    break;
                }
                break;
            }
        }
    }

    public void setColumnName(final String newName) throws UserWarningException {
        try {
            // Make sure this column name isn't already in the column
            for (Variable v : dataStore.getAllVariables()) {
                if (v.getName().equals(newName)) {
                    Datavyu.getApplication().showWarningDialog("Error: Column name already exists.");
                    return;
                }
            }
            variable.setName(newName);
            UndoableEdit edit = new ChangeNameVariableEdit(variable.getName(), newName);
            Datavyu.getView().getComponent().revalidate();
            Datavyu.getView().getUndoSupport().postEdit(edit);
        } catch (UserWarningException uwe) {
            Datavyu.getApplication().showWarningDialog(uwe);
            throw new UserWarningException();
        }

    }

    // *************************************************************************
    // Parent Class Overrides
    // *************************************************************************
    @Override
    public void requestFocus() {
        Datavyu.getView().getSpreadsheetPanel().revalidate();
        Datavyu.getView().getSpreadsheetPanel().reorientView(this);
        /**
         * Request focus for this column. It will request focus for the first
         * SpreadsheetCell in the column if one exists. If no cells exist it
         * will request focus for the datapanel of the column.
         */
        if (datapanel.getCells().size() > 0) {
//            datapanel.getCells().get(0).requestFocusInWindow();
            SpreadsheetCell sc = datapanel.getSelectedCell();
            if(sc != null) sc.requestFocus();
        } else {
            datapanel.requestFocusInWindow();
        }

    }

    // *************************************************************************
    // VariableListener Overrides
    // *************************************************************************
    @Override
    public void nameChanged(final String newName) {
        this.setText(newName);
    }

    @Override
    public void visibilityChanged(final boolean isHidden) {
        setVisible(!isHidden);
        this.datapanel.setVisible(!isHidden);
        this.setSelected(false);
        columnVisList.columnVisibilityChanged();
    }

    @Override
    public void lockChanged(final boolean locked) {
        updateLockMark();
        // Its clips' onsets show whether they can be edited.
        for (SpreadsheetCell cell : getCells()) {
            cell.valueChange(cell.getCell().getCellValue());
            cell.updateSelectionDisplay();
        }
    }

    @Override
    public void cellInserted(final Cell newCell) {
        datapanel.insertCell(this, newCell, cellSelList);
    }

    @Override
    public void cellRemoved(final Cell deletedCell) {
        datapanel.deleteCell(deletedCell);
        for(SpreadsheetCell c : getCellsTemporally()) {
            if(c.getOnsetTicks() >= deletedCell.getOnset()) {
                c.requestFocus();
                break;
            }
        }
    }

    // *************************************************************************
    // MouseListener Overrides
    // *************************************************************************
    @Override
    public void mouseEntered(final MouseEvent me) {}

    @Override
    public void mouseExited(final MouseEvent me) {
    }

    @Override
    public void mousePressed(final MouseEvent me) {
        if (showHeaderMenu(me)) {
            return;
        }
        if(moveable && !draggable) {
            System.out.println("Pressed X: " + me.getX());
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }
    }

    @Override
    public void mouseReleased(final MouseEvent me) {
        if (showHeaderMenu(me)) {
            return;
        }
        // Fix dragging columns
        // Call column moving routine if user dragged mouse across columns.

        if (moveable) {

            Component c = me.getComponent();

            Point destination = (Point) me.getPoint().clone();
            SwingUtilities.convertPointToScreen(destination, c);
            SwingUtilities.convertPointFromScreen(destination, transparentPanel);

            transparentPanel.setDestination(destination);
            transparentPanel.setVisible(false);
            transparentPanel.setImage(null);

            int x = me.getX();
            logger.info("Released X: " + x);
            // Iterate over the spreadsheet columns, starting at current column, to figure out how many
            // positions to shift by.
            SpreadSheetPanel sp = (SpreadSheetPanel) Datavyu.getView().getComponent();
            List<SpreadsheetColumn> cols = sp.getVisibleColumns();
            ListIterator<SpreadsheetColumn> itr = cols.listIterator(cols.indexOf(this));

            final int columnWidth = this.getWidth();

            SpreadsheetColumn swapCol;
            if (x > columnWidth) {
                swapCol = itr.next();
                while (x > swapCol.getWidth()) {
                    x -= swapCol.getWidth();
                    if (itr.hasNext()) swapCol = itr.next();
                    else break;
                }
                sp.moveColumn(this.getVariable(), swapCol.getVariable());
            } else if (x < 0 && itr.hasPrevious()) {
                swapCol = itr.previous();
                while (x < 0 && itr.hasPrevious()) {
                    x += swapCol.getWidth();
                    if (itr.hasPrevious() && x < 0) swapCol = itr.previous();
                    else break;
                }
                sp.moveColumn(this.getVariable(), swapCol.getVariable());
            }

            // Update globals
            moveable = false;
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }

    @Override
    public void mouseClicked(final MouseEvent me) {
        if (me.isPopupTrigger() || SwingUtilities.isRightMouseButton(me) || me.isControlDown()) {
            me.consume();
            return;
        }
        if (me.getClickCount() == 2) {
            showChangeVarNameDialog();
        }
        else {
            int keyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

            boolean groupSel = (((me.getModifiers() & ActionEvent.SHIFT_MASK)
                    != 0) || ((me.getModifiers() & keyMask) != 0));
            boolean curSelected = this.isSelected();

            if (!groupSel) {
                this.columnSelList.clearColumnSelection();
            }

            this.setSelected(!curSelected);
            this.columnSelList.addColumnToSelection(this);
        }

        me.consume();
    }

    // *************************************************************************
    // MouseMotionListener Overrides
    // *************************************************************************

    @Override
    public void mouseDragged(final MouseEvent me) {
        // Implements columns dragging.
        if (draggable) {
            int newWidth = me.getX();

            if (newWidth >= this.getMinimumSize().width) {
                this.setWidth(newWidth);
            }
        } else if (moveable) {
            if (transparentPanel.getImage() == null) {
                Component c = me.getComponent();

                BufferedImage image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics g = image.getGraphics();
                c.paint(g);

                transparentPanel.setVisible(true);

                Point destination = (Point) me.getPoint().clone();
                SwingUtilities.convertPointToScreen(destination, c);
                SwingUtilities.convertPointFromScreen(destination, transparentPanel);

                transparentPanel.setDestination(destination);
                transparentPanel.setImage(image);
                transparentPanel.setBackground(Color.BLACK);
                transparentPanel.repaint();
            }

            Component c = me.getComponent();

            Point destination = (Point) me.getPoint().clone();
            SwingUtilities.convertPointToScreen(destination, c);
            SwingUtilities.convertPointFromScreen(destination, transparentPanel);
            transparentPanel.setDestination(destination);

            transparentPanel.repaint();
        }
    }

    @Override
    public void mouseMoved(final MouseEvent me) {
        final int xCoord = me.getX();
        final int componentWidth = this.getSize().width;
        final int RESIZE_BOUNDARY_SIZE = Math.max(10, (int)(componentWidth * 0.10));
//        final int rangeStart = Math.round(componentWidth / 4F);
//        final int rangeEnd = Math.round(3F * componentWidth / 4F);

        // Implements columns dragging.
        if ((componentWidth - xCoord) < RESIZE_BOUNDARY_SIZE) {
            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
            draggable = true;

            // Implements moveable columns
        } else if (!draggable) {
            moveable = true;
        } else {
            draggable = false;
            moveable = false;
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }

    @Override
    public void clockPeriodicSync(double clockTime) {
        if(isSelected()
            && Datavyu.getVideoController().getCellHighlightAndFocus()
            && !Datavyu.getVideoController().getClockTimer().isPaused()) {
            focusNextCell();
        }
    }

    @Override
    public void clockForceSync(double clockTime) { }

    @Override
    public void clockRate(float rate) { }

    @Override
    public void clockStart(double clockTime) { }

    @Override
    public void clockStop(double clockTime) { }

    @Override
    public void clockPause(double clockTime) { }

    @Override
    public void clockBoundaryCheck(double clockTime) { }

    @Override
    public void streamsBoundaryCheck(double clockTime) { }

    @Override
    public void clockSeekPlayback(double clockTime) { }
}
