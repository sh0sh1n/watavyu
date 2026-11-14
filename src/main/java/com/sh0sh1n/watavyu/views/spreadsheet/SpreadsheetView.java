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

import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.CellListener;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.util.ClockTimer;
import com.sh0sh1n.watavyu.util.Settings;
import com.sh0sh1n.watavyu.views.VideoController;
import com.sh0sh1n.watavyu.views.spreadsheet.editors.CellValuesView;
import com.sh0sh1n.watavyu.views.spreadsheet.layouts.SheetGeometry;

import javax.swing.CellRendererPane;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

/**
 * The sheet of cells, in the spreadsheet's scroll pane: it draws the cells of its shown columns where the layout put
 * them ({@link SheetGeometry}), as {@code JTable} draws its cells, and puts one editor ({@link SpreadsheetCell}) over
 * the cell in focus. A press on a drawn cell moves the editor there and hands it the press, so the cell takes the
 * click as its own view would. The sheet follows its cells and the clock, drawing a cell again when it changes or
 * the needle moves past it, and asking to be laid out again when a cell changes in time or size.
 */
public final class SpreadsheetView extends JPanel implements Scrollable {

    /**
     * Maximum unit scroll amount.
     */
    private static final int MAX_UNIT_INCREMENT = 50;

    /** The playhead's line across the columns: its needle's colour, faint enough to read the cells through. */
    static final Color PLAYHEAD_LINE = new Color(SpreadsheetTheme.PALETTE.accent().getRed(),
            SpreadsheetTheme.PALETTE.accent().getGreen(), SpreadsheetTheme.PALETTE.accent().getBlue(), 90);

    /** The spreadsheet this sheet shows. */
    private final ProjectController controller;

    /** The columns, shown and hidden, in the spreadsheet's order. */
    private final List<SpreadsheetColumn> columns = new ArrayList<>();

    /** Where the layout last put the cells. */
    private SheetGeometry geometry = SheetGeometry.EMPTY;

    /** Where the playhead's line crosses the columns, or -1 for no line. */
    private int playheadY = -1;

    /** Draws each cell. */
    private final CellRenderer renderer = new CellRenderer();

    /** Paints the renderer where each cell lies. */
    private final CellRendererPane rendererPane = new CellRendererPane();

    /** The editor over the cell in focus, or null when no cell has had the focus. */
    private SpreadsheetCell editor;

    /** The part of the editor a press on a drawn cell went to, which takes the drag, release and click after it. */
    private Component pressedPart;

    /** What follows each cell of the shown and hidden columns. */
    private final Map<Cell, CellListener> watchers = new HashMap<>();

    /** How tall each cell was measured to be, and what for. */
    private final Map<Cell, Measured> heights = new HashMap<>();

    /** Where each drawn cell stood against the needle when it was last drawn, so a clock tick redraws only moves. */
    private final Map<Cell, CellLook.Time> shownTimes = new HashMap<>();

    /** Follows the clock, to draw cells again as the needle passes them. */
    private final ClockFollower clockFollower = new ClockFollower();

    /** Whether the sheet has closed, so a tick handed to the event thread before it closed does nothing. */
    private boolean closed;

    /** The width the column header has after its columns: its buttons. */
    private IntSupplier trailingWidth = () -> 0;

    /**
     * How tall a cell was measured to be, and for what width, font and values.
     *
     * @param width  the cell's width
     * @param font   the values' font
     * @param values the values' text
     * @param height its height
     */
    private record Measured(int width, Font font, String values, int height) {
    }

    /**
     * @param controller the spreadsheet this sheet shows, with its playback set
     */
    public SpreadsheetView(final ProjectController controller) {
        this.controller = controller;
        setLayout(null);
        setDoubleBuffered(true);
        add(rendererPane);
        MouseAdapter mouse = new PressHandOver();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        controller.getVideoController().getClockTimer().registerListener(clockFollower);
    }

