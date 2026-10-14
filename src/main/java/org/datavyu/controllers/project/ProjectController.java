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
import org.datavyu.controllers.component.MixerController;
import org.datavyu.models.Identifier;
import org.datavyu.models.component.TrackModel;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.Variable;
import org.datavyu.models.project.Project;
import org.datavyu.models.project.TrackSettings;
import org.datavyu.models.project.ViewerSetting;
import org.datavyu.plugins.StreamViewer;
import org.datavyu.plugins.javafx.JavaFxVideoPlugin;
import org.datavyu.util.FileSystemUtils;
import org.datavyu.views.VideoController;
import org.datavyu.views.discrete.SpreadSheetPanel;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
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
     * Set the original project directory.
     *
     * @param directory the original directory.
     */
    public void setOriginalProjectDirectory(final String directory) {
        project.setOriginalProjectDirectory(directory);
    }

    /**
     * Load the settings from the current project.
     */
    public void loadProject() {

        VideoController videoController = spreadSheetPanel.getVideoController();

        // Load the plugins required for each media file
        boolean showController = false;

        List<String> missingFilesList = new LinkedList<>();
        List<String> missingPluginList = new LinkedList<>();

        final MixerController mixerController = videoController.getMixerController();

        // Load the viewer settings.
        for (ViewerSetting setting : project.getViewerSettings()) {
            showController = true;

            File file = new File(setting.getFilePath());


            if (!file.exists()) {
                // Look for a file by generating OS-independent paths.
                File searchedFile = FileSystemUtils.generateRelative(
                        project.getOriginalProjectDirectory(),
                        setting.getFilePath(), project.getProjectDirectory());

                if (searchedFile != null) {
                    file = searchedFile;
                }
            }

            // TODO: Enable search of file again
            /*
            if (!file.exists()) {
                File currentDir = new File(project.getProjectDirectory());
                String dataFileName = FilenameUtils.getName(setting.getFilePath());

                // Look for a file that _might_ be the file we are looking for.
                // Searches in this directory, all child directories, and from
                // the parent directory in all child directories.
                File searchedFile = FileSystemUtils.searchForFile(currentDir, dataFileName);

                if (searchedFile != null) {
                    file = searchedFile;
                }
            }
            */

            // The file is actually missing.
            if (!file.exists()) {
                missingFilesList.add(setting.getFilePath());

                continue;
            }

            ViewerSetting.PlayerMatch match = setting.playerMatch();
            if (match == ViewerSetting.PlayerMatch.NONE) {
                missingPluginList.add(setting.getPluginName());
                continue;
            }
            if (match == ViewerSetting.PlayerMatch.OTHER_VIDEO_PLAYER) {
                JOptionPane.showMessageDialog(Datavyu.getView().getComponent(),
                    "This project uses the video plugin " + setting.getPluginName()
                        + ", which is no longer available.\nThe video will be opened with the default plugin.",
                    "Plugin Not Available", JOptionPane.INFORMATION_MESSAGE);
            }

            // Media opens in the background; the track appears once the player is ready.
            final ViewerSetting viewerSetting = setting;
            videoController.openStream(Identifier.generateIdentifier(), file, streamViewer -> {
                if (viewerSetting.getSettingsId() != null) {
                    // new project file
                    streamViewer.loadSettings(viewerSetting.getSettingsInputStream());
                } else {
                    // old project file
                    streamViewer.setOffset(viewerSetting.getOffset());
                }

                // Make sure that the parent dialog is visible
                if (streamViewer.getParentJDialog() != null) {
                    boolean visible = streamViewer.getParentJDialog().isVisible();
                    Datavyu.getApplication().show(streamViewer.getParentJDialog());
                    if (!visible) {
                        streamViewer.getParentJDialog().setVisible(false);
                    }
                }
            }, streamViewer -> {
                if (viewerSetting.getTrackSettings() != null) {
                    final TrackSettings ts = viewerSetting.getTrackSettings();
                    mixerController.setTrackLocked(streamViewer.getIdentifier(), ts.isLocked());
                }
            });
        }

        // Do not remove; this is here for backwards compatibility.
        for (TrackSettings setting : project.getTrackSettings()) {
            File file = new File(setting.getFilePath());

            if (!file.exists()) {

                // Look for a file by generating OS-independent paths.
                // This is not guaranteed for older project file formats.
                File searchedFile = FileSystemUtils.generateRelative(
                        project.getOriginalProjectDirectory(),
                        setting.getFilePath(), project.getProjectDirectory());

                if (searchedFile != null) {
                    file = searchedFile;
                }
            }

            if (!file.exists()) {

                // If absolute path does not find the file, look
                // in the relative path (as long as we are dealing with a newer
                // project file type).
                if (project.getOriginalProjectDirectory() != null) {

                    File searchedFile = FileSystemUtils.searchForFile(new File(
                            project.getProjectDirectory()), file.getName());

                    if (searchedFile != null) {
                        file = searchedFile;
                    }
                }
            }

            if (!file.exists()) {
                missingFilesList.add(setting.getFilePath());

                continue;
            }

            mixerController.setTrackLocked(setting.getFilePath(), setting.isLocked());
        }

        if (!missingFilesList.isEmpty() || !missingPluginList.isEmpty()) {
            JFrame mainFrame = Datavyu.getApplication().getMainFrame();
            Resources rMap = Resources.of(Datavyu.class);

            StringBuilder sb = new StringBuilder();

            if (!missingFilesList.isEmpty()) {
                sb.append("The following files are missing:\n\n");

                for (String filePath : missingFilesList) {
                    sb.append(filePath);
                    sb.append('\n');
                }
            }

            if (!missingPluginList.isEmpty()) {

                if (sb.length() != 0) {
                    sb.append('\n');
                }

                sb.append("The following plugins are missing:\n\n");

                for (String pluginName : missingPluginList) {
                    sb.append(pluginName);
                    sb.append('\n');
                }
            }

            JOptionPane.showMessageDialog(mainFrame, sb.toString(),
                    rMap.getString("ProjectLoadError.title"),
                    JOptionPane.WARNING_MESSAGE);

            showController = true;
        }

        // Show the data controller
        if (showController) {
            Datavyu.getApplication().showVideoController();
        }

    }

    /**
     * Gather and update the various project specific settings.
     */
    public void updateProject() {

        VideoController dataController = spreadSheetPanel.getVideoController();

        // Gather the data viewer settings
        List<ViewerSetting> viewerSettings = new LinkedList<ViewerSetting>();

        int settingsId = 1;

        for (StreamViewer viewer : dataController.getStreamViewers()) {
            ViewerSetting viewerSetting = new ViewerSetting();
            viewerSetting.setFilePath(viewer.getSourceFile().getAbsolutePath());
            viewerSetting.setPluginName(viewer.getClass().getName());


            viewerSetting.setPluginNameAndUUID(JavaFxVideoPlugin.PLUGIN_UUID);
            viewerSetting.setPluginClassifier(JavaFxVideoPlugin.NAMESPACE);

            viewerSetting.setSettingsId(Integer.toString(settingsId++));
            viewer.storeSettings(viewerSetting.getSettingsOutputStream());

            TrackModel tm = dataController.getMixerController().getTrackModel(viewer.getIdentifier());
            TrackSettings ts = new TrackSettings();
            ts.setLocked(tm.isLocked());

            viewerSetting.setTrackSettings(ts);

            viewerSettings.add(viewerSetting);
        }

        project.setViewerSettings(viewerSettings);
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
