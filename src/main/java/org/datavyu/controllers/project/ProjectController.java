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
package org.datavyu.controllers.project;

import org.datavyu.util.Resources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.Datavyu;
import org.datavyu.controllers.VocabEditorController;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.db.Variable;
import org.datavyu.models.project.Project;
import org.datavyu.projectfile.ProjectDocument;
import org.datavyu.projectfile.SpreadsheetColumns;
import org.datavyu.views.discrete.SpreadSheetPanel;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


/**
 * This class is responsible for managing a project.
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

    /** The last variable that was created */
    private Variable lastCreatedVariable;

    /** Project has been changed */
    private boolean changed;

    /** New project? */
    private boolean newProject;

    /** Last option used for saving */
    private FileFilter lastSaveOption;

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
        lastCreatedVariable = null;
        lastSelectedCells = new ArrayList<>();
        dataStore.setTitleNotifier(Datavyu.getApplication());
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
     * @return The last "saved" option used when saving.
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
        dataStore.setTitleNotifier(Datavyu.getApplication());
        
        //don't let code editor instance corresponding to an old DataStore hang around!
        VocabEditorController.getController().killView();
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
     * @return The last selected cell.
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
     * @return The last variable created for the datastore.
     */
    public Variable getLastCreatedVariable() {
        return lastCreatedVariable;
    }

    /**
     * Sets the newly created variable to the specified parameter.
     *
     * @param newVariable The newly created variable.
     */
    public void setLastCreatedVariable(final Variable newVariable) {
        lastCreatedVariable = newVariable;
    }

    /**
     * @return the changed
     */
    public boolean isChanged() {
        return (changed || ((dataStore != null) && dataStore.isChanged()));
    }

    /**
     * @return the newProject
     */
    public boolean isNewProject() {
        return newProject;
    }

    /**
     * @return the project name
     */
    public String getProjectName() {
        return project.getProjectName();
    }
    
   /**
     * @return the project name for purposes for display. returns "(untitled)" instead of null
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
     * @param fileName
     */
    public void setDatabaseFileName(final String fileName) {
        project.setDatabaseFileName(fileName);
    }

    /**
     * @return the database file name, directory not included.
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
            JFrame mainFrame = Datavyu.getApplication().getMainFrame();
            Resources rMap = Resources.of(Datavyu.class);

            StringBuilder sb = new StringBuilder("The following files are missing:\n\n");
            for (String filePath : missingFilesList) {
                sb.append(filePath);
                sb.append('\n');
            }

            JOptionPane.showMessageDialog(mainFrame, sb.toString(),
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
        Datavyu.getApplication().updateTitle();
    }

    /**
     * Marks the project as being changed. This method will not trigger a
     * project state update.
     */
    public void projectChanged() {
        changed = true;

        Datavyu.getApplication().updateTitle();
    }

    /**
     * @return the current project model.
     */
    public Project getProject() {
        return project;
    }

    /**
     *
     * @return the spreadsheet panel.
     */
    public SpreadSheetPanel getSpreadSheetPanel() {
        return spreadSheetPanel;
    }

    /**
     * Sets the spreadsheet panel.
     *
     * @param spreadSheetPanel
     */
    public void setSpreadSheetPanel(SpreadSheetPanel spreadSheetPanel) {
        this.spreadSheetPanel = spreadSheetPanel;
    }
}
