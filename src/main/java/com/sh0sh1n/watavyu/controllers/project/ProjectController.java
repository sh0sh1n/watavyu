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
package com.sh0sh1n.watavyu.controllers.project;

import com.sh0sh1n.watavyu.util.Resources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.sh0sh1n.watavyu.controllers.AutoSaveController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.UserWarningException;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.Project;
import com.sh0sh1n.watavyu.projectfile.ProjectDocument;
import com.sh0sh1n.watavyu.projectfile.SpreadsheetColumns;
import com.sh0sh1n.watavyu.undoableedits.SpreadsheetUndoManager;
import com.sh0sh1n.watavyu.views.VideoController;
import com.sh0sh1n.watavyu.views.spreadsheet.SpreadSheetPanel;

import javax.swing.*;
import javax.swing.event.UndoableEditListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.undo.UndoableEdit;
import javax.swing.undo.UndoableEditSupport;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;


/**
 * One open spreadsheet, the contents of one tab: its project, data, undo history and playback. Its views, controllers
 * and undo edits are given it, rather than finding the selected tab's, so each acts on its own spreadsheet.
 */
public final class ProjectController {

    /** Logger for this class */
    private static Logger logger = LogManager.getLogger(ProjectController.class);

    /** The spread sheet panel that this controller is associated with */
    private SpreadSheetPanel spreadSheetPanel;

    /** The current project we are working on */
    private Project project;

    /** The current database we are working on */
    private DataStore dataStore = null;

    /** The last cell that was created */
    private Cell lastCreatedCell;

    /** The last list of cells that was selected */
    private List<Cell> lastSelectedCells;

    /** The last cell that was selected */
    private Cell lastSelectedCell;

    /** The last column that was created */
    private Column lastCreatedColumn;

    /** Project has been changed */
    private boolean changed;

    /** New project? */
    private boolean newProject;

    /** Last option used for saving */
    private FileFilter lastSaveOption;

    /** This spreadsheet's undo history, which every edit posted to {@link #postEdit} joins. */
    private final SpreadsheetUndoManager undoManager = new SpreadsheetUndoManager();

    /** Where this spreadsheet's edits are posted; the undo history listens first. */
    private final UndoableEditSupport undoSupport = new UndoableEditSupport();

    /** This spreadsheet's clock, players and playback commands; set before its view is made. */
    private VideoController videoController;

    /** Runs when the project's name or changed state may have changed, so its title can be shown again. */
    private Runnable titleListener = () -> { };

    /** Autosaves this spreadsheet while its tab is open; null until started. */
    private AutoSaveController autoSave;

    /** The commands that make, delete and time cells */
    private final CellCommands cellCommands = new CellCommands(this);

    /** The commands for typing into cells */
    private final TypingCommands typingCommands = new TypingCommands(this);

    /** The commands that add, delete, rename, lock, hide and move columns */
    private final ColumnCommands columnCommands = new ColumnCommands(this);

    /** The commands that add, delete, move and rename columns' codes */
    private final CodeCommands codeCommands = new CodeCommands(this);

    /** The commands that add media */
    private final MediaCommands mediaCommands = new MediaCommands(this);

    /** Whether quick-key mode is on, in which typing in a selected column makes a cell; an application setting. */
    private BooleanSupplier quickKeyMode = () -> false;

    /**
     * Default constructor.
     */
    public ProjectController() {
        this(new Project(), DataStoreFactory.newDataStore());
        newProject = true;
    }

    public ProjectController(final Project project, final DataStore dataStore) {
        this.project = project;
        this.dataStore = dataStore;
        changed = false;
        newProject = false;
        lastCreatedCell = null;
        lastSelectedCell = null;
        lastCreatedColumn = null;
        lastSelectedCells = new ArrayList<>();
        dataStore.setTitleNotifier(this::titleChanged);
        undoSupport.addUndoableEditListener(event -> undoManager.addEdit(event.getEdit()));
    }

    /**
     * Sets what runs when the project's name or changed state may have changed.
     *
     * @param titleListener shows the project's title again
     */
    public void setTitleListener(final Runnable titleListener) {
        this.titleListener = titleListener;
    }

    private void titleChanged() {
        titleListener.run();
    }

    /**
     * Records an edit in this spreadsheet's undo history, and tells the edit's listeners.
     *
     * @param edit an edit already made to this spreadsheet
     */
    public void postEdit(final UndoableEdit edit) {
        undoSupport.postEdit(edit);
    }

    /**
     * Tells a listener of each edit posted, after the undo history has it.
     *
     * @param listener the listener
     */
    public void addUndoableEditListener(final UndoableEditListener listener) {
        undoSupport.addUndoableEditListener(listener);
    }

    /** Returns this spreadsheet's undo history. */
    public SpreadsheetUndoManager getUndoManager() {
        return undoManager;
    }

    /**
     * Undoes this spreadsheet's last edit, and shows its cells again, unless the edit refuses (see
     * {@code GuardedEdit}), which leaves the history as it was.
     *
     * @return why the edit refused, for the user, or null if it was undone or there was nothing to undo
     */
    public String undo() {
        String refusal = undoManager.undoRefusal();
        if (refusal == null && undoManager.canUndo()) {
            undoManager.undo();
            redrawCells();
        }
        return refusal;
    }

