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
import org.datavyu.models.Identifier;
import org.datavyu.projectfile.ProjectDocument;
import org.datavyu.projectfile.ProjectDocument.MediaColumn;
import org.datavyu.projectfile.ProjectPaths;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * This class represents a project in Datavyu. A project manages the different
 * files used by Datavyu, such as database files and media files.
 *
 * <p>Media come from a {@code .watavyu.json} file as media columns, or from an older {@code .opf} file as viewer
 * settings.
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
     * The directory that the project file was saved to. Could be the same as
     * the project directory, and could importantly be blank in the case of
     * loading older project files.
     */
    private String originalProjectDirectory;

    private List<ViewerSetting> viewerSettings;

    @Deprecated
    private List<TrackSettings> interfaceSettings;

    /** Media columns read from a project file, to open when the project loads. */
    private List<MediaColumn> mediaColumns = List.of();

    /**
     * Media columns whose media is not open: its file is missing, failed to open or is still opening. They are saved
     * as they were read, so media that cannot be played is not dropped from the project.
     */
    private final List<MediaColumn> unavailableMedia = new ArrayList<>();

    /** The column name of each open media file. */
    private final Map<Identifier, String> mediaNames = new HashMap<>();

    /**
     * Constructor.
     */
    public Project() {
        viewerSettings = new LinkedList<ViewerSetting>();
        interfaceSettings = new LinkedList<TrackSettings>();
    }

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
        String name = fileName.endsWith(ProjectDocument.EXTENSION)
                ? ProjectPaths.projectName(Paths.get(fileName))
                : FilenameUtils.removeExtension(fileName);

        if ("".equals(name)) {
            name = "Project1";
        }

        projectName = name;
    }

    public void setViewerSettings(
            final Iterable<ViewerSetting> viewerSettings) {

        if (viewerSettings != null) {
            this.viewerSettings = new LinkedList<ViewerSetting>();

            for (ViewerSetting viewerSetting : viewerSettings) {
                this.viewerSettings.add(viewerSetting);
            }
        }
    }

    @Deprecated
    public void setTrackSettings(
            final Iterable<TrackSettings> interfaceSettings) {

        if (interfaceSettings != null) {
            this.interfaceSettings = new LinkedList<TrackSettings>();

            for (TrackSettings interfaceSetting : interfaceSettings) {
                this.interfaceSettings.add(interfaceSetting);
            }
        }
    }

    /**
     * @return Viewer settings used for each media file being managed by
     * Datavyu.
     */
    public Iterable<ViewerSetting> getViewerSettings() {
        return viewerSettings;
    }

    @Deprecated
    public Iterable<TrackSettings> getTrackSettings() {
        return interfaceSettings;
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

    /**
     * @return the directory the project file was saved to.
     */
    public String getOriginalProjectDirectory() {
        return originalProjectDirectory;
    }

    /**
     * @param originalProjectDirectory sets the directory the project file was
     *                                 saved to.
     */
    public void setOriginalProjectDirectory(
            final String originalProjectDirectory) {
        this.originalProjectDirectory = originalProjectDirectory;
    }

    /** @return the media columns read from a project file, to open when the project loads */
    public List<MediaColumn> getMediaColumns() {
        return mediaColumns;
    }

    /** @param columns the media columns read from a project file */
    public void setMediaColumns(final List<MediaColumn> columns) {
        mediaColumns = List.copyOf(columns);
    }

    /** @return the media columns whose media is not open, in the order they were read */
    public List<MediaColumn> getUnavailableMedia() {
        return List.copyOf(unavailableMedia);
    }

    /** @param column a media column whose media is not open, or not open yet */
    public void addUnavailableMedia(final MediaColumn column) {
        unavailableMedia.add(column);
    }

    /**
     * Records that a media column's media has opened.
     *
     * @param column the column
     * @param media  the identifier of its open media
     */
    public void mediaOpened(final MediaColumn column, final Identifier media) {
        unavailableMedia.remove(column);
        mediaNames.put(media, column.name());
    }

    /**
     * @param media the identifier of open media
     * @return the name of its column, or null if it has not been named yet
     */
    public String getMediaName(final Identifier media) {
        return mediaNames.get(media);
    }

    /**
     * @param media the identifier of open media
     * @param name  the name of its column
     */
    public void setMediaName(final Identifier media, final String name) {
        mediaNames.put(media, name);
    }
}