    /** Stops following the clock and the cells, once the sheet is closed. */
    public void close() {
        closed = true;
        controller.getVideoController().getClockTimer().unRegisterListener(clockFollower);
        for (Map.Entry<Cell, CellListener> watched : List.copyOf(watchers.entrySet())) {
            watched.getKey().removeListener(watched.getValue());
        }
        watchers.clear();
        retire(editor);
        editor = null;
    }

    /**
     * Sets the width the column header has after its columns, so the sheet is as wide as the header.
     *
     * @param trailingWidth the width of the header's buttons after the columns
     */
    void setTrailingWidth(final IntSupplier trailingWidth) {
        this.trailingWidth = trailingWidth;
    }

    // *************************************************************************
    // Columns
    // *************************************************************************

    /**
     * Adds a column to this spreadsheet view.
     *
     * @param newColumn The new column to add this spreadsheet view.
     */
    public void addColumn(final SpreadsheetColumn newColumn) {
        columns.add(newColumn);
        newColumn.setExclusiveSelected(newColumn.isSelected()); //this will ensure visual highlighting accurately reflects column selected state
        revalidate();
        repaint();
    }

    /**
     * Removes a column from this spreadsheet view.
     *
     * @param delColumn The column to remove from this spreadsheet view.
     */
    public void removeColumn(final SpreadsheetColumn delColumn) {
        columns.remove(delColumn);
        revalidate();
        repaint();
    }

    /**
     * Puts the columns in a new order.
     *
     * @param arranged the columns this view holds, in their new order
     */
    public void arrangeColumns(final List<SpreadsheetColumn> arranged) {
        columns.clear();
        columns.addAll(arranged);
        revalidate();
        repaint();
    }

    /**
     * Returns all the columns held in this spreadsheet view.
     */
    public List<SpreadsheetColumn> getColumns() {
        return columns;
    }

    /** Returns the shown columns, with their cells in time order, as a layout takes them. */
    public List<SheetGeometry.ColumnCells> shownColumns() {
        List<SheetGeometry.ColumnCells> shown = new ArrayList<>();
        for (SpreadsheetColumn column : columns) {
            if (column.isVisible()) {
                shown.add(new SheetGeometry.ColumnCells(column.getColumn(), column.getWidth(),
                        List.copyOf(column.getColumn().getCellsTemporally())));
            }
        }
        return shown;
    }

    private Optional<SpreadsheetColumn> columnOf(final Column column) {
        return columns.stream().filter(shown -> shown.getColumn().equals(column)).findFirst();
    }

    /** Returns where a column starts across the sheet, or -1 if it is not shown. */
    private int columnX(final SpreadsheetColumn column) {
        int x = 0;
        for (SpreadsheetColumn each : columns) {
            if (each.isVisible()) {
                if (each.equals(column)) {
                    return x;
                }
                x += each.getWidth();
            }
        }
        return -1;
    }

    // *************************************************************************
    // Layout
    // *************************************************************************

    /**
     * Returns how tall a cell needs to be in its column, measuring it again only when its width, font or values have
     * changed since it was last measured.
     *
     * @param cell a cell of a shown column
     */
    public int heightOf(final Cell cell) {
        int width = columnOf(cell.getColumn()).map(SpreadsheetColumn::getWidth).orElse(CellParts.STRETCH) - 1;
        Font font = SpreadsheetTheme.dataFont();
        String values = CellValuesView.textOf(cell);
        Measured measured = heights.get(cell);
        if (measured == null || measured.width() != width || !measured.font().equals(font)
                || !measured.values().equals(values)) {
            measured = new Measured(width, font, values, renderer.height(cell, width));
            heights.put(cell, measured);
        }
        return measured.height();
    }

    /**
     * Takes where the layout has put the cells, moves the editor over its cell, and draws the sheet again. Called
     * while the scroll pane is laid out: a sheet that has changed size is invalidated, so its viewport resizes it in
     * the same pass.
     *
     * @param placed where the cells now lie
     */
    public void setGeometry(final SheetGeometry placed) {
        Dimension before = getPreferredSize();
        geometry = placed;
        if (!getPreferredSize().equals(before)) {
            invalidate();
        }
        placeEditor(true);
        repaint();
    }