    /**
     * Redoes this spreadsheet's last undone edit, and shows its cells again, unless the edit refuses, which leaves the
     * history as it was.
     *
     * @return why the edit refused, for the user, or null if it was redone or there was nothing to redo
     */
    public String redo() {
        String refusal = undoManager.redoRefusal();
        if (refusal == null && undoManager.canRedo()) {
            undoManager.redo();
            redrawCells();
        }
        return refusal;
    }

    /** Shows every cell's value and selection again, as after undo and redo. */
    void redrawCells() {
        if (spreadSheetPanel != null) {
            spreadSheetPanel.redrawCells();
        }
    }

    /**
     * Starts autosaving this spreadsheet.
     *
     * @param minutes how often to save it
     */
    public void startAutoSave(final int minutes) {
        autoSave = new AutoSaveController(this, minutes);
        autoSave.start();
    }

    /** Returns this spreadsheet's autosave, or null until it is started. */
    public AutoSaveController getAutoSave() {
        return autoSave;
    }

    /** Records that the project was just saved: it has no unsaved changes, so its autosave is deleted. */
    public void markSaved() {
        markProjectAsUnchanged();
        dataStore.markAsUnchanged();
        if (autoSave != null) {
            autoSave.clear();
        }
    }

    /** Closes this spreadsheet, once its work is saved or knowingly discarded: its autosave stops and is deleted. */
    public void close() {
        if (autoSave != null) {
            autoSave.discard();
        }
    }

    /**
     * Sets where this spreadsheet learns whether quick-key mode is on.
     *
     * @param quickKeyMode tells whether quick-key mode is on
     */
    public void setQuickKeyMode(final BooleanSupplier quickKeyMode) {
        this.quickKeyMode = quickKeyMode;
    }

    /** Returns whether quick-key mode is on, in which typing in a selected column makes a cell. */
    public boolean isQuickKeyMode() {
        return quickKeyMode.getAsBoolean();
    }

    /** Returns the commands that make, delete and time this spreadsheet's cells. */
    public CellCommands cells() {
        return cellCommands;
    }

    /** Returns the commands for typing into this spreadsheet's cells, from their value and time editors. */
    public TypingCommands typing() {
        return typingCommands;
    }

    /** Returns the commands that add, delete, rename, lock, hide and move this spreadsheet's columns. */
    public ColumnCommands columns() {
        return columnCommands;
    }

    /** Returns the commands that add, delete, move and rename the codes of this spreadsheet's columns. */
    public CodeCommands codes() {
        return codeCommands;
    }

    /** Returns the commands that add media to this spreadsheet. */
    public MediaCommands media() {
        return mediaCommands;
    }

    /**
     * Removes cells without recording an edit, as deleting and undoing a new cell do; the cells last created and
     * selected are forgotten if they go. A cell already in no column is left as it is.
     *
     * @param removed cells of this spreadsheet
     */
    public void removeCells(final List<Cell> removed) {
        List<Cell> stillSelected = new ArrayList<>(lastSelectedCells);
        stillSelected.removeAll(removed);
        lastSelectedCells = stillSelected;
        for (Cell cell : removed) {
            if (cell.equals(lastCreatedCell)) {
                lastCreatedCell = null;
            }
            if (cell.equals(lastSelectedCell)) {
                lastSelectedCell = null;
            }
            if (dataStore.getColumn(cell) != null) {
                dataStore.removeCell(cell);
            }
        }
    }

    /** Returns this spreadsheet's clock, players and playback commands; null until set. */
    public VideoController getVideoController() {
        return videoController;
    }

    /**
     * Sets this spreadsheet's video controller. Its view needs one, so this is set before the view is made.
     *
     * @param videoController the spreadsheet's clock, players and playback commands
     */
    public void setVideoController(final VideoController videoController) {
        this.videoController = videoController;
    }

    /**
     * Set the last save option used. This affects the "Save" functionality.
     *
     * @param saveOption The latest option used for "saving".
     */
    public void setLastSaveOption(final FileFilter saveOption) {
        lastSaveOption = saveOption;
    }

    /**
     *
     * Returns the last "saved" option used when saving.
     */
    public FileFilter getLastSaveOption() {
        return lastSaveOption;
    }

    /**
     * Sets the name of the project.
     *
     * @param newProjectName The new name to use for this project.
     */
    public void setProjectName(final String newProjectName) {
        project.setProjectName(newProjectName);
    }

    /**
     * Gets the DataStore associated with this project.
     *
     * @return The single DataStore used for this project.
     */
    public DataStore getDataStore() {
        return dataStore;
    }

    /**
     * Sets the data store to use with this project. This is used when loading a
     * database from file.
     *
     * @param newDataStore The new data store we are using.
     */
    public void setDataStore(final DataStore newDataStore) {
        dataStore = newDataStore;
        dataStore.setTitleNotifier(this::titleChanged);
    }

    /**
     * Returns the last created cell.
     *
     * @return The last cell created for the DataStore.
     */
    public Cell getLastCreatedCell() {
        return lastCreatedCell;
    }

