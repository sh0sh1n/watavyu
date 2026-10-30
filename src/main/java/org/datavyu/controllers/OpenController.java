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
package org.datavyu.controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.project.Project;
import org.datavyu.projectfile.ProjectDocument;
import org.datavyu.projectfile.ProjectFileException;
import org.datavyu.projectfile.ProjectJson;
import org.datavyu.projectfile.SpreadsheetColumns;

import java.io.File;


/**
 * Controller for opening Datavyu databases and project files.
 */
public final class OpenController {

    /** The logger instance for this class */
    private static Logger logger = LogManager.getLogger(OpenController.class);

    /** Reference to the data base that this controller opened */
    private DataStore dataStore = null;

    /** Reference to the project that this controller opened */
    private Project project = null;

    /** Why the last project file could not be opened. */
    private String error = null;


    /**
     * Opens a file as a Datavyu dataStore.
     *
     * @param dataStoreFile The file to use when opening a file as a dataStore.
     */
    public void openDataStore(final File dataStoreFile) {
        OpenDataStoreFileController odc = new OpenDataStoreFileController();
        dataStore = odc.open(dataStoreFile);
        dataStore.deselectAll();
    }

    /**
     * Opens a project file. On failure the data store and project stay null and {@link #getError()} says why.
     *
     * @param projectFile the project file
     */
    public void openProjectFile(final File projectFile) {
        try {
            ProjectDocument document = ProjectJson.read(projectFile.toPath());
            DataStore openedDataStore = DataStoreFactory.newDataStore();
            SpreadsheetColumns.addColumns(openedDataStore, document.columns(),
                projectFile.getAbsoluteFile().getParentFile().toPath());
            openedDataStore.setName(projectFile.getName());
            openedDataStore.deselectAll();
            openedDataStore.markAsUnchanged();

            dataStore = openedDataStore;
            project = new Project();
        } catch (ProjectFileException | UserWarningException e) {
            logger.error("Unable to open project file " + projectFile, e);
            error = "Unable to open " + projectFile.getName() + ".\n" + e.getMessage();
        }
    }

    /** @return why the last project file could not be opened, or null if it opened */
    public String getError() {
        return error;
    }

    /**
     * @return The instance of the data store that was opened by this controller, returns null if no dataStore opened.
     */
    public DataStore getDataStore() {
        return dataStore;
    }

    /**
     * @return The instance of the project
     */
    public Project getProject() {
        return project;
    }
}