    /** Returns where the layout last put the cells. */
    SheetGeometry getGeometry() {
        return geometry;
    }

    /**
     * Returns where a cell lies in the sheet, or empty if it is not drawn, as the cells of a hidden column are not.
     *
     * @param cell a cell
     */
    public Optional<Rectangle> boundsOf(final Cell cell) {
        Optional<SpreadsheetColumn> column = columnOf(cell.getColumn());
        Optional<SheetGeometry.Placed> placed = geometry.of(cell);
        if (column.isEmpty() || placed.isEmpty() || columnX(column.get()) < 0) {
            return Optional.empty();
        }
        Rectangle bounds = new Rectangle(placed.get().bounds());
        bounds.translate(columnX(column.get()), 0);
        return Optional.of(bounds);
    }

    /**
     * Returns the cell drawn at a point in the sheet, if any.
     *
     * @param point a point in the sheet
     */
    public Optional<Cell> cellAt(final Point point) {
        int x = 0;
        for (SpreadsheetColumn column : columns) {
            if (!column.isVisible()) {
                continue;
            }
            if (point.x >= x && point.x < x + column.getWidth()) {
                for (Cell cell : column.getColumn().getCellsTemporally()) {
                    Optional<SheetGeometry.Placed> placed = geometry.of(cell);
                    if (placed.isPresent() && placed.get().bounds().contains(point.x - x, point.y)) {
                        return Optional.of(cell);
                    }
                }
                return Optional.empty();
            }
            x += column.getWidth();
        }
        return Optional.empty();
    }

    @Override
    public Dimension getPreferredSize() {
        int width = trailingWidth.getAsInt();
        for (SpreadsheetColumn column : columns) {
            if (column.isVisible()) {
                width += column.getWidth();
            }
        }
        return new Dimension(width, geometry.height());
    }

    // *************************************************************************
    // The editor
    // *************************************************************************

    /**
     * Puts the editor over a cell, making it if it is over another, and returns it. Until the layout has placed a
     * new cell, its editor has no size; it is placed with the cell.
     *
     * @param cell a cell of one of this sheet's columns
     * @return the editor over the cell
     */
    public SpreadsheetCell edit(final Cell cell) {
        if (editor != null && editor.getCell().equals(cell)) {
            return editor;
        }
        SpreadsheetColumn column = columnOf(cell.getColumn())
                .orElseThrow(() -> new IllegalArgumentException("The cell's column is not in this sheet"));
        SpreadsheetCell next = new SpreadsheetCell(column, cell, column.getCellSelectionListener());
        cell.addListener(next);
        add(next, 0);
        retire(editor);
        editor = next;
        placeEditor(false);
        return next;
    }

    /** Returns the editor over the cell in focus, if any. */
    public Optional<SpreadsheetCell> getEditor() {
        return Optional.ofNullable(editor);
    }

    /**
     * Moves the editor over its cell, with the cell's ordinal and overlap mark. After a layout, an editor whose cell
     * is no longer drawn, as when its column is hidden or the cell deleted, leaves the sheet.
     */
    private void placeEditor(final boolean laidOut) {
        if (editor == null) {
            return;
        }
        Cell cell = editor.getCell();
        Optional<Rectangle> bounds = boundsOf(cell);
        if (bounds.isEmpty()) {
            if (laidOut) {
                retire(editor);
                editor = null;
            } else {
                editor.setBounds(0, 0, 0, 0);
            }
            return;
        }
        editor.setOrdinal(cell.getColumn().getCellsTemporally().indexOf(cell) + 1);
        editor.setOverlapBorder(geometry.of(cell).map(SheetGeometry.Placed::overlap).orElse(false));
        editor.setBounds(bounds.get());
        editor.validate();
    }

