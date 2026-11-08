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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.sh0sh1n.watavyu.controllers.ColumnCommands;
import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.*;
import com.sh0sh1n.watavyu.util.ClockTimer;
import com.sh0sh1n.watavyu.util.Constants;
import com.sh0sh1n.watavyu.views.LockMark;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.ListIterator;

/**
 * This class maintains the visual representation of the column in the
 * Spreadsheet window.
 */
public final class SpreadsheetColumn extends JLabel implements ColumnListener, MouseListener, MouseMotionListener,
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
     * Reference to the column.
     */
    private Column dataColumn;

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


    private int previouslyFocusedCellIdx = -1;

    /** The spreadsheet this column is in */
    private final ProjectController controller;

    /**
     * Creates new SpreadsheetColumn.
     *
     * @param controller the spreadsheet this column is in
     * @param var    the column this column displays.
     * @param cellSelL Spreadsheet cell selection listener to notify
     * @param colSelL  Column selection listener to notify.
     */
    public SpreadsheetColumn(final ProjectController controller,
                             final Column var,
                             final CellSelectionListener cellSelL,
                             final ColumnSelectionListener colSelL,
                             final ColumnVisibilityListener colVisL) {
        this.controller = controller;
        this.dataStore = controller.getDataStore();
        this.dataColumn = var;
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

        controller.getVideoController().getClockTimer().registerListener(this);
    }

    /** Returns the spreadsheet this column is in. */
    public ProjectController getProjectController() {
        return controller;
    }

    /** The window's glass pane, which shows a column's image while it is dragged. */
    private TransparentPanel transparentPanel() {
        return (TransparentPanel) SwingUtilities.getRootPane(this).getGlassPane();
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

    /** The mark on a locked column's header, which shakes when the column refuses a change. */
    private final LockMark lockMark = new LockMark(14, this);

    /** Shows the lock mark while the column is locked. */
    private void updateLockMark() {
        setIcon(dataColumn.isLocked() ? lockMark : null);
        setToolTipText(dataColumn.isLocked() ? "Locked: right-click to unlock" : null);
    }

    /** Returns the mark shown while the column is locked. */
    LockMark getLockMark() {
        return lockMark;
    }

    /**
     * Makes the Lock Column menu item of a column's header: it shows whether the column is locked and toggles it.
     *
     * @param columns the spreadsheet's column commands
     * @param column  the column whose header was clicked
     * @return the menu item
     */
    static JCheckBoxMenuItem lockItem(final ColumnCommands columns, final Column column) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem("Lock Column", column.isLocked());
        item.setName("lockColumn");
        item.addActionListener(e -> columns.setLocked(column, item.isSelected()));
        return item;
    }

    /**
     * Makes the Hide menu item of a column's header: it hides the column, or every selected column if the column is
     * one of them.
     *
     * @param columns   the spreadsheet's column commands
     * @param dataStore the spreadsheet holding the column
     * @param column    the column whose header was clicked
     * @return the menu item
     */
    static JMenuItem hideItem(final ColumnCommands columns, final DataStore dataStore, final Column column) {
        List<Column> hidden = actedOn(dataStore, column);
        JMenuItem item = new JMenuItem(hidden.size() == 1 ? "Hide Column" : "Hide Selected Columns");
        item.setName("hideColumns");
        item.addActionListener(e -> columns.hide(hidden));
        return item;
    }

    /**
     * Makes the Delete menu item of a column's header: it deletes the column, or every selected column if the column
     * is one of them. It is disabled while any of those columns is locked.
     *
     * @param columns   the spreadsheet's column commands
     * @param dataStore the spreadsheet holding the column
     * @param column    the column whose header was clicked
     * @return the menu item
     */
    static JMenuItem deleteItem(final ColumnCommands columns, final DataStore dataStore, final Column column) {
        List<Column> deleted = actedOn(dataStore, column);
        JMenuItem item = new JMenuItem(deleted.size() == 1 ? "Delete Column" : "Delete Selected Columns");
        item.setName("deleteColumns");
        item.setEnabled(deleted.stream().noneMatch(Column::isLocked));
        item.addActionListener(e -> columns.delete(deleted));
        return item;
    }

    /** @return the columns a header's menu acts on: the column, or every selected column if it is one of them */
    private static List<Column> actedOn(final DataStore dataStore, final Column column) {
        return column.isSelected() ? dataStore.getSelectedColumns() : List.of(column);
    }

    /** Shows the column's header menu, if this event asks for one. */
    private boolean showHeaderMenu(final MouseEvent me) {
        if (!me.isPopupTrigger()) {
            return false;
        }
        JPopupMenu menu = new JPopupMenu();
        menu.add(hideItem(controller.columns(), dataStore, dataColumn));
        menu.add(deleteItem(controller.columns(), dataStore, dataColumn));
        menu.addSeparator();
        menu.add(lockItem(controller.columns(), dataColumn));
        menu.show(this, me.getX(), me.getY());
        me.consume();
        return true;
    }

    /**
     * Returns the height in pixels of the cells that have been laid in this
     * column.
     */
    public int getWorkingHeight() {
        return dataHeight;
    }

    /**
     * Sets the height of the column's laid-out cells.
     *
     * @param newHeight The height in pixels of the cells that have been laid
     *                  in this column.
     */
    public void setWorkingHeight(final int newHeight) {
        dataHeight = newHeight;
    }

    public Column getColumn() {
        return dataColumn;
    }

    /**
     * Registers this spreadsheet column with everything that needs to notify
     * this class of events.
     */
    public void registerListeners() {
        addMouseListener(this);
        addMouseMotionListener(this);
        dataColumn.addListener(this);
        datapanel.registerListeners();
    }

    /**
     * Deregisters this spreadsheet column with everything that is currently
     * notiying it of events.
     */
    public void deregisterListeners() {
        removeMouseListener(this);
        removeMouseMotionListener(this);
        dataColumn.removeListener(this);
        datapanel.deregisterListeners();
        controller.getVideoController().getClockTimer().unRegisterListener(this);
    }

    /**
     * Returns column Header size as a dimension.
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
     * Sets the column's width.
     *
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
     * Returns the datapanel.
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
     * Returns selection status of underlying column.
     */
    public boolean isSelected() {
        return dataColumn.isSelected();
    }

    /**
     * Set the selected state for the DataColumn this displays.
     *
     * @param isSelected Selected state.
     */
    public void setSelected(final boolean isSelected) {
        LOGGER.info("select column");
        dataColumn.setSelected(isSelected);
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
     * Returns the SpreadsheetCells in this column.
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
     * Returns the Spreadsheet cells in this column temporally.
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
     * Returns the header name of this SpreadsheetColumn.
     */
    public String getColumnName() {
        return dataColumn.getName();
    }

    private void focusNextCell() {
        long time = controller.getVideoController().getCurrentTime();
        List<SpreadsheetCell> tempCells = getCellsTemporally();
        for (SpreadsheetCell c : tempCells) {
            if (c.getCell().isInTimeWindow(time)) {
                if(!c.isFocusOwner()) {
                    int firstEmpty = c.getCell().getValues().indexOf("");
                    c.requestFocus();
                    if(firstEmpty > -1) {
                        c.getDataView().getEdTracker().setEditor(c.getDataView().getEdTracker().getEditorAtArgIndex(firstEmpty));
                    } else {
                        c.getDataView().getEdTracker().setEditor(c.getDataView().getEdTracker().getEditorAtIndex(previouslyFocusedCellIdx > -1 ? previouslyFocusedCellIdx : 0));
                    }
                }
                if(c.getCell().getOnset() > time) {
                    break;
                }
                break;
            }
        }
    }

    // *************************************************************************
    // Parent Class Overrides
    // *************************************************************************
    @Override
    public void requestFocus() {
        controller.getSpreadSheetPanel().revalidate();
        controller.getSpreadSheetPanel().reorientView(this);
        // Request focus for this column. It will request focus for the first SpreadsheetCell in the column if one
        // exists. If no cells exist it will request focus for the datapanel of the column.
        if (datapanel.getCells().size() > 0) {
//            datapanel.getCells().get(0).requestFocusInWindow();
            SpreadsheetCell sc = datapanel.getSelectedCell();
            if(sc != null) sc.requestFocus();
        } else {
            datapanel.requestFocusInWindow();
        }

    }

    // *************************************************************************
    // ColumnListener Overrides
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
    public void editRefused() {
        lockMark.shake();
    }

    @Override
    public void lockChanged(final boolean locked) {
        updateLockMark();
        // Its clips' onsets show whether they can be edited.
        redrawValues();
    }

    /** Redraws each cell's values, which follow a code added, deleted or moved. */
    @Override
    public void codesChanged() {
        redrawValues();
    }

    /**
     * Redraws the cells that show a renamed code's name, as each key typed renames it: a cell draws a code's name only
     * as the placeholder of an empty value.
     */
    @Override
    public void codeRenamed(final int index) {
        for (SpreadsheetCell cell : getCells()) {
            if (cell.getCell().getValue(index).isEmpty()) {
                cell.redrawValues();
            }
        }
    }

    private void redrawValues() {
        for (SpreadsheetCell cell : getCells()) {
            cell.redrawValues();
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
            SwingUtilities.convertPointFromScreen(destination, transparentPanel());

            transparentPanel().setDestination(destination);
            transparentPanel().setVisible(false);
            transparentPanel().setImage(null);

            int x = me.getX();
            logger.info("Released X: " + x);
            // Iterate over the spreadsheet columns, starting at current column, to figure out how many
            // positions to shift by.
            SpreadSheetPanel sp = controller.getSpreadSheetPanel();
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
                controller.columns().move(dataColumn, swapCol.getColumn());
            } else if (x < 0 && itr.hasPrevious()) {
                swapCol = itr.previous();
                while (x < 0 && itr.hasPrevious()) {
                    x += swapCol.getWidth();
                    if (itr.hasPrevious() && x < 0) swapCol = itr.previous();
                    else break;
                }
                controller.columns().move(dataColumn, swapCol.getColumn());
            }

            // Update globals
            moveable = false;
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }

    @Override
    public void mouseClicked(final MouseEvent me) {
        if (SecondaryClick.is(me)) {
            me.consume();
            return;
        }
        // The second click of a double-click leaves the selection as the first made it.
        if (me.getClickCount() == 1) {
            int keyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            boolean groupSel = me.isShiftDown() || (me.getModifiersEx() & keyMask) != 0;
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
            if (transparentPanel().getImage() == null) {
                Component c = me.getComponent();

                BufferedImage image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics g = image.getGraphics();
                c.paint(g);

                transparentPanel().setVisible(true);

                Point destination = (Point) me.getPoint().clone();
                SwingUtilities.convertPointToScreen(destination, c);
                SwingUtilities.convertPointFromScreen(destination, transparentPanel());

                transparentPanel().setDestination(destination);
                transparentPanel().setImage(image);
                transparentPanel().setBackground(Color.BLACK);
                transparentPanel().repaint();
            }

            Component c = me.getComponent();

            Point destination = (Point) me.getPoint().clone();
            SwingUtilities.convertPointToScreen(destination, c);
            SwingUtilities.convertPointFromScreen(destination, transparentPanel());
            transparentPanel().setDestination(destination);

            transparentPanel().repaint();
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
            && controller.getVideoController().getCellHighlightAndFocus()
            && !controller.getVideoController().getClockTimer().isPaused()) {
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
