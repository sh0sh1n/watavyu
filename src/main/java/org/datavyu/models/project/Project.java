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
package org.datavyu.models.project;

import org.apache.commons.io.FilenameUtils;
import org.datavyu.projectfile.ProjectPaths;

import java.nio.file.Paths;


/**
 * This class represents a project in Datavyu. A project manages the different
 * files used by Datavyu, such as database files and media files.
 */
public final class Project {

    /**
     * Name of this project.
     */
    private String projectName;

    /**
     * Database file name.
     */
    private String databaseFileName;

    /**
     * The directory that the project file resides in.
     */
    private String projectDirectory;

    /**
     * @return The database file name. Does not include directory.
     */
    public String getDatabaseFileName() {
        return databaseFileName;
    }

    /**
     * @param fileName the database file name. Does not include directory.
     */
    public void setDatabaseFileName(final String fileName) {
        databaseFileName = fileName;
    }

    /**
     * @return The name of this project.
     */
    public String getProjectName() {
        return projectName;
    }

    /**
     * Sets the name of the project.
     *
     * @param newProjectName The new name to use for this project.
     */
    public void setProjectName(final String newProjectName) {

        // Check Pre-conditions.
        assert (newProjectName != null);

        // Set the name of the project.
        String fileName = FilenameUtils.getName(newProjectName);
        String name = ProjectPaths.isProjectFile(fileName)
                ? ProjectPaths.projectName(Paths.get(fileName))
                : FilenameUtils.removeExtension(fileName);

        if ("".equals(name)) {
            name = "Project1";
        }

        projectName = name;
    }

    /**
     * @return the projectDirectory
     */
    public String getProjectDirectory() {
        return projectDirectory;
    }

    /**
     * @param projectDirectory the projectDirectory to set
     */
    public void setProjectDirectory(final String projectDirectory) {
        this.projectDirectory = projectDirectory;
    }
}