    /**
     * Takes an editor off the sheet. One that holds the focus shrinks out of sight, so its cell is drawn beneath, and
     * leaves once the focus has left it, so that its parts hear they lost the focus, as a cell's view always has, and
     * the cell is deselected as before.
     */
    private void retire(final SpreadsheetCell old) {
        if (old == null) {
            return;
        }
        old.getCell().removeListener(old);
        KeyboardFocusManager focus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        Component owner = focus.getFocusOwner();
        if (owner == null || !SwingUtilities.isDescendingFrom(owner, old)) {
            take(old);
            return;
        }
        Rectangle was = old.getBounds();
        old.setBounds(0, 0, 0, 0);
        repaint(was);
        PropertyChangeListener[] once = new PropertyChangeListener[1];
        once[0] = event -> {
            if (!(event.getNewValue() instanceof Component now) || !SwingUtilities.isDescendingFrom(now, old)) {
                focus.removePropertyChangeListener("focusOwner", once[0]);
                SwingUtilities.invokeLater(() -> take(old));
            }
        };
        focus.addPropertyChangeListener("focusOwner", once[0]);
    }

    private void take(final SpreadsheetCell old) {
        Rectangle was = old.getBounds();
        remove(old);
        repaint(was);
    }

    /** Hands a press on a drawn cell to the editor, moved over the cell, and what follows it to the same part. */
    private final class PressHandOver extends MouseAdapter {
        @Override
        public void mousePressed(final MouseEvent e) {
            pressedPart = null;
            // A right-click or ⌃-click leaves the selection, and the editor, where they are.
            if (SecondaryClick.is(e)) {
                return;
            }
            Optional<Cell> cell = cellAt(e.getPoint());
            if (cell.isEmpty()) {
                return;
            }
            SpreadsheetCell pressed = edit(cell.get());
            Point inEditor = SwingUtilities.convertPoint(SpreadsheetView.this, e.getPoint(), pressed);
            Component part = SwingUtilities.getDeepestComponentAt(pressed, inEditor.x, inEditor.y);
            pressedPart = part == null ? pressed : part;
            handOver(e);
        }

        @Override
        public void mouseReleased(final MouseEvent e) {
            handOver(e);
        }

        @Override
        public void mouseClicked(final MouseEvent e) {
            handOver(e);
            pressedPart = null;
        }

        @Override
        public void mouseDragged(final MouseEvent e) {
            handOver(e);
        }

        private void handOver(final MouseEvent e) {
            if (pressedPart != null) {
                pressedPart.dispatchEvent(SwingUtilities.convertMouseEvent(SpreadsheetView.this, e, pressedPart));
            }
        }
    }

    // *************************************************************************
    // Following the cells and the clock
    // *************************************************************************

    /**
     * Follows a cell, drawing it again when it changes and laying the sheet out again when it changes in time or in
     * what it shows.
     *
     * @param cell a cell of one of this sheet's columns
     */
    public void watch(final Cell cell) {
        watchers.computeIfAbsent(cell, watched -> {
            CellListener watcher = new CellWatcher(watched);
            watched.addListener(watcher);
            return watcher;
        });
    }

    /**
     * Stops following a cell, as when it is deleted, and takes the editor off it.
     *
     * @param cell a cell
     */
    public void unwatch(final Cell cell) {
        CellListener watcher = watchers.remove(cell);
        if (watcher != null) {
            cell.removeListener(watcher);
        }
        heights.remove(cell);
        shownTimes.remove(cell);
        if (editor != null && editor.getCell().equals(cell)) {
            retire(editor);
            editor = null;
        }
        revalidate();
        repaint();
    }

    /** Draws every cell's colours again, after the past-cell dimming changes. */
    public void refreshColours() {
        shownTimes.clear();
        repaint();
        if (editor != null) {
            editor.refreshColours();
        }
    }

    /** Lays out and draws every cell again, as after undo, a change of font or of highlighting. */
    public void redrawCells() {
        revalidate();
        repaint();
        if (editor != null) {
            editor.redrawValues();
            editor.updateSelectionDisplay();
        }
    }

    /**
     * Lays out and draws a column's cells again, as after its codes or its lock change.
     *
     * @param column the column
     */
    public void redrawColumn(final SpreadsheetColumn column) {
        revalidate();
        repaint();
        if (editor != null && editor.getCell().getColumn().equals(column.getColumn())) {
            editor.redrawValues();
            editor.updateSelectionDisplay();
        }
    }

