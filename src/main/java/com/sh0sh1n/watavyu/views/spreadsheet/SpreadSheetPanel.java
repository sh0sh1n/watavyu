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

import com.sh0sh1n.watavyu.controllers.CellNavigation;
import com.sh0sh1n.watavyu.controllers.MediaPlayers;
import com.sh0sh1n.watavyu.util.Resources;
import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.*;
import com.sh0sh1n.watavyu.util.Constants;
import com.sh0sh1n.watavyu.views.Shortcuts;
import com.sh0sh1n.watavyu.views.VideoController;
import com.sh0sh1n.watavyu.views.WindowPlacement;
import com.sh0sh1n.watavyu.views.NewColumnV;
import com.sh0sh1n.watavyu.views.spreadsheet.layouts.SheetLayout;
import com.sh0sh1n.watavyu.views.spreadsheet.layouts.SheetLayoutFactory;
import com.sh0sh1n.watavyu.views.spreadsheet.layouts.SheetLayoutFactory.SheetLayoutType;
import com.sh0sh1n.watavyu.views.spreadsheet.layouts.TimeAxis;
import com.sh0sh1n.watavyu.util.Settings;

import javax.swing.*;
import javax.swing.Box.Filler;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * A spreadsheet tab: its columns, the controls drawer and the playhead.
 */
