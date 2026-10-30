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

import org.datavyu.controllers.project.MediaPlayers;
import org.datavyu.util.Resources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.Datavyu;
import org.datavyu.controllers.CreateNewCellController;
import org.datavyu.controllers.NewVariableController;
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.event.component.FileDropEvent;
import org.datavyu.event.component.FileDropEventListener;
import org.datavyu.models.db.*;
import org.datavyu.undoableedits.AddCellEdit;
import org.datavyu.util.ArrayDirection;
import org.datavyu.util.Constants;
import org.datavyu.views.DataviewProgressBar;
import org.datavyu.views.VideoController;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.undo.UndoableEdit;


/**
 * Custom view of contents of the Datavyu database as a spread sheet
 */
public final class SpreadSheetPanel extends JPanel implements DataStoreListener, CellSelectionListener,
        ColumnSelectionListener, ColumnVisibilityListener, KeyEventDispatcher {

    /** The logger for this class */
    private static Logger logger = LogManager.getLogger(SpreadSheetPanel.class);

    /** List containing listeners interested in file drop events */
    private final transient CopyOnWriteArrayList<FileDropEventListener> fileDropListeners;

    /** Current project controller */
    private ProjectController projectController;

    /** Video controller */
    private transient VideoController videoController;

    /** Plays this spreadsheet's media columns; set with the video controller. */
    private MediaPlayers mediaPlayers;

    /** The video controller's buttons, in a drawer at the left; set with the video controller. */
    private JPanel controlsDrawer;

    /** The playhead beside the columns; set with the video controller. */
    private PlayheadCarriage playhead;

    /** Scrollable view inserted into the JScrollPane */
    private SpreadsheetView mainView;

    /** View showing the Column titles */
    private JPanel headerView;

    /** The Database being viewed */
    private DataStore dataStore;

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

    public SpreadSheetPanel(final ProjectController projectController, DataviewProgressBar progressBar) {
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
        this.projectController = projectController;
        buildColumns(progressBar);
        projectController.setSpreadSheetPanel(this);

        setName(dataStore.getName());

        // Enable drag and drop support.
        setDropTarget(new DropTarget(this, new SSDropTarget()));
        fileDropListeners = new CopyOnWriteArrayList<FileDropEventListener>();

        lastSelectedCell = null;

        updateHiddenVars();
        headerView.add(hiddenVariablesButton);

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
        List<Variable> hiddensOnly = new ArrayList<Variable>();
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
                                v.setHidden(false);
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
                cell.valueChange(cell.getCell().getCellValue());
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

    /** @return what plays this spreadsheet's media columns, or null before the video controller is set */
    public MediaPlayers getMediaPlayers() {
        return mediaPlayers;
    }

    public VideoController getVideoController() {
        return videoController;
    }

    public void setVideoController(VideoController videoController) {
        this.videoController = videoController;
        mediaPlayers = new MediaPlayers(projectController.getDataStore(), videoController);
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

    /** @return the playhead beside the columns, or null before the video controller is set */
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
    private void buildColumns(DataviewProgressBar progressBar) {
        List<Variable> vlist = getDataStore().getAllVariables();
        int pb_increment = 0;
        int i = 0;

        if (progressBar != null) {
            pb_increment = (100 - progressBar.getProgress()) / vlist.size();
        }

        for (Variable v : vlist) {
            if (progressBar != null) {
                progressBar.setProgress(progressBar.getProgress() + pb_increment, "Adding column " + ++i + " of " + vlist.size());
            }
            addColumn(getDataStore(), v);
        }
    }

    /**
     * Add a column panel to the scroll panel.
     *
     * @param db  database.
     * @param var The variable that this column represents.
     */
    private void addColumn(final DataStore db, final Variable var) {
        // Remove previous instance of newVariableButton from the header.
        headerView.remove(newVariableButton);
        headerView.remove(hiddenVariablesButton);
        mainView.remove(newVariableSpacerButton);
        mainView.remove(hiddenVariablesSpacerLabel);

        // Create the spreadsheet column and register it.
        SpreadsheetColumn col = new SpreadsheetColumn(db, var, this, this, this);
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

        var.setOrderIndex(columns.size() - 1);

//        updateColumnIndex();
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
     * @param var
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
     * @return the vector of Spreadsheet columns.
         * Need for UISpec4J testing
     */
    public List<SpreadsheetColumn> getColumns() {
        return columns;
    }

    /**
     * @return the vector of Spreadsheet columns.
     * Need for UISpec4J testing
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
        if ((dataStore != null) && (dataStore != db)) {
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
     * @return Database this spreadsheet displays
     */
    public DataStore getDataStore() {
        return (dataStore);
    }

    @Override
    public void variableAdded(final Variable newVariable) {
        addColumn(dataStore, newVariable);
        variableVisible(newVariable);
    }

    @Override
    public void variableRemoved(final Variable deletedVariable) {
        deselectAll();
        removeColumn(deletedVariable);
        revalidate();
        variableHidden(deletedVariable);
    }

    @Override
    public void variableOrderChanged() {
        // Do nothing.
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
        SpreadsheetColumn selectedColumn = Datavyu.getView().getSpreadsheetPanel().getSelectedColumn();
        // ⌘← and ⌘→ select the column to the left or right; plain arrows move the caret in a cell.
        if (isColumnMove(e, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx())) {

            List<SpreadsheetColumn> visibleColumns = Datavyu.getView().getSpreadsheetPanel().getVisibleColumns();

            // No visible columns: do nothing and consume event.
            // One visible: select it and consume event
            if(visibleColumns.size()==0) return true; // do nothing with no visible columns
            if(visibleColumns.size()==1){                                          // select the single column
                visibleColumns.get(0).setSelected(true);
                visibleColumns.get(0).requestFocus();
                return true;
            }

            // User is attempting to move to the column to the left.
            if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                if(selectedColumn == null) {
                    visibleColumns.get(visibleColumns.size()-1).setSelected(true); // select last visible column
                    visibleColumns.get(visibleColumns.size()-1).requestFocus();
                } else{
                    selectColumn(selectedColumn, -1);
                }
                e.consume();

                return true;

                // User is attempting to move to the column to the right.
            } else {

                if(selectedColumn == null){
                    visibleColumns.get(0).setSelected(true); // select first visible column
                    visibleColumns.get(0).requestFocus();
                } else{
                    selectColumn(selectedColumn,+1);
                }
                e.consume();
                
                return true;
            }
        } else if (e.getID() == KeyEvent.KEY_PRESSED && !e.isMetaDown() && !e.isControlDown()) {
            if ((Datavyu.getView().isQuickKeyMode() &&
                    (Character.isAlphabetic(e.getKeyChar()) || Character.isDigit(e.getKeyChar()))
            )) {

                if(selectedColumn != null && !selectedColumn.getVariable().isMedia()) {
                    CreateNewCellController controller = new CreateNewCellController();
                    Cell c = controller.createCell(selectedColumn.getVariable());
                    if (c == null) {
                        // A locked column refuses it.
                        e.consume();
                        return true;
                    }

                    CellValue v = c.getCellValue();
                    if(v instanceof MatrixCellValue) {
                        List<CellValue> vals = ((MatrixCellValue) v).getArguments();
                        vals.get(0).set(String.valueOf(e.getKeyChar()));
                    } else {
                        v.set(String.valueOf(e.getKeyChar()));
                    }
                    c.setOnset(Datavyu.getVideoController().getCurrentTime());
                    c.setOffset(Datavyu.getVideoController().getCurrentTime());

                    //Add the new cell to the Undo Support
                    UndoableEdit edit = new AddCellEdit(selectedColumn.getVariable().getName(), c);
                    Datavyu.getView().getComponent().revalidate();
                    Datavyu.getView().getUndoSupport().postEdit(edit);

                    Datavyu.getProjectController().getSpreadSheetPanel().redrawCells();
                    e.consume();
                    return true;
                }
                // Add a cell to that column with this key in the first argument
            }
        }
        return false;
    }

    /**
     * @param dir The direction in which to search for adjacent cells (left or
     *            right).
     * @return An indication of the columns adjacent of the current cell
     * selection,
     * 0 = no columns to the left of the current cell selection.
     * 1 = one column to the left of the current cell selection.
     * 2 = many columns to the left of the current cell selection.
     */
    public int getAdjacentSelectedCells(final ArrayDirection dir) {
        int result = 0;

        for (Cell cell : dataStore.getSelectedCells()) {
            for (int i = 0; i < dataStore.getVisibleVariables().size(); i++) {
                if (dataStore.getVisibleVariables().get(i).equals(dataStore.getVariable(cell))) {
                    // We have at least one column to the left of the cells.
                    if (((i + dir.getModifier()) >= 0) && ((i + dir.getModifier())
                            < dataStore.getVisibleVariables().size())) {
                        result++;
                    }

                    // We have many columns to the left of the cells - end.
                    if (result == 2) {
                        return result;
                    }
                }
            }
        }

        return result;
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
        new NewVariableController();
    }

    /**
     * Moves a given column to the right by a certain number of positions.
     *
     * @param var       The variable for the column to move
     * @param swapVar   The variable we are swapping with
     */
    public void moveColumn(final Variable var, final Variable swapVar) {
        logger.info("move column right");

        // What index does the column sit at
        int columnIndex = var.getOrderIndex();
        int swapColumnIndex = swapVar.getOrderIndex();

        if (columnIndex >= 0 && swapColumnIndex >= 0) {
            shuffleColumn(columnIndex, swapColumnIndex);
        }
    }

    /**
     * Removes the source column and reinserts the column at a given
     * destination.
     *
     * @param source      index of the source column
     * @param destination index of the destination column
     */
    public void shuffleColumn(final int source, final int destination) {
        int numCols = columns.size();
        assert (source < numCols && destination < numCols) : String.format("%d and %d must be less than %d", source, destination, numCols);

        if (source == destination) {
            return;
        }
        logger.info(source + ", " + destination);


        synchronized(this.getTreeLock()) {
            // Reorder the columns vector
            SpreadsheetColumn sourceColumn = columns.get(source);
            columns.remove(source);
            columns.add(destination, sourceColumn);

            // Go through columns and setOrderIndex().
//            updateColumnIndex();
            for(int i = Math.min(source, destination); i<= Math.max(source, destination); i++){
                columns.get(i).getVariable().setOrderIndex(i);
            }
            // Reorder the header components
            Component comp = headerView.getComponent(source + 1);
            headerView.remove(source + 1);
            headerView.add(comp, destination + 1);

            // Reorder the data components
            comp = mainView.getComponent(source);
            mainView.remove(source);
            mainView.add(comp, destination);

        }
        dataStore.columnsReordered();
        revalidate();
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
    }

    /**
     * Utility method for determining if the platform specific input mask is
     * triggered.
     *
     * @param e KeyEvent to examine.
     * @return true if the input mask is used, false otherwise.
     */
    /**
     * Whether a key press selects the next column: ⌘← or ⌘→, with no other modifier.
     *
     * @param e        the key event
     * @param menuMask the platform's menu shortcut modifier, as an extended modifier mask
     * @return whether the event is a column move
     */
    static boolean isColumnMove(final KeyEvent e, final int menuMask) {
        return e.getID() == KeyEvent.KEY_PRESSED
            && (e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_RIGHT)
            && (e.getModifiersEx() & ALL_MODIFIERS) == menuMask;
    }

    /** The keyboard modifiers that make an arrow key something other than a plain arrow. */
    private static final int ALL_MODIFIERS = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
        | InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;

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
    private void notifyFileDropEventListeners(final Iterable<File> files) {
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

    public void selectColumn(final SpreadsheetColumn selectedColumn, final int shift) {
        // Find currently selected cell, if there is one
        List<SpreadsheetColumn> visibleColumns = Datavyu.getView().getSpreadsheetPanel().getVisibleColumns();
        SpreadsheetCell sc = lastSelectedCell;
        int vcIndex = visibleColumns.indexOf(selectedColumn);
        int newIndex = Math.floorMod(vcIndex+shift, visibleColumns.size()); // wrap around visible columns
        if(0 <= newIndex
                && newIndex < visibleColumns.size()) {

            sc = selectedColumn.getDataPanel().getSelectedCell();

            clearCellSelection();
            clearColumnSelection();
            requestFocus();

            SpreadsheetColumn newColumn = visibleColumns.get(newIndex);
            SpreadsheetCell newCell = null;
            if(!newColumn.getCells().isEmpty()) {
                if (sc != null) {
                    if (Datavyu.getView().getSheetLayout() == SheetLayoutType.WeakTemporal) {
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
        public SSDropTarget() {
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

            for (int type = 0; type < flavors.length; type++) {

                if (flavors[type].isFlavorJavaFileListType()) {
                    dtde.acceptDrop(DnDConstants.ACTION_REFERENCE);

                    List fileList = new LinkedList();

                    try {
                        fileList = (List) tr.getTransferData(flavors[type]);

                        // If we made it this far, everything worked.
                        dtde.dropComplete(true);
                    } catch (UnsupportedFlavorException e) {
                        dtde.rejectDrop();
                    } catch (IOException e) {
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