    private void repaintCell(final Cell cell) {
        boundsOf(cell).ifPresent(this::repaint);
    }

    /**
     * Follows the clock on the event thread: the cells, drawn and edited, whose place against the needle has changed
     * are drawn again, and on a tick, in highlight-and-focus mode, a column moves the focus to its cell in
     * play. A jump leaves the focus where it is, as a new cell made at the needle takes it.
     *
     * @param moveFocus whether a tick has come since the sheet last followed the clock
     */
    private void followClock(final boolean moveFocus) {
        if (closed) {
            return;
        }
        if (moveFocus) {
            for (SpreadsheetColumn column : columns) {
                column.followNeedle();
            }
        }
        showTime();
        if (editor != null) {
            editor.followNeedle();
        }
    }

    /** Draws again the cells in sight whose place against the needle has changed since they were drawn. */
    private void showTime() {
        Times now = new Times(controller.getVideoController());
        Rectangle visible = getVisibleRect();
        int x = 0;
        for (SpreadsheetColumn column : columns) {
            if (!column.isVisible()) {
                continue;
            }
            for (Cell cell : column.getColumn().getCellsTemporally()) {
                Optional<SheetGeometry.Placed> placed = geometry.of(cell);
                if (placed.isEmpty()) {
                    continue;
                }
                Rectangle bounds = new Rectangle(placed.get().bounds());
                bounds.translate(x, 0);
                if (bounds.intersects(visible) && now.of(cell) != shownTimes.get(cell)) {
                    repaint(bounds);
                }
            }
            x += column.getWidth();
        }
    }

    /** How cells stand against the needle at one moment, as the sheet and its editor show it. */
    static final class Times {
        private final boolean highlighting;
        private final long now;

        Times(final VideoController playback) {
            highlighting = playback.getCellHighlightAndFocus() || playback.getCellHighlighting();
            now = playback.getCurrentTime();
        }

        CellLook.Time of(final Cell cell) {
            return highlighting ? CellLook.timeOf(cell.getOnset(), cell.getOffset(), now) : CellLook.Time.AHEAD;
        }
    }

    /** Follows one cell for the sheet. */
    private final class CellWatcher implements CellListener {
        private final Cell cell;

        CellWatcher(final Cell cell) {
            this.cell = cell;
        }

        @Override
        public void offsetChanged(final long newOffset) {
            revalidate();
            repaint();
        }

        @Override
        public void onsetChanged(final long newOnset) {
            revalidate();
            repaint();
        }

        @Override
        public void highlightingChange(final boolean isHighlighted) {
            repaintCell(cell);
        }

        @Override
        public void selectionChange(final boolean isSelected) {
            repaintCell(cell);
        }

        @Override
        public void contentChanged() {
            revalidate();
            repaintCell(cell);
        }
    }

    /**
     * Hears the clock on its thread and follows it on the event thread, where all Swing work belongs: one tick at a
     * time, skipping ticks that come while one is still waiting there.
     */
    private final class ClockFollower implements ClockTimer.ClockListener {
        /** Whether the sheet is waiting on the event thread to follow the clock. */
        private final AtomicBoolean waiting = new AtomicBoolean();

        /** Whether a tick has come since the sheet last followed the clock. */
        private final AtomicBoolean ticked = new AtomicBoolean();

        private void handOver() {
            if (waiting.compareAndSet(false, true)) {
                SwingUtilities.invokeLater(() -> {
                    waiting.set(false);
                    followClock(ticked.getAndSet(false));
                });
            }
        }

        @Override
        public void clockForceSync(final double clockTime) {
            handOver();
        }

        @Override
        public void clockTick(final double clockTime) {
            ticked.set(true);
            handOver();
        }

        @Override
        public void clockPause(final double clockTime) {
            // A pause leaves the needle where it is.
        }

        @Override
        public void clockStart(final double clockTime) {
            // The ticks that follow move the needle.
        }

