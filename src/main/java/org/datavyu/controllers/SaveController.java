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
import org.datavyu.models.db.UserWarningException;
import org.datavyu.projectfile.ProjectDocument;
import org.datavyu.projectfile.ProjectFileException;
import org.datavyu.projectfile.ProjectJson;
import org.datavyu.util.Settings;

import java.io.File;

/**
 * Saves project files.
 */
public final class SaveController {

    /** The logger instance for this class */
    private static final Logger logger = LogManager.getLogger(SaveController.class);

    /**
     * Saves a project file and adds it to the recent projects.
     *
     * @param projectFile where to save
     * @param document    the project
     * @throws UserWarningException if the project cannot be saved
     */
    public void saveProject(final File projectFile, final ProjectDocument document) throws UserWarningException {
        saveProject(projectFile, document, true);
    }

    /**
     * Saves a project file.
     *
     * @param projectFile where to save
     * @param document    the project
     * @param remember    whether to add the file to the recent projects
     * @throws UserWarningException if the project cannot be saved
     */
    public void saveProject(final File projectFile, final ProjectDocument document, final boolean remember)
            throws UserWarningException {
        logger.info("Saving project to " + projectFile.getAbsolutePath());
        try {
            ProjectJson.write(projectFile.toPath(), document);
        } catch (ProjectFileException e) {
            logger.error("Failed to save project. Error: ", e);
            throw new UserWarningException("Unable to save " + projectFile.getName() + ".\n" + e.getMessage(), e);
        }
        if (remember) {
            Settings.get().rememberProject(projectFile);
            Settings.get().save();
        }
    }
}