    /**
     * Sets the last created cell to the specified parameter.
     *
     * @param newCell The newly created cell.
     */
    public void setLastCreatedCell(final Cell newCell) {
        lastCreatedCell = newCell;
    }

    /**
     * Returns the last selected cell.
     */
    public Cell getLastSelectedCell() {
        return lastSelectedCell;
    }

    /**
     * Return a list of last selected cells.
     *
     * @return The last selected cells.
     */
    public List<Cell> getLastSelectedCells() { return lastSelectedCells; }

    /**
     * Sets the last selected cell to the specified cell.
     *
     * @param newCell The newly selected cell.
     */
    public void setLastSelectedCell(final Cell newCell) {
        lastSelectedCell = newCell;
    }

    /**
     * Sets the last selected cells, this setter is used to persist the selected cells
     * before performing actions on the Menu.
     *
     * @param lastSelectedCells The newly selected cell.
     */
    public void setLastSelectedCells(List<Cell> lastSelectedCells) {
        this.lastSelectedCells = lastSelectedCells;
    }

    /**
     * Returns the last column created for the datastore.
     */
    public Column getLastCreatedColumn() {
        return lastCreatedColumn;
    }

    /**
     * Sets the newly created column to the specified parameter.
     *
     * @param newColumn The newly created column.
     */
    public void setLastCreatedColumn(final Column newColumn) {
        lastCreatedColumn = newColumn;
    }

    /**
     * Returns whether the project has changes not yet saved.
     */
    public boolean isChanged() {
        return (changed || ((dataStore != null) && dataStore.isChanged()));
    }

    /**
     * Returns the newProject.
     */
    public boolean isNewProject() {
        return newProject;
    }

    /**
     * Returns the project name.
     */
    public String getProjectName() {
        return project.getProjectName();
    }
    
   /**
     * Returns the project name for purposes for display. returns "(untitled)" instead of null.
     */
    public String getProjectNamePretty() {
        if (project.getProjectName() != null) {
            return project.getProjectName();
        } else {
            return "(untitled)";
        }
    }

    /**
     * Set the database file name, directory not included.
     *
     * @param fileName the file's name
     */
    public void setDatabaseFileName(final String fileName) {
        project.setDatabaseFileName(fileName);
    }

    /**
     * Returns the database file name, directory not included.
     */
    public String getDatabaseFileName() {
        return project.getDatabaseFileName();
    }

    /**
     * Get the full path
     *
     * @return the database file name, directory not included.
     */
    public String getFullPath() {
        try {
            return new File(project.getProjectDirectory() + File.separator
                    + project.getDatabaseFileName()).getCanonicalPath();
        } catch (IOException e) {
            logger.error("Unable to get full path. Error: ", e);
        }
        return null;
    }

    /**
     * Set the directory the project file (and all project specific resources) resides in.
     *
     * @param directory The project directory.
     */
    public void setProjectDirectory(final String directory) {
        project.setProjectDirectory(directory);
    }

    /**
     * Get the project directory.
     *
     * @return the directory the project file and all project specific resources reside in.
     */
    public String getProjectDirectory() {
        return project.getProjectDirectory();
    }

    /**
     * Opens the players of the project's media columns. Reports media that is missing.
     */
    public void loadProject() {
        List<String> missingFilesList = spreadSheetPanel.getMediaPlayers().openAll();

        if (!missingFilesList.isEmpty()) {
            Resources rMap = Resources.of(ProjectController.class);

            StringBuilder sb = new StringBuilder("The following files are missing:\n\n");
            for (String filePath : missingFilesList) {
                sb.append(filePath);
                sb.append('\n');
            }

            JOptionPane.showMessageDialog(spreadSheetPanel, sb.toString(),
                    rMap.getString("ProjectLoadError.title"),
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * The project as a project file holds it: every column, media and annotation, in spreadsheet order.
     *
     * @param projectFolder the folder the project file is saved in, which media paths are relative to
     * @return the project
     * @throws UserWarningException if a clip's file has not opened yet, so what it holds is unknown
     */
    public ProjectDocument toDocument(final Path projectFolder) throws UserWarningException {
        return new ProjectDocument(SpreadsheetColumns.columns(dataStore, projectFolder));
    }

    /**
     * Marks the project state as being saved.
     */
    public void markProjectAsUnchanged() {
        changed = false;
        newProject = false;
        titleChanged();
    }

    /**
     * Marks the project as being changed. This method will not trigger a
     * project state update.
     */
    public void projectChanged() {
        changed = true;
        titleChanged();
    }

    /**
     * Returns the current project model.
     */
    public Project getProject() {
        return project;
    }

    /**
     *
     * Returns the spreadsheet panel.
     */
    public SpreadSheetPanel getSpreadSheetPanel() {
        return spreadSheetPanel;
    }

    /**
     * Sets the spreadsheet panel.
     *
     * @param spreadSheetPanel the panel showing this spreadsheet
     */
    public void setSpreadSheetPanel(SpreadSheetPanel spreadSheetPanel) {
        this.spreadSheetPanel = spreadSheetPanel;
    }
}