        @Override
        public void clockRate(final float rate) {
            // The rate does not change where the needle is.
        }
    }

    // *************************************************************************
    // Painting
    // *************************************************************************

    /** Draws the columns and the cells in sight: the ground below each column's cells, its rule, and each cell. */
    @Override
    protected void paintComponent(final Graphics g) {
        super.paintComponent(g);
        Rectangle clip = g.getClipBounds();
        if (clip == null) {
            clip = new Rectangle(0, 0, getWidth(), getHeight());
        }
        boolean highlightAndFocus = controller.getVideoController().getCellHighlightAndFocus();
        Times now = new Times(controller.getVideoController());
        int dim = Settings.get().getPastCellDim();
        int x = 0;
        for (SpreadsheetColumn column : columns) {
            if (!column.isVisible()) {
                continue;
            }
            int width = column.getWidth();
            int cellsHeight = geometry.cellsHeight(column.getColumn());
            g.setColor(SpreadsheetTheme.PALETTE.ground());
            g.fillRect(x, cellsHeight, width, Math.max(0, getHeight() - cellsHeight));
            g.setColor(SpreadsheetTheme.PALETTE.rule());
            g.fillRect(x + width - 1, 0, 1, getHeight());
            if (clip.intersects(x, 0, width, getHeight())) {
                paintCells(g, column, x, clip, now, dim, highlightAndFocus);
            }
            x += width;
        }
    }

    private void paintCells(final Graphics g, final SpreadsheetColumn column, final int x, final Rectangle clip,
                            final Times now, final int dim, final boolean highlightAndFocus) {
        List<Cell> cells = column.getColumn().getCellsTemporally();
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            Optional<SheetGeometry.Placed> placed = geometry.of(cell);
            if (placed.isEmpty()) {
                continue;
            }
            Rectangle bounds = new Rectangle(placed.get().bounds());
            bounds.translate(x, 0);
            if (!bounds.intersects(clip)) {
                continue;
            }
            CellLook.Time time = now.of(cell);
            shownTimes.put(cell, time);
            renderer.configure(cell, i + 1, placed.get().overlap(), time, dim, highlightAndFocus);
            rendererPane.paintComponent(g, renderer, this, bounds.x, bounds.y, bounds.width, bounds.height, true);
        }
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

    /** Returns the playhead's line's height in the sheet, or -1 if there is none. */
    int getPlayheadY() {
        return playheadY;
    }

    private void repaintLine(final int y) {
        if (y >= 0) {
            repaint(0, y - 2, getWidth(), 4);
        }
    }

    /** With a line over the cells, the editor repaints through this view, so that the line is drawn over it again. */
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

    // *************************************************************************
    // Scrollable
    // *************************************************************************

    /**
     * Returns the preferred size of the viewport for a view component.
     * In this instance it returns getPreferredSize
     *
     * @return the preferredSize of a <code>JViewport</code> whose view
     * is this <code>SpreadsheetView</code>
     */
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    /** The sheet fills a viewport wider than its columns, drawing the space after them. */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport viewport && viewport.getWidth() > getPreferredSize().width;
    }

    /** The sheet fills a viewport taller than its cells, drawing the ground below them. */
    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
    }

    /**
     * Temporary fix for cell-jumping behavior.
     * TODO: Make this work correctly.
     */
    @Override
    public void scrollRectToVisible(final Rectangle r) {
        // The layouts scroll a cell in focus into sight (SheetLayout.reorientView).
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
    public int getScrollableUnitIncrement(final Rectangle visibleRect,
                                          final int orientation,
                                          final int direction) {
        int currentPosition = orientation == SwingConstants.HORIZONTAL ? visibleRect.x : visibleRect.y;

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
    public int getScrollableBlockIncrement(final Rectangle visibleRect,
                                           final int orientation,
                                           final int direction) {
        if (orientation == SwingConstants.HORIZONTAL) {
            return visibleRect.width - MAX_UNIT_INCREMENT;
        } else {
            return visibleRect.height - MAX_UNIT_INCREMENT;
        }
    }

}