public final class SpreadSheetPanel extends JPanel implements DataStoreListener, CellSelectionListener,
        ColumnSelectionListener, ColumnVisibilityListener, KeyEventDispatcher {


    /** List containing listeners interested in file drop events */
    private final transient List<FileDropEventListener> fileDropListeners;

    /** Current project controller */
    private transient ProjectController projectController;

    /** Video controller */
    private transient VideoController videoController;

    /** Plays this spreadsheet's media columns; set with the video controller. */
    private transient MediaPlayers mediaPlayers;

    /** The video controller's buttons, in a drawer at the left; set with the video controller. */
    private JPanel controlsDrawer;

    /** The playhead beside the columns; set with the video controller. */
    private PlayheadCarriage playhead;

    /** Scrollable view inserted into the JScrollPane */
    private SpreadsheetView mainView;

    /** View showing the Column titles */
    private JPanel headerView;

    /** The Database being viewed */
    private transient DataStore dataStore;

    /** Vector of the Spreadsheet columns added to the Spreadsheet */
    private List<SpreadsheetColumn> columns;

    /** Reference to the scrollPane */
    private JScrollPane scrollPane;

    /** New column button to be added to the column header panel */
    private JButton newColumnButton = new JButton();

    /** Hidden columns button to be added to the column header panel */
    private JButton hiddenColumnsButton;

    /** Highlighted cell */
    private transient Cell highlightedCell;

    /** Last selected cell - used as an end point for continuous selections */
    private transient Cell lastSelectedCell;

    /** Current layout */
    private SheetLayoutType currentLayoutType;

    /** Drop down menu for hidden columns */
    private JPopupMenu dropdown = new JPopupMenu();

    /**
     * Shows a spreadsheet, with its controls drawer and playhead.
     *
     * @param projectController the spreadsheet, whose video controller is already set
     * @param owner             the window that owns its media windows; null for none
     */
    public SpreadSheetPanel(final ProjectController projectController, final Frame owner) {
        this.projectController = projectController;
        videoController = Objects.requireNonNull(projectController.getVideoController(),
            "A spreadsheet's video controller is set before its view is made");
        projectController.setSpreadSheetPanel(this);
        setName(this.getClass().getSimpleName());
        setLayout(new BorderLayout());

        mainView = new SpreadsheetView(projectController);

        headerView = new JPanel();
        headerView.setLayout(new BoxLayout(headerView, BoxLayout.X_AXIS));
        headerView.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, SpreadsheetTheme.PALETTE.rule()));
        headerView.setName("headerView");

        columns = new ArrayList<>();
        scrollPane = new JScrollPane();
        scrollPane.setDoubleBuffered(true);
        this.add(scrollPane, BorderLayout.CENTER);
        add(scrollPane);
        scrollPane.setViewportView(mainView);
        scrollPane.setColumnHeaderView(headerView);


        // Default layout is ordinal.
        setLayoutType(SheetLayoutFactory.DEFAULT_LAYOUT);

        // set strut for headerView - necessary while there are no col headers
        Dimension dimension = new Dimension(0, SpreadsheetColumn.DEFAULT_HEADER_HEIGHT);

        Filler headerStrut = new Filler(dimension, dimension, dimension);
        headerView.add(headerStrut);

        // Set a border for the top right corner
        JPanel rightCorner = new JPanel();
        rightCorner.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 0, SpreadsheetTheme.PALETTE.rule()));
        scrollPane.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, rightCorner);

        Resources rMap = Resources.of(SpreadSheetPanel.class);

        
        // Set up the add new column button
        newColumnButton.setBorder(BorderFactory.createMatteBorder(0, 0, 0, Constants.BORDER_SIZE, SpreadsheetTheme.PALETTE.rule()));
        newColumnButton.setName("newVarPlusButton");
        newColumnButton.setToolTipText(rMap.getString("add.tooltip"));
        
        newColumnButton.addActionListener(e -> openNewVarMenu());
        newColumnButton.setText(" + ");
        headerView.add(newColumnButton);


        // set the database and layout the columns
        if (projectController.getDataStore() == null) {
            projectController.setDataStore(new DefaultDataStore());
        }
        setDatabase(projectController.getDataStore());

        hiddenColumnsButton = makeHiddenVarsButton();
        // The sheet is as wide as the header, whose buttons follow the columns.
        mainView.setTrailingWidth(() -> newColumnButton.getPreferredSize().width
                + hiddenColumnsButton.getPreferredSize().width);
        updateHiddenVars();
        hiddenColumnsButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dropdown.show(e.getComponent(), e.getX(), e.getY());
            }
        });
        headerView.add(hiddenColumnsButton);


        //layout the columns
        buildColumns();

        setName(dataStore.getName());

        // Enable drag and drop support.
        setDropTarget(new DropTarget(this, new SSDropTarget()));
        fileDropListeners = new CopyOnWriteArrayList<>();

        lastSelectedCell = null;

        updateHiddenVars();
        headerView.add(hiddenColumnsButton);

        attachVideoController(owner);
        this.projectController.getDataStore().markAsUnchanged();
    }
    
    private JButton makeHiddenVarsButton()
    {
        JButton res = new JButton();
        res.setBorder(BorderFactory.createMatteBorder(0, 0, 0, Constants.BORDER_SIZE, SpreadsheetTheme.PALETTE.rule()));
        res.setName("hiddenVarsButton");       
        return res;
    }

    private void updateHiddenVars() {
        List<Column> allVars = dataStore.getAllColumns();
        List<Column> hiddensOnly = new ArrayList<>();
        dropdown.removeAll();
        for(final Column v: allVars)
        {
            if(v.isHidden()) 
            {
                hiddensOnly.add(v);
                dropdown.add(new JMenuItem(
                        new AbstractAction(v.getName()) {
                            @Override
                            public void actionPerformed(ActionEvent e)
                            {
                                projectController.columns().show(v);
                            }
                        }));
            }
        }
        
        
        hiddenColumnsButton.setText("  " +  hiddensOnly.size() + " Hidden Column");
        if(hiddensOnly.size() != 1) hiddenColumnsButton.setText(hiddenColumnsButton.getText() + "s");
        hiddenColumnsButton.setText(hiddenColumnsButton.getText() + "  "); //cheating: easier than resizing the button
        
        hiddenColumnsButton.setEnabled(hiddensOnly.size() != 0);
    }

    /** Shows every cell's colours again, after the past-cell dimming changes. */
    public void refreshCellColours() {
        mainView.refreshColours();
    }

    /** Lays out and draws every cell again, as after undo, a change of font or of highlighting. */
    public void redrawCells() {
        mainView.redrawCells();
    }

    /**
     * Returns the spreadsheet this tab shows.
     */
    public ProjectController getProjectController() {
        return projectController;
    }

    /** Returns the sheet that draws the cells. */
    SpreadsheetView getSheet() {
        return mainView;
    }

    /** Returns what plays this spreadsheet's media columns, or null before the video controller is set. */
    public MediaPlayers getMediaPlayers() {
        return mediaPlayers;
    }

    public VideoController getVideoController() {
        return videoController;
    }

    /** Plays the spreadsheet's media columns, and puts the controls drawer and playhead beside the columns. */
    private void attachVideoController(final Frame owner) {
        mediaPlayers = new MediaPlayers(projectController.getDataStore(), videoController, owner);
        videoController.setMediaPlayers(mediaPlayers);

        playhead = new PlayheadCarriage(scrollPane.getViewport(), this::timeAxis, videoController::setCurrentTime,
                mainView::setPlayheadY, () -> Settings.get().isPlayheadAcrossColumns());
        layoutFollowedByPlayhead();
        videoController.setPlayhead(playhead);

        // Above the playhead, a blank the height of the column headers, so the strip starts level with the cells.
        JComponent headerBlank = new JComponent() {
            @Override
            public Dimension getPreferredSize() {
                JViewport columnHeader = scrollPane.getColumnHeader();
                return new Dimension(PlayheadCarriage.WIDTH, scrollPane.getInsets().top
                        + (columnHeader == null ? 0 : columnHeader.getPreferredSize().height));
            }
        };
        headerView.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent e) {
                headerBlank.revalidate();
            }
        });
        JPanel playheadColumn = new JPanel(new BorderLayout());
        playheadColumn.add(headerBlank, BorderLayout.NORTH);
        playheadColumn.add(playhead, BorderLayout.CENTER);

        controlsDrawer = new JPanel(new BorderLayout());
        controlsDrawer.setName("controlsDrawer");
        controlsDrawer.setBackground(SpreadsheetTheme.PALETTE.chrome());
        controlsDrawer.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, SpreadsheetTheme.PALETTE.rule()));
        controlsDrawer.add(videoController.getControls(), BorderLayout.NORTH);
        controlsDrawer.setVisible(Settings.get().isShowControls());

        JPanel west = new JPanel(new BorderLayout());
        west.add(controlsDrawer, BorderLayout.WEST);
        west.add(playheadColumn, BorderLayout.EAST);
        add(west, BorderLayout.WEST);
        revalidate();
    }

    /** @return where times fall down the sheet, or empty when it is laid out by order */
    private Optional<TimeAxis> timeAxis() {
        return ((SheetLayout) scrollPane.getLayout()).timeAxis();
    }

    /** Has the playhead follow the cells each time the sheet is laid out. */
    private void layoutFollowedByPlayhead() {
        if (playhead != null) {
            ((SheetLayout) scrollPane.getLayout()).setAfterLayout(playhead::refresh);
            playhead.refresh();
        }
    }

    /** Returns the playhead beside the columns, or null before the video controller is set. */
    public PlayheadCarriage getPlayhead() {
        return playhead;
    }

    /**
     * Shows or hides the controls drawer.
     *
     * @param show whether it shows
     */
    public void setControlsShown(final boolean show) {
        if (controlsDrawer != null) {
            controlsDrawer.setVisible(show);
            revalidate();
        }
    }

    /**
     * Closes the spreadsheet once its tab is gone and its work saved or knowingly discarded: it stops listening for
     * keys, its columns stop following the clock, its playback, players and media windows close, and its autosave is
     * deleted.
     */
    public void close() {
        deregisterListeners();
        for (SpreadsheetColumn column : columns) {
            column.deregisterListeners();
        }
        dataStore.removeListener(this);
        mainView.close();
        videoController.close();
        projectController.close();
    }

    /**
     * Registers this column data panel with everything that needs to notify
     * this class of events.
     */
    public void registerListeners() {
        KeyboardFocusManager m = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        m.addKeyEventDispatcher(this);
    }

    /**
     * Deregisters this column data panel with everything that is currently
     * notiying it of events.
     */
    public void deregisterListeners() {
        KeyboardFocusManager m = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        m.removeKeyEventDispatcher(this);
    }

    /**
     * Populate from the database.
     */
    private void buildColumns() {
        for (Column v : getDataStore().getAllColumns()) {
            addColumn(v);
        }
    }

    /**
     * Add a column panel to the scroll panel.
     *
     * @param var The column that this column represents.
     */
    private void addColumn(final Column var) {
        // Remove previous instance of newColumnButton from the header.
        headerView.remove(newColumnButton);
        headerView.remove(hiddenColumnsButton);

        // Create the spreadsheet column and register it.
        SpreadsheetColumn col = new SpreadsheetColumn(projectController, var, mainView, this, this, this);
        col.registerListeners();

        // add the datapanel to the scrollpane viewport
        mainView.addColumn(col);

        // add the headerpanel to the scrollpane headerviewport
        headerView.add(col);

        // add the new column '+' button to the header.
        headerView.add(newColumnButton);
        updateHiddenVars();
        headerView.add(hiddenColumnsButton);

        // and add it to our maintained ref collection
        columns.add(col);
    }

    /**
     * Remove all the columns from the spreadsheet panel.
     */
    @Override
    public void removeAll() {
        for (SpreadsheetColumn col : columns) {
            col.deregisterListeners();
            mainView.removeColumn(col);
            headerView.remove(col);
        }

        columns.clear();
    }

    /**
     * Remove a column panel from the scroll panel viewport.
     *
     * @param var the column whose panel goes
     */
    private void removeColumn(final Column var) {
        for (SpreadsheetColumn col : columns) {
            if (col.getColumn().equals(var)) {
                col.deregisterListeners();
                mainView.removeColumn(col);
                headerView.remove(col);
                columns.remove(col);

                break;
            }
        }
    }

    /**
     * Returns the vector of Spreadsheet columns.
     * Need for UISpec4J testing.
     */
    public List<SpreadsheetColumn> getColumns() {
        return columns;
    }

    /**
     * Returns the vector of Spreadsheet columns.
     * Need for UISpec4J testing.
     */
    public List<SpreadsheetColumn> getVisibleColumns() {
        List<SpreadsheetColumn> visibleColumns = new ArrayList<>();
        for (SpreadsheetColumn column : columns){
            if(column.isVisible()){ visibleColumns.add(column); }
        }
        return visibleColumns;
    }

    /**
     * Deselect all selected items in the Spreadsheet.
     */
    public void deselectAll() {
        dataStore.clearCellSelection();
        dataStore.clearColumnSelection();
    }

    /**
     * Set Database.
     *
     * @param db Database to set
     */
    public void setDatabase(final DataStore db) {
        // check if we need to deregister any existing listeners.
        if (dataStore != null && !dataStore.equals(db)) {
            dataStore.removeListener(this);
        }

        // set the database
        dataStore = db;
        dataStore.addListener(this);

        // setName to remember screen locations
        setName(db.getName());

        db.deselectAll();
    }

    /**
     * Returns the data store this spreadsheet shows.
     */
    public DataStore getDataStore() {
        return dataStore;
    }

    @Override
    public void columnAdded(final Column newColumn) {
        addColumn(newColumn);
        // Undo puts a deleted column back in its place, not always last.
        arrangeColumns();
        revalidate();
        repaint();
        columnVisible(newColumn);
    }

    @Override
    public void columnRemoved(final Column deletedColumn) {
        removeColumn(deletedColumn);
        revalidate();
        repaint();
        columnHidden(deletedColumn);
    }

    @Override
    public void columnOrderChanged() {
        arrangeColumns();
    }

    @Override
    public void columnHidden(final Column hiddenColumn) {
    }

    @Override
    public void columnVisible(final Column visibleColumn) {
    }

    @Override
    public void columnNameChange(final Column editedColumn) {
        // Do nothing.
    }

    /**
     * Dispatches the key event to the desired components.
     *
     * @param e The key event to dispatch.
     * @return true if the event has been consumed by this dispatch, false
     * otherwise
     */
    @Override
    public boolean dispatchKeyEvent(final KeyEvent e) {
        // Every open spreadsheet listens; only the one on screen, in the selected tab, takes keys.
        return isShowing() && takeKey(e);
    }

    /**
     * Takes a key for the spreadsheet on screen: a quick key, ahead of the keys that change mode, so in quick-key mode
     * {@code i} makes a cell as other letters do.
     *
     * @param e a key event, from any window
     * @return whether the spreadsheet took it
     */
    boolean takeKey(final KeyEvent e) {
        if (e.getID() == KeyEvent.KEY_PRESSED && (e.getModifiersEx() & Shortcuts.MODIFIERS) == 0
                && (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_DOWN) && mainView.hasKeyboard()
                && !SpreadsheetView.menuOpen()) {
            // ↑ and ↓, while the keyboard is in the sheet, are ⌃K and ⌃J, and at a column's end do nothing as they do.
            moveVertically(e.getKeyCode() == KeyEvent.VK_DOWN);
            e.consume();
            return true;
        }
        SpreadsheetColumn selectedColumn = getSelectedColumn();
        // In quick-key mode, a letter or digit typed in a selected annotation column makes a cell holding it. A locked
        // column refuses the cell, and the key is still taken.
        if (e.getID() == KeyEvent.KEY_PRESSED && !e.isMetaDown() && !e.isControlDown()
                && projectController.isQuickKeyMode()
                && (Character.isAlphabetic(e.getKeyChar()) || Character.isDigit(e.getKeyChar()))
                && selectedColumn != null && !selectedColumn.getColumn().isMedia()) {
            Cell made = projectController.cells().newCellTyped(selectedColumn.getColumn(), e.getKeyChar(),
                videoController.getCurrentTime());
            if (made != null) {
                // The keyboard stays where it is, and the new cell is shown.
                revalidate();
                validate();
                reorientView(made);
            }
            e.consume();
            return true;
        }
        return mainView.modeKey(e);
    }

    /**
     * Next and Previous Cell (⌃J, ⌃K, and ↓, ↑ in the sheet): goes down or up the selected column, from its selected
     * cell or with none from the playhead ({@link CellNavigation#vertical}), in the mode the keyboard is in.
     *
     * @param down whether going down, to later cells
     * @return whether there was a cell to go to
     */
    public boolean moveVertically(final boolean down) {
        Cell current = currentCell();
        SpreadsheetColumn column = current == null ? currentColumn() : columnOf(current);
        if (column == null) {
            return false;
        }
        Optional<Cell> next = CellNavigation.vertical(column.getCellsTemporally(), current, down,
            videoController.getCurrentTime());
        next.ifPresent(column::goTo);
        return next.isPresent();
    }

    /**
     * Returns the cell navigation goes from: the one being typed in, or else the highlighted cell, the one selected
     * last, or else the first selected cell of the selected column; null with no cell selected.
     */
    private Cell currentCell() {
        if (mainView.inInsertMode()) {
            Optional<Cell> editing = mainView.getEditor().map(SpreadsheetCell::getCell).filter(Cell::isSelected);
            if (editing.isPresent()) {
                return editing.get();
            }
        }
        if (highlightedCell != null && highlightedCell.isSelected() && columnOf(highlightedCell) != null) {
            return highlightedCell;
        }
        SpreadsheetColumn column = currentColumn();
        return column == null ? null : column.getSelectedCell();
    }

    /** Returns the shown column view of a cell, or null if its column is hidden or gone. */
    private SpreadsheetColumn columnOf(final Cell cell) {
        return mainView.shownColumnOf(cell.getColumn()).orElse(null);
    }

    /**
     * Returns the column navigation goes from with no cell selected: the column selected last, the one New Cell from
     * the menu goes to, if it is selected and shown, or else the first selected.
     */
    private SpreadsheetColumn currentColumn() {
        Column last = projectController.getLastCreatedColumn();
        if (last != null && last.isSelected()) {
            Optional<SpreadsheetColumn> shown = mainView.shownColumnOf(last);
            if (shown.isPresent()) {
                return shown.get();
            }
        }
        return getSelectedColumn();
    }

    /**
     * Column Left and Right (⌃H, ⌃L): goes to the shown annotation column beside the selected one
     * ({@link CellNavigation#beside}), landing on the cell whose onset is nearest the selected cell's, or with none the
     * playhead's time, or selecting the column alone if it has no cells; in the mode the keyboard is in.
     *
     * @param right whether going right
     */
    public void moveHorizontally(final boolean right) {
        Cell current = currentCell();
        SpreadsheetColumn from = current == null ? currentColumn() : columnOf(current);
        List<SpreadsheetColumn> shown = getVisibleColumns();
        List<Column> models = shown.stream().map(SpreadsheetColumn::getColumn).toList();
        Optional<Column> target = CellNavigation.beside(models, from == null ? null : from.getColumn(), right);
        if (target.isEmpty()) {
            return;
        }
        long time = current == null ? videoController.getCurrentTime() : current.getOnset();
        SpreadsheetColumn to = shown.get(models.indexOf(target.get()));
        CellNavigation.nearest(to.getCellsTemporally(), time).ifPresentOrElse(to::goTo, to::goToColumn);
    }

    /** Returns how the cells are laid out: in time (temporal) or one after another (ordinal). */
    public SheetLayoutType getLayoutType() {
        return currentLayoutType;
    }

    /**
     * Set the layout type for the spreadsheet.
     *
     * @param type SheetLayoutType to set.
     */
    public void setLayoutType(final SheetLayoutType type) {
        this.currentLayoutType = type;
        this.scrollPane.setLayout(SheetLayoutFactory.createLayout(type));
        layoutFollowedByPlayhead();

        revalidate();
    }

    /**
     * Method to invoke when the user clicks on the "+" icon in the spreadsheet
     * header.
     */
    public void openNewVarMenu() {
        WindowPlacement.show(new NewColumnV((Frame) SwingUtilities.getWindowAncestor(this), false,
            projectController));
    }

    /**
     * Puts the columns in the spreadsheet's order, as the data store keeps it: after a column is moved, or put back
     * in its place by undo.
     */
    private void arrangeColumns() {
        List<SpreadsheetColumn> arranged = new ArrayList<>(columns);
        arranged.sort(Comparator.comparingInt(col -> dataStore.indexOf(col.getColumn())));
        if (arranged.equals(columns)) {
            return;
        }
        columns.clear();
        columns.addAll(arranged);
        for (int i = 0; i < arranged.size(); i++) {
            // The header holds a strut before the columns.
            headerView.remove(arranged.get(i));
            headerView.add(arranged.get(i), i + 1);
        }
        mainView.arrangeColumns(arranged);
        revalidate();
        repaint();
    }

    /**
     * Adds a series of cells as a continuous selection: the cells in time order from the last one selected to this
     * one, in the same column.
     *
     * @param cell The cell to use as the end point for the selection.
     */
    @Override
    public void addCellToContinousSelection(final Cell cell) {
        if (lastSelectedCell != null) {
            Column v1 = lastSelectedCell.getColumn();

            // We can only do continuous selections in a single column at
            // at the moment.
            if (v1.equals(cell.getColumn())) {

                // Deselect the highlighted cell.
                if (highlightedCell != null) {
                    highlightedCell.setHighlighted(false);
                    highlightedCell.setSelected(true);
                    highlightedCell = null;
                }

                // Perform continuous selection.
                boolean addToSelection = false;
                for (Cell c : v1.getCellsTemporally()) {
                    if (!addToSelection) {
                        c.setSelected(false);
                    }

                    if (c.equals(cell) || c.equals(lastSelectedCell)) {
                        addToSelection = !addToSelection;

                        // We always include start and end cells.
                        c.setSelected(true);
                    }

                    if (addToSelection) {
                        c.setSelected(true);
                    }
                }
            }
        } else {
            lastSelectedCell = cell;
        }
    }

    /**
     * Add a cell to the current selection.
     *
     * @param cell The cell to add to the selection.
     */
    @Override
    public void addCellToSelection(final Cell cell) {
        if (highlightedCell != null) {
            highlightedCell.setSelected(true);
            highlightedCell.setHighlighted(false);
            highlightedCell = null;
        }

        lastSelectedCell = cell;
    }

    /**
     * Set the currently highlighted cell.
     *
     * @param cell The cell to highlight.
     */
    @Override
    public void setHighlightedCell(final Cell cell) {
        if (cell != null) {
            highlightedCell = cell;
            lastSelectedCell = cell;
            highlightedCell.setHighlighted(true);
        } else if (highlightedCell != null) {
            highlightedCell.setHighlighted(false);
        }
    }

    @Override
    public void removeCellFromSelection(final Cell cell) {
        cell.setSelected(false);
        if (cell.equals(highlightedCell)) {
            highlightedCell = null;
        }
        if (cell.equals(lastSelectedCell)) {
            lastSelectedCell = null;
        }
        if (cell.equals(projectController.getLastSelectedCell())) {
            projectController.setLastSelectedCell(null);
        }
    }

    /**
     * Goes to insert mode in a cell, its editor taking the focus, leaving the selection as it is, as after a command
     * makes the cell.
     *
     * @param cell a cell of a shown column
     */
    public void editCell(final Cell cell) {
        if (columnOf(cell) != null) {
            mainView.focusEditor(mainView.edit(cell));
        }
    }

    /**
     * Clears the current cell selection.
     */
    @Override
    public void clearCellSelection() {
        highlightedCell = null;
        lastSelectedCell = null;

        dataStore.clearCellSelection();
    }

    /**
     * Add a column to the current selection.
     *
     * @param column The column to add to the current selection.
     */
    @Override
    public void addColumnToSelection(final SpreadsheetColumn column) {
        clearCellSelection();
        column.requestFocus();
    }

    /**
     * Clears the current column selection.
     */
    @Override
    public void clearColumnSelection() {
        for (SpreadsheetColumn col : getColumns()) {
            col.setSelected(false);
        }
    }
    
    @Override
    public void columnVisibilityChanged() {
        updateHiddenVars();
        revalidate();
        repaint();
    }

    /**
     * Scrolls a cell into sight, as when it takes the focus.
     *
     * @param cell the cell
     */
    public void reorientView(final Cell cell) {
        mainView.boundsOf(cell).ifPresent(((SheetLayout) scrollPane.getLayout())::reorientView);
    }

    public void reorientView(SpreadsheetColumn column) {
        ((SheetLayout) scrollPane.getLayout()).reorientView(column);
    }

    /**
     * Add a listener interested in file drop events.
     *
     * @param listener The listener to add.
     */
    public void addFileDropEventListener(final FileDropEventListener listener) {

        synchronized (this) {
            fileDropListeners.add(listener);
        }
    }

    /**
     * Notifies all registered listeners about the file drag and drop event.
     *
     * @param files The files that were dropped onto the spreadsheet.
     */
    private void notifyFileDropEventListeners(final List<File> files) {
        final FileDropEvent event = new FileDropEvent(this, files);

        synchronized (this) {

            for (FileDropEventListener listener : fileDropListeners) {
                listener.filesDropped(event);
            }
        }
    }

    public SpreadsheetColumn getSelectedColumn() {
        SpreadsheetColumn col = null;
        for(SpreadsheetColumn c : this.getVisibleColumns()) {
            if(c.isSelected()) {
                col = c;
                break;
            }
        }
        return col;
    }

    /**
     * Select Column to Left or Right (⌘←, ⌘→): selects the shown column beside the selected one, coming round from
     * the end, or with none selected, the last or the first.
     *
     * @param direction -1 for the column to the left, 1 for the one to the right
     */
    public void selectNextColumn(final int direction) {
        List<SpreadsheetColumn> visibleColumns = getVisibleColumns();
        if (visibleColumns.isEmpty()) {
            return;
        }
        SpreadsheetColumn selectedColumn = getSelectedColumn();
        if (selectedColumn != null) {
            selectColumn(selectedColumn, direction);
            return;
        }
        // With none selected, the last or the first is selected, and the cells are left as they are.
        SpreadsheetColumn column = visibleColumns.get(direction < 0 ? visibleColumns.size() - 1 : 0);
        column.setSelected(true);
        column.requestFocus();
    }

    public void selectColumn(final SpreadsheetColumn selectedColumn, final int shift) {
        // Find currently selected cell, if there is one
        List<SpreadsheetColumn> visibleColumns = getVisibleColumns();
        int vcIndex = visibleColumns.indexOf(selectedColumn);
        int newIndex = Math.floorMod(vcIndex+shift, visibleColumns.size()); // wrap around visible columns
        if(0 <= newIndex
                && newIndex < visibleColumns.size()) {

            Cell sc = selectedColumn.getSelectedCell();

            clearCellSelection();
            clearColumnSelection();
            requestFocus();

            SpreadsheetColumn newColumn = visibleColumns.get(newIndex);
            List<Cell> newCells = newColumn.getCellsTemporally();
            Cell newCell;
            if (!newCells.isEmpty()) {
                if (sc != null) {
                    if (currentLayoutType == SheetLayoutType.WeakTemporal) {
                        newCell = newColumn.getNearestCellTemporally(sc);
                    } else {
                        int ord = selectedColumn.getCellsTemporally().indexOf(sc);
                        newCell = newCells.get(Math.min(ord, newCells.size() - 1));
                    }
                    sc.setHighlighted(false);
                } else {
                    newCell = newCells.get(0);
                }
                selectedColumn.setSelected(false);

                newColumn.setSelected(true);

                newCell.setHighlighted(true);
                newCell.setSelected(true);

                newColumn.requestFocus();
            } else {
                if (sc != null) {
                    sc.setHighlighted(false);
                }

                selectedColumn.setSelected(false);
                newColumn.setSelected(true);
                newColumn.requestFocus();
            }
        }

    }

    /**
     * Inner class for handling file drag and drop.
     */
    private class SSDropTarget extends DropTargetAdapter {

        /**
         * Creates a new drag and drop handler.
         */
        SSDropTarget() {
            super();
        }

        /**
         * The event handler for when a file is dropped onto the interface.
         *
         * @param dtde The event to handle.
         */
        @Override
        public void drop(final DropTargetDropEvent dtde) {
            Transferable tr = dtde.getTransferable();
            DataFlavor[] flavors = tr.getTransferDataFlavors();

            for (DataFlavor flavor : flavors) {
                if (flavor.isFlavorJavaFileListType()) {
                    dtde.acceptDrop(DnDConstants.ACTION_REFERENCE);

                    List<File> fileList = new ArrayList<>();
                    try {
                        // A Java file list holds only files.
                        for (Object file : (List<?>) tr.getTransferData(flavor)) {
                            fileList.add((File) file);
                        }
                        dtde.dropComplete(true);
                    } catch (UnsupportedFlavorException | IOException e) {
                        dtde.rejectDrop();
                    }

                    notifyFileDropEventListeners(fileList);

                    return;
                }
            }

            dtde.rejectDrop();
        }
    }

}
