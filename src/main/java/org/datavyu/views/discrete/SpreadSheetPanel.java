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
package org.datavyu.views.discrete;

import org.datavyu.controllers.project.MediaPlayers;
import org.datavyu.util.Resources;
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.event.component.FileDropEvent;
import org.datavyu.event.component.FileDropEventListener;
import org.datavyu.models.db.*;
import org.datavyu.util.Constants;
import org.datavyu.views.VideoController;
import org.datavyu.views.WindowPlacement;
import org.datavyu.views.NewVariableV;
import org.datavyu.views.discrete.layouts.SheetLayout;
import org.datavyu.views.discrete.layouts.SheetLayoutFactory;
import org.datavyu.views.discrete.layouts.SheetLayoutFactory.SheetLayoutType;
import org.datavyu.views.discrete.layouts.TimeAxis;
import org.datavyu.util.Settings;

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
 * Custom view of contents of the Datavyu database as a spread sheet
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

    /** New variable button to be added to the column header panel */
    private JButton newVariableButton = new JButton();

    /** New spacer variable */
    private JLabel newVariableSpacerButton = new JLabel();

    /** Hidden variables button to be added to the column header panel */
    private JButton hiddenVariablesButton;

    private JLabel hiddenVariablesSpacerLabel = new JLabel();

    /** Highlighted cell */
    private SpreadsheetCell highlightedCell;

    /** Last selected cell - used as an end point for continuous selections */
    private SpreadsheetCell lastSelectedCell;

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

        mainView = new SpreadsheetView();
        mainView.setLayout(new BoxLayout(mainView, BoxLayout.X_AXIS));

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

        
        // Set up the add new variable button
        newVariableButton.setBorder(BorderFactory.createMatteBorder(0, 0, 0, Constants.BORDER_SIZE, SpreadsheetTheme.PALETTE.rule()));
        newVariableButton.setName("newVarPlusButton");
        newVariableButton.setToolTipText(rMap.getString("add.tooltip"));
        
        newVariableButton.addActionListener(e -> openNewVarMenu());
        newVariableButton.setText(" + ");
        headerView.add(newVariableButton);


        // set the database and layout the columns
        if (projectController.getDataStore() == null) {
            projectController.setDataStore(new DatavyuDataStore());
        }
        setDatabase(projectController.getDataStore());
        newVariableSpacerButton.setText(" + ");
        newVariableSpacerButton.setForeground(newVariableSpacerButton.getBackground());
        mainView.add(newVariableSpacerButton);

        hiddenVariablesButton = makeHiddenVarsButton();
        updateHiddenVars();
        hiddenVariablesButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dropdown.show(e.getComponent(), e.getX(), e.getY());
            }
        });
        headerView.add(hiddenVariablesButton);
        hiddenVariablesSpacerLabel.setForeground(hiddenVariablesSpacerLabel.getBackground());
        mainView.add(hiddenVariablesSpacerLabel);


        //layout the columns
        buildColumns();

        setName(dataStore.getName());

        // Enable drag and drop support.
        setDropTarget(new DropTarget(this, new SSDropTarget()));
        fileDropListeners = new CopyOnWriteArrayList<>();

        lastSelectedCell = null;

        updateHiddenVars();
        headerView.add(hiddenVariablesButton);

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
        List<Variable> allVars = dataStore.getAllVariables();
        List<Variable> hiddensOnly = new ArrayList<>();
        dropdown.removeAll();
        for(final Variable v: allVars)
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
        
        
        hiddenVariablesButton.setText("  " +  hiddensOnly.size() + " Hidden Column");
        if(hiddensOnly.size() != 1) hiddenVariablesButton.setText(hiddenVariablesButton.getText() + "s");
        hiddenVariablesButton.setText(hiddenVariablesButton.getText() + "  "); //cheating: easier than resizing the button
        
        hiddenVariablesButton.setEnabled(hiddensOnly.size() != 0);

        hiddenVariablesSpacerLabel.setText(hiddenVariablesButton.getText());
    }

    /** Shows every cell's colours again, after the past-cell dimming changes. */
    public void refreshCellColours() {
        for (SpreadsheetColumn col : getColumns()) {
            for (SpreadsheetCell cell : col.getCells()) {
                cell.refreshColours();
            }
        }
    }

    public void redrawCells() {
        for (SpreadsheetColumn col : getColumns()) {
            for (SpreadsheetCell cell : col.getCells()) {
                cell.redrawValues();
                cell.updateSelectionDisplay();
            }
        }

    }

    /**
     * Gets the single instance project associated with the currently running with Datavyu.
     *
     * @return The single project in use with this instance of Datavyu
     */
    public ProjectController getProjectController() {
        // TODO: Do we want the project controller here or in Datavyu?
        return projectController;
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
        for (Variable v : getDataStore().getAllVariables()) {
            addColumn(v);
        }
    }

    /**
     * Add a column panel to the scroll panel.
     *
     * @param var The variable that this column represents.
     */
    private void addColumn(final Variable var) {
        // Remove previous instance of newVariableButton from the header.
        headerView.remove(newVariableButton);
        headerView.remove(hiddenVariablesButton);
        mainView.remove(newVariableSpacerButton);
        mainView.remove(hiddenVariablesSpacerLabel);

        // Create the spreadsheet column and register it.
        SpreadsheetColumn col = new SpreadsheetColumn(projectController, var, this, this, this);
        col.registerListeners();

        // add the datapanel to the scrollpane viewport
        mainView.addColumn(col);

        // add the headerpanel to the scrollpane headerviewport
        headerView.add(col);

        // add the new variable '+' button to the header.
        headerView.add(newVariableButton);
        updateHiddenVars();
        headerView.add(hiddenVariablesButton);
        mainView.add(newVariableSpacerButton);
        mainView.add(hiddenVariablesSpacerLabel);

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
            col.clear();

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
    private void removeColumn(final Variable var) {
        for (SpreadsheetColumn col : columns) {
            if (col.getVariable().equals(var)) {
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
        dataStore.clearVariableSelection();
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
    public void variableAdded(final Variable newVariable) {
        addColumn(newVariable);
        // Undo puts a deleted column back in its place, not always last.
        arrangeColumns();
        revalidate();
        repaint();
        variableVisible(newVariable);
    }

    @Override
    public void variableRemoved(final Variable deletedVariable) {
        removeColumn(deletedVariable);
        revalidate();
        repaint();
        variableHidden(deletedVariable);
    }

    @Override
    public void variableOrderChanged() {
        arrangeColumns();
    }

    @Override
    public void variableHidden(final Variable hiddenVariable) {
    }

    @Override
    public void variableVisible(final Variable visibleVariable) {
    }

    @Override
    public void variableNameChange(final Variable editedVariable) {
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
        if (!isShowing()) {
            return false;
        }
        SpreadsheetColumn selectedColumn = getSelectedColumn();
        // In quick-key mode, a letter or digit typed in a selected annotation column makes a cell holding it. A locked
        // column refuses the cell, and the key is still taken.
        if (e.getID() == KeyEvent.KEY_PRESSED && !e.isMetaDown() && !e.isControlDown()
                && projectController.isQuickKeyMode()
                && (Character.isAlphabetic(e.getKeyChar()) || Character.isDigit(e.getKeyChar()))
                && selectedColumn != null && !selectedColumn.getVariable().isMedia()) {
            projectController.cells().newCellTyped(selectedColumn.getVariable(), e.getKeyChar(),
                videoController.getCurrentTime());
            e.consume();
            return true;
        }
        return false;
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
        WindowPlacement.show(new NewVariableV((Frame) SwingUtilities.getWindowAncestor(this), false,
            projectController));
    }

    /**
     * Puts the columns in the spreadsheet's order, as the data store keeps it: after a column is moved, or put back
     * in its place by undo.
     */
    private void arrangeColumns() {
        List<SpreadsheetColumn> arranged = new ArrayList<>(columns);
        arranged.sort(Comparator.comparingInt(col -> dataStore.indexOf(col.getVariable())));
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
     * Returns the cells of the supplied column as ordered by the current
     * layout.
     *
     * @param col The column to fetch the cells from.
     * @return The cells in ordinal order if SheetLayoutType is Ordinal,
     * otherwise the cells will be in temporal alignment.
     */
    public List<SpreadsheetCell> getOrderedCells(SpreadsheetColumn col) {
        if (this.currentLayoutType == SheetLayoutType.Ordinal) {
            return col.getCells();
        } else {
            return col.getCellsTemporally();
        }
    }

    /**
     * Adds a series of cells as a continuous selection.
     *
     * @param cell The cell to use as the end point for the selection.
     */
    @Override
    public void addCellToContinousSelection(final SpreadsheetCell cell) {
        if (lastSelectedCell != null) {
            Cell c1 = lastSelectedCell.getCell();
            Variable v1 = dataStore.getVariable(c1);

            Cell c2 = cell.getCell();
            Variable v2 = dataStore.getVariable(c2);

            // We can only do continuous selections in a single column at
            // at the moment.
            if (v1.equals(v2)) {

                // Deselect the highlighted cell.
                if (highlightedCell != null) {
                    highlightedCell.getCell().setHighlighted(false);
                    highlightedCell.getCell().setSelected(true);
                    highlightedCell = null;
                }

                for (SpreadsheetColumn col : getColumns()) {

                    if (v1.equals(col.getVariable())) {

                        // Perform continuous selection.
                        boolean addToSelection = false;

                        for (SpreadsheetCell c : getOrderedCells(col)) {

                            if (!addToSelection) {
                                c.getCell().setSelected(false);
                            }

                            if (c.equals(cell) || c.equals(lastSelectedCell)) {
                                addToSelection = !addToSelection;

                                // We always include start and end cells.
                                c.getCell().setSelected(true);
                            }

                            if (addToSelection) {
                                c.getCell().setSelected(true);
                            }
                        }

                        break;
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
    public void addCellToSelection(final SpreadsheetCell cell) {
//        clearColumnSelection();

        if (highlightedCell != null) {
            highlightedCell.getCell().setSelected(true);
            highlightedCell.getCell().setHighlighted(false);
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
    public void setHighlightedCell(final SpreadsheetCell cell) {
        if (highlightedCell != null) {
//            highlightedCell.getCell().setSelected(false);
            highlightedCell.invalidate();
        }
        if(cell != null){
            highlightedCell = cell;
            lastSelectedCell = cell;
            highlightedCell.getCell().setHighlighted(true);
//          clearColumnSelection();
        }else{            
            highlightedCell.getCell().setHighlighted(false);            
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

    public void reorientView(SpreadsheetCell cell) {
        ((SheetLayout) scrollPane.getLayout()).reorientView(cell);
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

            SpreadsheetCell sc = selectedColumn.getDataPanel().getSelectedCell();

            clearCellSelection();
            clearColumnSelection();
            requestFocus();

            SpreadsheetColumn newColumn = visibleColumns.get(newIndex);
            SpreadsheetCell newCell;
            if(!newColumn.getCells().isEmpty()) {
                if (sc != null) {
                    if (currentLayoutType == SheetLayoutType.WeakTemporal) {
                        newCell = newColumn.getNearestCellTemporally(sc);
                    } else {
                        int ord = selectedColumn.getCellsTemporally().indexOf(sc);
                        if (newColumn.getCellsTemporally().size() > ord) {
                            newCell = newColumn.getCellsTemporally().get(ord);
                        } else {
                            newCell = newColumn.getCellsTemporally().get(newColumn.getCellsTemporally().size() - 1);
                        }
                    }
                    sc.getCell().setHighlighted(false);
                } else {
                    newCell = newColumn.getCellTemporally(0);
                }
                selectedColumn.setSelected(false);

                newColumn.setSelected(true);

                newCell.getCell().setHighlighted(true);
                newCell.getCell().setSelected(true);

                newColumn.requestFocus();
            }else{
                if(sc != null) sc.getCell().setHighlighted(false);

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
