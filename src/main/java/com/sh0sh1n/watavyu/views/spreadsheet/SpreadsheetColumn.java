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
        KeyEventDispatcher {

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

    /** The sheet that draws this column's cells. */
    private final SpreadsheetView sheet;

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

    private int previouslyFocusedCellIdx = -1;

    /** The spreadsheet this column is in */
    private final ProjectController controller;

    /**
     * Creates new SpreadsheetColumn.
     *
     * @param controller the spreadsheet this column is in
     * @param var    the column this column displays.
     * @param sheet  the sheet that draws its cells
     * @param cellSelL Spreadsheet cell selection listener to notify
     * @param colSelL  Column selection listener to notify.
     */
    public SpreadsheetColumn(final ProjectController controller,
                             final Column var,
                             final SpreadsheetView sheet,
                             final CellSelectionListener cellSelL,
                             final ColumnSelectionListener colSelL,
                             final ColumnVisibilityListener colVisL) {
        this.controller = controller;
        this.dataStore = controller.getDataStore();
        this.dataColumn = var;
        this.sheet = sheet;
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

        this.setVisible(!var.isHidden());

    }

    /** Returns the spreadsheet this column is in. */
    public ProjectController getProjectController() {
        return controller;
    }

    /** Returns what hears of the selection of this column's cells. */
    CellSelectionListener getCellSelectionListener() {
        return cellSelList;
    }

    /** Returns the sheet that draws this column's cells. */
    SpreadsheetView getSheet() {
        return sheet;
    }

    /**
     * Selects a cell of this column, and only it and this column, in command mode: the sheet takes the focus, and the
     * editor goes over the cell without it. New Cell from the menu then goes to this column.
     *
     * @param cell a cell of this column
     */
    void selectInCommandMode(final Cell cell) {
        columnSelList.clearColumnSelection();
        cellSelList.clearCellSelection();
        setSelected(true);
        selectInColumn(cell);
        controller.setLastCreatedColumn(dataColumn);
        if (controller.getSpreadSheetPanel() != null) {
            controller.getSpreadSheetPanel().reorientView(cell);
        }
        sheet.commandMode();
    }

    /** Selects a cell of this column alone among its cells, with the editor waiting over it. */
    private void selectInColumn(final Cell cell) {
        for (Cell other : getCellsTemporally()) {
            if (other.isSelected() && !other.equals(cell)) {
                other.setSelected(false);
            }
        }
        sheet.edit(cell);
        cell.setHighlighted(true);
        cellSelList.setHighlightedCell(cell);
        controller.setLastSelectedCell(cell);
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
        for (Cell cell : dataColumn.getCells()) {
            sheet.watch(cell);
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    }

    /**
     * Deregisters this spreadsheet column with everything that is currently
     * notiying it of events.
     */
    public void deregisterListeners() {
        removeMouseListener(this);
        removeMouseMotionListener(this);
        dataColumn.removeListener(this);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
        for (Cell cell : dataColumn.getCells()) {
            sheet.unwatch(cell);
        }
    }

    /**
     * Returns column Header size as a dimension.
     */
    public Dimension getHeaderSize() {
        return new Dimension(getWidth(), DEFAULT_HEADER_HEIGHT);
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

        sheet.revalidate();
        sheet.repaint();
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

    /** Returns the column's cells in time order. */
    public List<Cell> getCellsTemporally() {
        return dataColumn.getCellsTemporally();
    }

    /** Returns the first selected cell in time order, or null if none is. */
    public Cell getSelectedCell() {
        for (Cell cell : getCellsTemporally()) {
            if (cell.isSelected()) {
                return cell;
            }
        }
        return null;
    }

    /**
     * Returns the cell whose onset is nearest a cell's, the first of those as near.
     *
     * @param cell a cell, in this column or another
     * @return the nearest cell, or null for a column without cells
     */
    public Cell getNearestCellTemporally(final Cell cell) {
        Cell closestCell = null;
        for (Cell c : getCellsTemporally()) {
            if (closestCell == null
                    || Math.abs(cell.getOnset() - c.getOnset()) < Math.abs(cell.getOnset() - closestCell.getOnset())) {
                closestCell = c;
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
        for (Cell c : getCellsTemporally()) {
            if (c.isInTimeWindow(time)) {
                if (!sheet.inInsertMode()) {
                    // Outside insert mode the keyboard is left where it is, in command mode or another window: the
                    // cell in play is selected alone in this column. The other columns, and where the sheet is
                    // scrolled, are left as they are.
                    if (!c.isSelected() || getCellsTemporally().stream().filter(Cell::isSelected).count() > 1) {
                        selectInColumn(c);
                    }
                    break;
                }
                SpreadsheetCell editor = sheet.edit(c);
                if (!editor.isFocusOwner()) {
                    int firstEmpty = c.getValues().indexOf("");
                    editor.requestFocus();
                    EditorTracker tracker = editor.getDataView().getEdTracker();
                    if (firstEmpty > -1) {
                        tracker.setEditor(tracker.getEditorAtArgIndex(firstEmpty));
                    } else {
                        tracker.setEditor(tracker.getEditorAtIndex(Math.max(previouslyFocusedCellIdx, 0)));
                    }
                }
                break;
            }
        }
    }

    /**
     * ↑ and ↓ move the selection to the cell before or after the selected one in time, in this column, keeping the
     * caret in the same value; the first and last cells go no further.
     *
     * @param e The key event to dispatch.
     * @return true if the event has been consumed by this dispatch, false
     * otherwise
     */
    @Override
    public boolean dispatchKeyEvent(final KeyEvent e) {
        if (e.getID() != KeyEvent.KEY_PRESSED
                || (e.getKeyCode() != KeyEvent.VK_UP && e.getKeyCode() != KeyEvent.VK_DOWN)) {
            return false;
        }
        Cell selected = getSelectedCell();
        // A cell stays selected in command mode with the focus gone elsewhere; the keys are then not this sheet's.
        if (selected == null || !sheet.hasKeyboard()) {
            return false;
        }
        List<Cell> cells = getCellsTemporally();
        int target = cells.indexOf(selected) + (e.getKeyCode() == KeyEvent.VK_UP ? -1 : 1);
        if (target < 0 || target >= cells.size()) {
            return false;
        }
        Cell next = cells.get(target);
        if (sheet.inCommandMode()) {
            // Command mode stays.
            selectInCommandMode(next);
            e.consume();
            return true;
        }
        int pos = sheet.getEditor().filter(editor -> editor.getCell().equals(selected))
                .map(editor -> editor.getDataView().getEdTracker().indexOfCurrentEditor()).orElse(-1);
        selected.setHighlighted(false);
        selected.setSelected(false);
        next.setHighlighted(true);
        next.setSelected(true);
        SpreadsheetCell editor = sheet.edit(next);
        EditorTracker tracker = editor.getDataView().getEdTracker();
        tracker.setEditor(tracker.getEditorAtIndex(Math.max(pos, 0)));
        sheet.focusEditor(editor);
        e.consume();
        return true;
    }

    // *************************************************************************
    // Parent Class Overrides
    // *************************************************************************
    @Override
    public void requestFocus() {
        controller.getSpreadSheetPanel().revalidate();
        controller.getSpreadSheetPanel().reorientView(this);
        // The column's selected cell takes the focus, if it has one; otherwise the sheet does, in command mode with
        // the column selected and no cell.
        Cell selectedCell = getSelectedCell();
        if (selectedCell != null) {
            sheet.focusEditor(sheet.edit(selectedCell));
        } else {
            sheet.commandMode();
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
        sheet.revalidate();
        sheet.repaint();
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
     * Redraws the cells, which show a renamed code's name as the placeholder of an empty value, as each key typed
     * renames it.
     */
    @Override
    public void codeRenamed(final int index) {
        redrawValues();
    }

    private void redrawValues() {
        sheet.redrawColumn(this);
    }

    /** A new cell is drawn, and takes the focus. */
    @Override
    public void cellInserted(final Cell newCell) {
        sheet.watch(newCell);
        sheet.revalidate();
        sheet.edit(newCell).requestFocus();
    }

    /** A deleted cell goes from the sheet, and the cell after it in time takes the focus. */
    @Override
    public void cellRemoved(final Cell deletedCell) {
        sheet.unwatch(deletedCell);
        for (Cell c : getCellsTemporally()) {
            if (c.getOnset() >= deletedCell.getOnset()) {
                sheet.edit(c).requestFocus();
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

    /**
     * In highlight-and-focus mode, while the clock runs, puts the focus in the selected column's cell in play, or in
     * command mode selects it. Called by the sheet on the event thread as the clock ticks.
     */
    void followNeedle() {
        if (isSelected()
            && controller.getVideoController().getCellHighlightAndFocus()
            && !controller.getVideoController().getClockTimer().isPaused()) {
            focusNextCell();
        }
    }
}
