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
package com.sh0sh1n.watavyu.views;

import com.sh0sh1n.watavyu.util.Dialogs;
import com.sh0sh1n.watavyu.util.Resources;
import com.sh0sh1n.watavyu.util.Settings;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.sh0sh1n.watavyu.Datavyu;
import com.sh0sh1n.watavyu.controllers.*;
import com.sh0sh1n.watavyu.controllers.project.MediaColumns;
import com.sh0sh1n.watavyu.controllers.project.ProjectController;
import com.sh0sh1n.watavyu.event.component.FileDropEvent;
import com.sh0sh1n.watavyu.event.component.FileDropEventListener;
import com.sh0sh1n.watavyu.models.db.*;
import com.sh0sh1n.watavyu.plugins.javafx.JavaFxVideoPlugin;
import com.sh0sh1n.watavyu.projectfile.ProjectDocument;
import com.sh0sh1n.watavyu.projectfile.ProjectPaths;
import com.sh0sh1n.watavyu.undoableedits.SpreadsheetUndoManager;
import com.sh0sh1n.watavyu.util.ArrayDirection;
import com.sh0sh1n.watavyu.util.DragAndDrop.TransparentPanel;
import com.sh0sh1n.watavyu.util.FileFilters.*;
import com.sh0sh1n.watavyu.views.discrete.SpreadSheetPanel;
import com.sh0sh1n.watavyu.views.discrete.SpreadsheetTheme;
import com.sh0sh1n.watavyu.views.discrete.layouts.SheetLayoutFactory;
import com.sh0sh1n.watavyu.views.discrete.layouts.SheetLayoutFactory.SheetLayoutType;

import javax.swing.*;
import javax.swing.Action;
import javax.swing.event.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * The main window: a tab per open spreadsheet, the menu bar, and the keyboard shortcuts.
 */
public final class DatavyuView implements FileDropEventListener {

    // Variable for the amount to raise the font size by when zooming.
    public static final int ZOOM_INTERVAL = 2;

    // Variables to set the maximum zoom and minimum zoom.
    public static final int ZOOM_MAX_SIZE = 42;
    public static final int ZOOM_MIN_SIZE = 8;

    /** The logger instance for this class */
    private static final Logger logger = LogManager.getLogger(DatavyuView.class);

    /** How often each spreadsheet is autosaved, in minutes */
    private static final int AUTOSAVE_MINUTES = 1;

    private DataviewProgressBar progressBar;
    private OpenTask task;

    /** The Code Editor, which edits the selected tab's columns and codes; null until first shown */
    private VocabEditorV codeEditor;

    /** The running application, which quits and asks about unsaved work */
    private final Datavyu app;

    /** The main window */
    private final JFrame frame;

    /** Text of this view's menus and dialogs */
    private final Resources resources = Resources.of(DatavyuView.class);

    /** Every command's action, which its menu item and shortcut share */
    private final CommandTable commands = new CommandTable(this::countSelection);

    /** The selected tab's selection, counted once each time the commands are refreshed. */
    private Selection selection = Selection.NONE;

    private final Shortcuts shortcuts;

    private JMenu openRecentFileMenu;

    private boolean quickKeyMode = false;

    private final JTabbedPane tabbedPane = new JTabbedPane();

    /**
     * Fills the main window with the menu bar and an empty spreadsheet. The caller places and shows it.
     *
     * @param app   The running application.
     * @param frame The main window.
     */
    public DatavyuView(final Datavyu app, final JFrame frame) {
        this.app = app;
        this.frame = frame;
        frame.setName("mainFrame");
        frame.setTitle(Resources.of(Datavyu.class).getString("Application.title"));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        defineCommands();
        shortcuts = createShortcuts();
        frame.setJMenuBar(createMenuBar());
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(shortcuts);

        frame.setGlassPane(new TransparentPanel());
        frame.getGlassPane().setVisible(true);

        tabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                JTabbedPane tabbedPane = (JTabbedPane) e.getSource();

                for (Component tab : tabbedPane.getComponents()) {
                    if (tab instanceof SpreadSheetPanel sheet) {
                        sheet.getVideoController().stopAction();
                        if (sheet.getMediaPlayers() != null) {
                            sheet.getMediaPlayers().setShowing(false);
                        }
                    }
                }

                if (tabbedPane.getComponentCount() > 0 && tabbedPane.getSelectedIndex() >= 0) {

                    SpreadSheetPanel spreadSheetPanel = (SpreadSheetPanel) tabbedPane.getSelectedComponent();
                    spreadSheetPanel.revalidate();
                    tabbedPane.revalidate();
                    if (spreadSheetPanel.getMediaPlayers() != null) {
                        spreadSheetPanel.getMediaPlayers().setShowing(true);
                    }

                    updateTitle();
                    refreshCommands();
                    if (codeEditor != null) {
                        codeEditor.setProjectController(spreadSheetPanel.getProjectController());
                    }
                }
            }
        });

        frame.getContentPane().add(tabbedPane, BorderLayout.CENTER);

        addTab(new ProjectController());
        refreshCommands();

        WindowListener exitListener = new WindowAdapter() {
            @Override
            public void windowIconified(WindowEvent e) {
                frame.setState(WindowEvent.WINDOW_ICONIFIED);
                SpreadSheetPanel panel = getSpreadsheetPanel();
                if (panel != null && panel.getMediaPlayers() != null) {
                    panel.getMediaPlayers().setShowing(false);
                }
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
                frame.setState(WindowEvent.WINDOW_DEICONIFIED);
                SpreadSheetPanel panel = getSpreadsheetPanel();
                if (panel != null && panel.getMediaPlayers() != null) {
                    panel.getMediaPlayers().setShowing(true);
                }
            }

            @Override
            public void windowClosing(WindowEvent e) {
                app.exit();
            }
        };
        frame.addWindowListener(exitListener);
    }

    /**
     * Makes a tab for a spreadsheet, with its playback, autosave and view, and selects it.
     *
     * @param pc the spreadsheet
     * @return its view
     */
    private SpreadSheetPanel addTab(final ProjectController pc) {
        pc.setVideoController(new VideoController(pc, frame));
        pc.setTitleListener(() -> SwingUtilities.invokeLater(() -> updateTitle(pc)));
        pc.setQuickKeyMode(this::isQuickKeyMode);
        pc.addUndoableEditListener(event -> refreshCommands());

        SpreadSheetPanel panel = new SpreadSheetPanel(pc, frame);
        panel.registerListeners();
        panel.addFileDropEventListener(this);
        tabbedPane.add(panel);
        tabbedPane.setTabComponentAt(tabbedPane.indexOfComponent(panel),
            new TabWithCloseButton(tabbedPane, this::closeTab));
        tabbedPane.setSelectedComponent(panel);
        pc.startAutoSave(AUTOSAVE_MINUTES);
        return panel;
    }

    /**
     * Takes a spreadsheet's tab away, once its work is saved or knowingly discarded.
     *
     * @param panel the spreadsheet's view
     */
    void removeTab(final SpreadSheetPanel panel) {
        tabbedPane.remove(panel);
        panel.close();
    }

    /** Returns the selected tab's spreadsheet, or null when no tab is open. */
    public ProjectController selectedController() {
        SpreadSheetPanel panel = getSpreadsheetPanel();
        return panel == null ? null : panel.getProjectController();
    }

    /**
     * Shows a warning over the main window.
     *
     * @param message the warning
     */
    private void warn(final String message) {
        Dialogs.warn(frame, message);
    }

    /**
     * Returns the main window.
     */
    public JFrame getFrame() {
        return frame;
    }

    /**
     * Returns the selected spreadsheet.
     */
    public JComponent getComponent() {
        return (JComponent) tabbedPane.getSelectedComponent();
    }


    public void checkForAutosavedFile() {
        // Autosaves are written to the temporary folder, and one left there is from a run that did not quit cleanly.
        File[] listOfFiles = new File(System.getProperty("java.io.tmpdir")).listFiles();
        if (listOfFiles == null) {
            return;
        }
        for (File f : listOfFiles) {
            if (f.isFile() && FilenameUtils.wildcardMatchOnSystem(f.getName(), "~*" + ProjectDocument.EXTENSION)) {
                // The autosave is deleted once it has opened, or if the user does not want it.
                if (JOptionPane.showConfirmDialog(null,
                        "Watavyu quit before \"" + recoveredName(f.getName())
                            + "\" was saved. Recover the unsaved project?",
                        "Watavyu",
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    if (recover(f)) {
                        deleteAutosave(f);
                        saveAs();
                    }
                } else {
                    deleteAutosave(f);
                }
            }
        }
    }

    /** Deletes an autosave that was recovered or declined, and logs it if the file stays. */
    private static void deleteAutosave(final File autosave) {
        if (!autosave.delete() && autosave.exists()) {
            logger.warn("Could not delete the autosave " + autosave);
        }
    }


    /**
     * Update the title of the application.
     */
    public void updateTitle() {
        ProjectController selected = selectedController();
        if (selected != null) {
            updateTitle(selected);
        }
    }

    /**
     * Shows a spreadsheet's name and whether it has unsaved changes on its tab, and on the window if it is selected.
     *
     * @param projectController the spreadsheet
     */
    private void updateTitle(final ProjectController projectController) {
        SpreadSheetPanel sheet = projectController.getSpreadSheetPanel();
        int index = tabbedPane.indexOfComponent(sheet);
        if (index < 0) {
            return;
        }
        // Show the project name instead of database.
        Resources resourceMap = Resources.of(Datavyu.class);
        String postFix = projectController.isChanged() ? "  *" : "  ";
        String extension = "";
        final FileFilter lastSaveOption = projectController.getLastSaveOption();

        if (lastSaveOption instanceof ProjectFileFilter) {
            extension = ProjectDocument.EXTENSION;
        }

        String projectName = projectController.getProjectName();
        projectName = (projectName == null) ? "untitled" : projectName;

        String title = windowTitle(resourceMap.getString("Application.title"), projectName + extension + postFix,
                isQuickKeyMode());
        String tabTitle = projectName + postFix;

        projectController.getProject().setDatabaseFileName(projectName + extension);
        sheet.setName(tabTitle);
        tabbedPane.setTitleAt(index, tabTitle);

        if (sheet.equals(tabbedPane.getSelectedComponent())) {
            frame.setTitle(title);
        }
    }


    /**
     * Returns the main window's title.
     * @param applicationTitle The application's name.
     * @param document         The project's file name and changed marker.
     * @param quickKeyMode     Whether quick-key mode is on. Highlight-and-focus mode is on by default, so its state is
     *                         shown only on its button and menu item.
     */
    static String windowTitle(final String applicationTitle, final String document, final boolean quickKeyMode) {
        String title = applicationTitle + " - " + document;
        return quickKeyMode ? title + " <QUICK KEY MODE>" : title;
    }

    /**
     * Action for creating a new project.
     */
    public void showNewProjectForm() {
        WindowPlacement.show(new NewProjectV(frame, true, this));
    }

    /**
     * Action for closing current tab
     */
    public void closeTab() {
        closeTab(getTabbedPane().getSelectedComponent());
    }

    /**
     * Closes a tab, after asking about its unsaved work, and quits once no tab is left.
     *
     * @param tab the tab's spreadsheet
     */
    private void closeTab(final Component tab) {
        if (app.safeQuit(tab)) {
            removeTab((SpreadSheetPanel) tab);
        }
        if (getTabbedPane().getTabCount() == 0) {
            app.exit();
        }
    }

    /**
     * Action for saving the current project. A project not yet saved as a project file, such as a recovered one, is
     * saved through {@link #saveAs()}.
     *
     * @return whether the project was saved
     */
    public boolean save() {
        ProjectController projectController = selectedController();
        if (projectController.isNewProject() || (projectController.getProjectName() == null)
                || !(projectController.getLastSaveOption() instanceof ProjectFileFilter)) {
            return saveAs();
        }
        return saveProject(new File(projectController.getProjectDirectory(), projectController.getDatabaseFileName()));
    }

    /**
     * Action for saving the current project as a particular project file.
     *
     * @return whether the project was saved
     */
    public boolean saveAs() {
        DatavyuFileChooser fileChooser = new DatavyuFileChooser();

        fileChooser.addChoosableFileFilter(ProjectFileFilter.INSTANCE);

        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setFileFilter(ProjectFileFilter.INSTANCE);

        int result = fileChooser.showSaveDialog(getComponent());

        boolean saved = false;
        if (result == JFileChooser.APPROVE_OPTION) {
            String fileName = fileChooser.getSelectedFile().getName();
            if (!fileName.endsWith(ProjectDocument.EXTENSION)) {
                fileName = fileName.concat(ProjectDocument.EXTENSION);
            }
            String folder = fileChooser.getSelectedFile().getParent();

            // Only save if the project file does not exist or the user confirms overwriting it.
            saved = canSave(folder, fileName) && saveProject(new File(folder, fileName));
            if (saved) {
                ProjectController projectController = selectedController();
                projectController.setProjectName(fileName);
                projectController.setProjectDirectory(folder);
                projectController.setDatabaseFileName(fileName);
                projectController.setLastSaveOption(ProjectFileFilter.INSTANCE);
                projectController.getDataStore().setName(fileName);
            }
        }
        updateTitle();
        return saved;
    }

    /**
     * Saves the current project to a project file.
     *
     * @param file the project file
     * @return whether it was saved
     */
    private boolean saveProject(final File file) {
        ProjectController projectController = selectedController();
        try {
            new SaveController().saveProject(file, projectController.toDocument(file.getAbsoluteFile().getParentFile()
                .toPath()));
            projectController.markSaved();
            return true;
        } catch (UserWarningException e) {
            logger.error("Save failed. Error: ", e);
            warn(e.getMessage());
            return false;
        }
    }

    public void toggleQuickKeys() {
        quickKeyMode = !quickKeyMode;
        updateTitle();
    }

    public void toggleHighlightAndFocusMode() {
        ProjectController selected = selectedController();
        selected.getVideoController().toggleCellHighlightingAutoFocus();
        selected.getSpreadSheetPanel().redrawCells();
    }

    public boolean isQuickKeyMode() {
        return quickKeyMode;
    }


    /**
     * Action for exporting the current SpreadSheet as a JSON File
     */
    public void exportToJSON(){
        DatavyuFileChooser fileChooser = new DatavyuFileChooser();

        fileChooser.addChoosableFileFilter(JSONFilter.INSTANCE);
        fileChooser.setFileFilter(JSONFilter.INSTANCE);

        int result = fileChooser.showSaveDialog(getComponent());

        if (result == JFileChooser.APPROVE_OPTION){
            exportJSON(fileChooser);;
        }
    }

    private void exportJSON(DatavyuFileChooser fc){
        ProjectController projectController = selectedController();

        try{
            ExportDatabaseFileController exportJSON = new ExportDatabaseFileController();

            String dbFileName = fc.getSelectedFile().getPath();
            if (!dbFileName.endsWith(".json")) {
                dbFileName = dbFileName.concat(".json");
            }

            // Only save if the project file does not exists or if the user
            // confirms a file overwrite in the case that the file exists.
            if (!canSave(fc.getSelectedFile().getParent(), dbFileName)) {
                return;
            }

            exportJSON.exportAsJSON(dbFileName, projectController.getDataStore());
        } catch (UserWarningException e) {
            logger.error("Failed export to JSON. Error: ", e);
            warn(e.getMessage());
        }

    }

    /**
     * Action for exposing the current SpreadSheet as a JSON File
     */
    public void importJSONToSpreadsheet(){
        DatavyuFileChooser fileChooser = new DatavyuFileChooser();

        fileChooser.addChoosableFileFilter(JSONFilter.INSTANCE);
        fileChooser.setFileFilter(JSONFilter.INSTANCE);

        int result = fileChooser.showOpenDialog(getComponent());

        if (result == JFileChooser.APPROVE_OPTION){
            importJSON(fileChooser);
        }

        getSpreadsheetPanel().redrawCells();
    }

    private void importJSON(DatavyuFileChooser fc){
        try {
            new OpenDataStoreFileController(this::warn)
                    .importJSONToSpreadsheet(fc.getSelectedFile(), getSpreadsheetPanel().getDataStore());
        } catch (UserWarningException | IOException e) {
            logger.error("Failed import from JSON. Error: ", e);
            warn(e.getMessage());
        }
    }



    private boolean canSave(final String directory, final String file) {
        File newFile = new File(directory, file);
        return (newFile.exists() && overwriteExisting()) || !newFile.exists();
    }

    /**
     * Asks whether to save over a file that exists.
     *
     * @return whether to overwrite it
     */
    private boolean overwriteExisting() {
        Resources resourceMap = Resources.of(Datavyu.class);
        return Dialogs.confirm(frame, resourceMap.getString("OverwriteDialog.message"),
                resourceMap.getString("OverwriteDialog.title"), "Overwrite");
    }

    /**
     * Loading a Datavyu project
     */
    public void open() {

        DatavyuFileChooser fileChooser = new DatavyuFileChooser();

        fileChooser.addChoosableFileFilter(ProjectFileFilter.INSTANCE);

        fileChooser.setFileFilter(ProjectFileFilter.INSTANCE);
        int result = fileChooser.showOpenDialog(getComponent());

        if (result == JFileChooser.APPROVE_OPTION) {
            open(fileChooser.getSelectedFile());
        }
    }

    /**
     * Simulate loading an Datavyu project from file chooser
     */
    public void open(final File file) {
        logger.info("Opening " + file.getAbsolutePath());
        DatavyuFileChooser fc = new DatavyuFileChooser();
        fc.setVisible(false);
        fc.setSelectedFile(file);

        try {
            if (checkIfFileAlreadyOpen(file.getCanonicalPath())) {
                // TODO: Replace by a language specific error message!
                warn("Error: File is already open.");
                return;
            }
        } catch (IOException e) {
            logger.error("Error when opening file: ", e);
        }

        FileFilter filter = filterFor(file);
        if (filter != null) {
            fc.setFileFilter(filter);
            open(fc);
        }
    }

    /**
     * @param file a file to open
     * @return the filter for its kind, judged by its name, or null if it is not a kind Watavyu opens
     */
    private static FileFilter filterFor(final File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(ProjectDocument.EXTENSION)) {
            return ProjectFileFilter.INSTANCE;
        }
        return null;
    }

    /**
     * Helper method for opening a file from disk.
     *
     * @param fileChooser The file chooser to use.
     */
    private void open(final DatavyuFileChooser fileChooser) {
        // An untitled spreadsheet with no changes gives way to the project opened.
        ProjectController selected = selectedController();
        SpreadSheetPanel replaced = tabbedPane.getTabCount() == 1 && selected != null
            && selected.getProjectName() == null && !selected.isChanged() ? selected.getSpreadSheetPanel() : null;
        progressBar = new DataviewProgressBar(this.getFrame(), false);
        task = new OpenTask(fileChooser);
        task.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("progress".equals(evt.getPropertyName())) {
                    int val = (Integer) evt.getNewValue();
                    String msg = switch (val) {
                        case 0 -> "Preparing spreadsheet";
                        case 10 -> "Opening project";
                        case 40 -> "Project opened";
                        case 50 -> "Loading project into spreadsheet";
                        case 100 -> "Completed!";
                        default -> "Error loading project!";
                    };

                    progressBar.setProgress(val, msg);
                }
            }
        });
        task.execute();
        try {
            ProjectController p = task.get();
            if (p != null) {
                createNewSpreadsheet(p);
                if (replaced != null) {
                    removeTab(replaced);
                }
            }

            progressBar.close();
        } catch (ExecutionException e) {
            logger.error("Failed opening project. Error: ", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted opening a project", e);
        }
    }

    public void openExternalFile(final File f) {
        DatavyuFileChooser fileChooser = new DatavyuFileChooser();
        fileChooser.setSelectedFile(f);
        FileFilter filter = filterFor(f);
        fileChooser.setFileFilter(filter != null ? filter : ProjectFileFilter.INSTANCE);
        open(fileChooser);
    }

    /**
     * Opens an autosaved project in a new tab as an unsaved project named after the original, so that saving it goes
     * through Save As and quitting asks about it.
     *
     * @param autosave the autosaved file
     * @return whether it opened
     */
    private boolean recover(final File autosave) {
        open(autosave);
        ProjectController projectController = selectedController();
        if (projectController == null || !autosave.getName().equals(projectController.getDatabaseFileName())) {
            return false;
        }
        projectController.setLastSaveOption(null);
        projectController.setProjectName(recoveredName(autosave.getName()));
        projectController.projectChanged();
        return true;
    }

    /**
     * Returns the project's name.
     * @param autosave the name of an autosaved file: {@code ~}, the project's name, {@code _} and a number, then the
     *                 extension
     */
    static String recoveredName(final String autosave) {
        String name = ProjectPaths.isProjectFile(autosave)
            ? autosave.substring(0, autosave.length() - ProjectDocument.EXTENSION.length())
            : FilenameUtils.removeExtension(autosave);
        return name.replaceFirst("^~", "").replaceFirst("_\\d+$", "");
    }

    private OpenController openProjectFile(final File projectFile) {
        OpenController openController = new OpenController();
        openController.openProjectFile(projectFile);
        return openController;
    }

    /**
     * Handles the event for files being dropped onto a component. Only the
     * first file received will be opened.
     *
     * @param evt The event to handle.
     */
    @Override
    public void filesDropped(final FileDropEvent evt) {
        // Media dropped on the spreadsheet joins the project; anything else opens as a project.
        List<File> media = new ArrayList<>();
        List<File> projects = new ArrayList<>();
        for (File file : evt.getFiles()) {
            (JavaFxVideoPlugin.isMediaFile(file) ? media : projects).add(file);
        }
        addMediaFiles(media);

        if (projects.isEmpty() || !app.safeQuit()) {
            return;
        }

        DatavyuFileChooser fileChooser = new DatavyuFileChooser();
        fileChooser.setVisible(false);

        for (File file : projects) {
            open(file);
        }
    }


    /**
     * Action for adding media: chooses media files and adds them, as clips at the current time when one media column
     * is selected, or each as a new media column.
     */
    public void addMedia() {
        DatavyuFileChooser chooser = new DatavyuFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.addChoosableFileFilter(JavaFxVideoPlugin.VIDEO_FILE_FILTER);
        chooser.setFileFilter(JavaFxVideoPlugin.VIDEO_FILE_FILTER);
        if (chooser.showOpenDialog(getFrame()) == JFileChooser.APPROVE_OPTION) {
            addMediaFiles(List.of(chooser.getSelectedFiles()));
        }
    }

    private void addMediaFiles(final List<File> files) {
        SpreadSheetPanel panel = getSpreadsheetPanel();
        if (files.isEmpty() || panel == null) {
            return;
        }
        try {
            selectedController().media().addMedia(files, panel.getVideoController().getCurrentTime(),
                locked -> Dialogs.confirm(getFrame(), locked.getName()
                    + " is locked, so no clips can be added to it. Add the media to a new media column instead?",
                    "Column Locked", "Add to New Column"));
        } catch (UserWarningException e) {
            logger.error("Unable to add media", e);
            warn(e.getMessage());
        }
    }

    /**
     * Action for editing vocabs.
     */
    public void showVocabEditor() {
        if (codeEditor == null) {
            codeEditor = new VocabEditorV(frame, false, selectedController());
        } else {
            codeEditor.setProjectController(selectedController());
        }
        WindowPlacement.show(codeEditor);
    }

    /**
     * Action for showing the variable list.
     */
    public void showVariableList() {
        VariableListV list = new VariableListV(frame, false, selectedController());
        list.registerListeners();
        WindowPlacement.show(list);
    }

    /**
     * Action for showing the quicktime video controller.
     */
    public void toggleControls() {
        boolean show = !Settings.get().isShowControls();
        Settings.get().setShowControls(show);
        for (Component tab : tabbedPane.getComponents()) {
            if (tab instanceof SpreadSheetPanel spreadSheetPanel) {
                spreadSheetPanel.setControlsShown(show);
            }
        }
    }

    /**
     * Action for running the playhead's line across the spreadsheet's columns, or only down its own strip.
     */
    public void togglePlayheadAcrossColumns() {
        boolean across = !Settings.get().isPlayheadAcrossColumns();
        Settings.get().setPlayheadAcrossColumns(across);
        for (Component tab : tabbedPane.getComponents()) {
            if (tab instanceof SpreadSheetPanel spreadSheetPanel && spreadSheetPanel.getPlayhead() != null) {
                spreadSheetPanel.getPlayhead().refresh();
            }
        }
    }

    /**
     * Gives the spreadsheet open at launch the empty media columns a new spreadsheet starts with. Its columns can be
     * made only once the application has its view and video controller, after this view is made.
     */
    public void startFirstSpreadsheet() {
        startNewSpreadsheet(selectedController().getDataStore());
    }

    /**
     * Gives a new spreadsheet the empty media columns it starts with, as set in the settings.
     *
     * @param dataStore the new spreadsheet
     */
    private static void startNewSpreadsheet(final DataStore dataStore) {
        try {
            MediaColumns.startNewSpreadsheet(dataStore, Settings.get().getNewSpreadsheetMediaColumns());
        } catch (UserWarningException e) {
            logger.error("Unable to add the new spreadsheet's media columns", e);
        }
    }

    /**
     * Action for adding an empty media column, for media added later.
     */
    public void newMediaColumn() {
        try {
            selectedController().columns().newMediaColumn();
        } catch (UserWarningException e) {
            logger.error("Unable to add a media column", e);
            warn(e.getMessage());
        }
    }

    /**
     * Action for showing the about window.
     */
    public void showAboutWindow() {
        app.showAboutWindow();
    }

    /**
     * Action for showing how to cite Watavyu.
     */
    public void showCitationDialog() {
        WindowPlacement.show(new CitationDialog(getFrame()));
    }

    /**
     * Opens a spreadsheet in a new tab, and opens its media.
     *
     * @param pc the spreadsheet
     * @return the spreadsheet
     */
    public ProjectController createNewSpreadsheet(ProjectController pc) {
        // A new tab takes the layout of the one selected before it.
        SheetLayoutType layout = getSpreadsheetPanel() == null ? SheetLayoutFactory.DEFAULT_LAYOUT
            : getSpreadsheetPanel().getLayoutType();
        SpreadSheetPanel panel = addTab(pc);
        panel.clearCellSelection();
        panel.setLayoutType(layout);
        pc.loadProject();
        // Selecting the tab refreshed the commands before it had its layout and project.
        commands.refresh();
        return pc;
    }

    public ProjectController createNewSpreadsheet(String name) {
        ProjectController pc = new ProjectController();
        DataStore dataStore = DataStoreFactory.newDataStore();
        startNewSpreadsheet(dataStore);
        pc.setDataStore(dataStore);
        pc.setProjectName(name);
        return createNewSpreadsheet(pc);
    }

    /**
     * Action for removing cells from the database.
     */
    public void deleteCells() {
        selectedController().cells().deleteSelected();
    }

    /** Switches the selected spreadsheet between temporal and ordinal layout. */
    private void toggleTemporalLayout() {
        SpreadSheetPanel panel = getSpreadsheetPanel();
        panel.setLayoutType(panel.getLayoutType() == SheetLayoutType.WeakTemporal ? SheetLayoutType.Ordinal
            : SheetLayoutType.WeakTemporal);
    }

    public boolean checkIfFileAlreadyOpen(String filepath) {
        for (Component tab : tabbedPane.getComponents()) {
            if (tab instanceof SpreadSheetPanel sp && filepath.equals(sp.getProjectController().getFullPath())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Undo Action.
     */
    private void undo() {
        String refusal = selectedController().undo();
        if (refusal != null) {
            warn(refusal);
        }
        redrawSheet();
    }

    /**
     * Redo Action.
     */
    private void redo() {
        String refusal = selectedController().redo();
        if (refusal != null) {
            warn(refusal);
        }
        redrawSheet();
    }



    public void zoomIn() {
        changeFontSize(ZOOM_INTERVAL);
    }

    public void zoomOut() {
        changeFontSize(-ZOOM_INTERVAL);
    }

    /**
     * Function to reset the zoom level to the default size.
     */
    public void resetZoom() {
        changeFontSize(SpreadsheetTheme.DEFAULT_DATA_FONT_SIZE - SpreadsheetTheme.dataFont().getSize());
    }

    /**
     * Action for setting cell values in Plex Mono or Plex Sans. Cells read their font each time they paint, as zoom
     * relies on, so the sheet only needs laying out again.
     */
    public void toggleMonospacedValues() {
        boolean monospaced = !Settings.get().isMonospacedValues();
        Settings.get().setMonospacedValues(monospaced);
        SpreadsheetTheme.setMonospacedValues(monospaced);
        getSpreadsheetPanel().revalidate();
        getSpreadsheetPanel().repaint();
    }

    /**
     * Sets how far the times and values of cells the needle has passed fade, and shows every cell again.
     *
     * @param percent from 0 (unchanged) to 100 (hidden)
     */
    public void setPastCellDim(final int percent) {
        Settings.get().setPastCellDim(percent);
        for (Component tab : tabbedPane.getComponents()) {
            if (tab instanceof SpreadSheetPanel spreadSheetPanel) {
                spreadSheetPanel.refreshCellColours();
            }
        }
    }

    /** Steps offered in the Dim Past Cells menu; the settings file takes any whole number from 0 to 100. */
    private static final int[] PAST_CELL_DIM_STEPS = {0, 20, 40, 60, 80, 100};

    private JMenu pastCellDimMenu() {
        JMenu menu = new JMenu(resources.getString("pastCellDimMenu.text"));
        ButtonGroup group = new ButtonGroup();
        int current = Settings.get().getPastCellDim();
        for (int step : PAST_CELL_DIM_STEPS) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(step + "%", step == current);
            item.addActionListener(e -> setPastCellDim(step));
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    public void newCellLeft() {
        selectedController().cells().newCellsBeside(ArrayDirection.LEFT);
    }

    public void newCellRight() {
        selectedController().cells().newCellsBeside(ArrayDirection.RIGHT);
    }

    /**
     *
     *
     * Changes the font size by adding sizeDif to the current size. Then it
     * creates and revalidates a new panel to show the font update. This will
     * not make the font smaller than smallestSize.
     *
     * @param diff Add diff to current size.
     */
    public void changeFontSize(final int diff) {
        int size = SpreadsheetTheme.dataFont().getSize() + diff;
        SpreadsheetTheme.setDataFontSize(Math.max(Math.min(size, ZOOM_MAX_SIZE), ZOOM_MIN_SIZE));
        // Create and redraw fresh window pane so all of the fonts are new again.
        getSpreadsheetPanel().revalidate();
        getSpreadsheetPanel().repaint();
    }

    /**
     * Returns SpreadSheetPanel
     *
     * @return SpreadSheetPanel panel
     */
    public SpreadSheetPanel getSpreadsheetPanel() {
        return (SpreadSheetPanel) tabbedPane.getSelectedComponent();
    }

    /**
     * Shows the state of the commands, which follows the selected spreadsheet: Undo and Redo name its edits, and its
     * modes and layout are shown checked or named as they are in it (B20).
     */
    void refreshCommands() {
        commands.refresh();
        redrawSheet();
    }

    private void redrawSheet() {
        if (getSpreadsheetPanel() != null) {
            getSpreadsheetPanel().revalidate();
            getSpreadsheetPanel().repaint();
        }
    }

    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    /**
     * Returns a command's action, which its menu item, shortcut and controls share.
     *
     * @param command the command
     * @return its action
     */
    Action action(final Command command) {
        return commands.get(command);
    }

    /** Returns the main window's shortcuts, which the menu bar's accelerators and the keypad follow. */
    Shortcuts getShortcuts() {
        return shortcuts;
    }

    /**
     * Defines what each command does and the state it shows, worked out from the selected spreadsheet whenever the
     * commands are refreshed.
     */
    private void defineCommands() {
        commands.define(Command.NEW_PROJECT, this::showNewProjectForm);
        commands.define(Command.OPEN, this::open);
        commands.define(Command.CLOSE_TAB, this::closeTab);
        commands.define(Command.SAVE, this::save);
        commands.define(Command.SAVE_AS, this::saveAs);

        commands.define(Command.NEW_MEDIA_COLUMN, this::newMediaColumn);
        commands.define(Command.ADD_MEDIA, this::addMedia);
        commands.define(Command.COLUMN_LIST, this::showVariableList);
        commands.define(Command.CODE_EDITOR, this::showVocabEditor);
        commands.define(Command.NEW_CELL, this::newCell).enabledWhen(() -> {
            ProjectController selected = selectedController();
            return selected != null && selected.cells().hasColumnForNewCell();
        });
        commands.define(Command.NEW_CELL_LEFT, this::newCellLeft)
                .labelled(() -> countedLabel("newCellLeftMenuItem", selection.besideLeft()))
                .enabledWhen(() -> selection.besideLeft() > 0);
        commands.define(Command.NEW_CELL_RIGHT, this::newCellRight)
                .labelled(() -> countedLabel("newCellRightMenuItem", selection.besideRight()))
                .enabledWhen(() -> selection.besideRight() > 0);
        commands.define(Command.DELETE_CELLS, this::deleteCells)
                .labelled(() -> countedLabel("deleteCellMenuItem", selection.cells()))
                .enabledWhen(() -> selection.cells() > 0);
        commands.define(Command.SELECT_COLUMN_LEFT, () -> getSpreadsheetPanel().selectNextColumn(-1))
                .enabledWhen(() -> getSpreadsheetPanel() != null);
        commands.define(Command.SELECT_COLUMN_RIGHT, () -> getSpreadsheetPanel().selectNextColumn(1))
                .enabledWhen(() -> getSpreadsheetPanel() != null);
        commands.define(Command.UNDO, this::undo)
                .labelled(() -> history() == null ? Command.UNDO.label() : history().getUndoPresentationName())
                .enabledWhen(() -> history() != null && history().canUndo());
        commands.define(Command.REDO, this::redo)
                .labelled(() -> history() == null ? Command.REDO.label() : history().getRedoPresentationName())
                .enabledWhen(() -> history() != null && history().canRedo());
        commands.define(Command.EXPORT_JSON, this::exportToJSON).enabledWhen(() -> selection.visibleColumns());
        commands.define(Command.IMPORT_JSON, this::importJSONToSpreadsheet);
        commands.define(Command.TEMPORAL_LAYOUT, this::toggleTemporalLayout)
                .checked(() -> getSpreadsheetPanel() != null
                        && getSpreadsheetPanel().getLayoutType() == SheetLayoutType.WeakTemporal);
        commands.define(Command.ZOOM_IN, this::zoomIn);
        commands.define(Command.ZOOM_OUT, this::zoomOut);
        commands.define(Command.RESET_ZOOM, this::resetZoom);
        commands.define(Command.MONOSPACED_VALUES, this::toggleMonospacedValues)
                .checked(() -> Settings.get().isMonospacedValues());
        commands.define(Command.QUICK_KEYS, this::toggleQuickKeys).labelled(this::quickKeyModeText);
        commands.define(Command.HIGHLIGHT_AND_FOCUS, this::toggleHighlightAndFocusMode)
                .labelled(() -> highlightAndFocusText(controller() == null
                        ? VideoController.HIGHLIGHT_AND_FOCUS_BY_DEFAULT : controller().getCellHighlightAndFocus()));

        commands.define(Command.SHOW_CONTROLS, this::toggleControls).checked(() -> Settings.get().isShowControls());
        commands.define(Command.PLAYHEAD_ACROSS_COLUMNS, this::togglePlayheadAcrossColumns)
                .checked(() -> Settings.get().isPlayheadAcrossColumns());
        for (Command playback : PLAYBACK) {
            commands.define(playback, () -> {
                if (controller() != null) {
                    controller().run(playback);
                }
            });
        }

        commands.define(Command.SHORTCUTS, this::showHotkeysDialog);
        commands.define(Command.CITATION, this::showCitationDialog);
    }

    /** The commands of the controls drawer and the keypad, which the selected spreadsheet's playback runs. */
    private static final List<Command> PLAYBACK = List.of(Command.SET_ONSET, Command.PLAY, Command.SET_OFFSET,
            Command.SHUTTLE_BACK, Command.STOP, Command.SHUTTLE_FORWARD, Command.JOG_BACK, Command.PAUSE,
            Command.JOG_FORWARD, Command.NEW_CELL_CLOSING_PREVIOUS, Command.JUMP_BACK, Command.JUMP_FORWARD,
            Command.FIND_ONSET, Command.FIND_OFFSET, Command.NEW_CELL_IN_SELECTED_COLUMN);

    /** Returns the selected spreadsheet's undo history, or null when no tab is open. */
    private SpreadsheetUndoManager history() {
        ProjectController selected = selectedController();
        return selected == null ? null : selected.getUndoManager();
    }

    /**
     * What the cell commands' state is worked out from, counted in one pass over the cells per refresh, which runs
     * before every key.
     *
     * @param visibleColumns whether the selected tab shows any column
     * @param cells          how many cells are selected, all in shown columns, as hiding deselects
     * @param besideLeft     how many selected cells have a shown column to their left
     * @param besideRight    how many have a shown column to their right
     */
    private record Selection(boolean visibleColumns, int cells, int besideLeft, int besideRight) {
        static final Selection NONE = new Selection(false, 0, 0, 0);
    }

    private void countSelection() {
        SpreadSheetPanel panel = getSpreadsheetPanel();
        if (panel == null) {
            selection = Selection.NONE;
            return;
        }
        List<Variable> shown = panel.getDataStore().getVisibleVariables();
        int cells = 0;
        int left = 0;
        int right = 0;
        for (Variable column : panel.getDataStore().getAllVariables()) {
            int place = shown.indexOf(column);
            int selected = (int) column.getCells().stream().filter(Cell::isSelected).count();
            cells += selected;
            left += place > 0 ? selected : 0;
            right += place >= 0 && place < shown.size() - 1 ? selected : 0;
        }
        selection = new Selection(!shown.isEmpty(), cells, left, right);
    }

    /** Returns a command's label for one cell or for several, such as Delete Cell or Delete Cells. */
    private String countedLabel(final String key, final int count) {
        return resources.getString(key + (count > 1 ? "Plural" : "Single") + ".text");
    }

    /** @return the selected spreadsheet's playback, which the keypad drives; null when no tab is open */
    private VideoController controller() {
        ProjectController selected = selectedController();
        return selected == null ? null : selected.getVideoController();
    }

    /**
     * @return Every keyboard shortcut of the application.
     */
    private Shortcuts createShortcuts() {
        final int cmd = Shortcuts.COMMAND;
        final int shift = InputEvent.SHIFT_DOWN_MASK;
        return Shortcuts.builder(commands::get)
                .menu("File", Command.NEW_PROJECT, KeyStroke.getKeyStroke(KeyEvent.VK_N, cmd))
                .menu("File", Command.OPEN, KeyStroke.getKeyStroke(KeyEvent.VK_O, cmd))
                .menu("File", Command.CLOSE_TAB, KeyStroke.getKeyStroke(KeyEvent.VK_W, cmd))
                .menu("File", Command.SAVE, KeyStroke.getKeyStroke(KeyEvent.VK_S, cmd))
                .menu("File", Command.SAVE_AS, KeyStroke.getKeyStroke(KeyEvent.VK_S, cmd | shift))
                .global("Spreadsheet", Command.NEW_CELL_LEFT, KeyStroke.getKeyStroke(KeyEvent.VK_L, cmd))
                .global("Spreadsheet", Command.NEW_CELL_RIGHT, KeyStroke.getKeyStroke(KeyEvent.VK_R, cmd))
                // Run first: a cell's text takes ⌘\ to unselect.
                .global("Spreadsheet", Command.DELETE_CELLS, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, cmd))
                .menu("Spreadsheet", Command.UNDO, KeyStroke.getKeyStroke(KeyEvent.VK_Z, cmd))
                .menu("Spreadsheet", Command.REDO, KeyStroke.getKeyStroke(KeyEvent.VK_Y, cmd))
                .menu("Spreadsheet", Command.TEMPORAL_LAYOUT, KeyStroke.getKeyStroke(KeyEvent.VK_T, cmd))
                .global("Spreadsheet", Command.ZOOM_IN, KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, cmd))
                .global("Spreadsheet", Command.ZOOM_OUT, KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, cmd))
                .menu("Spreadsheet", Command.RESET_ZOOM, KeyStroke.getKeyStroke(KeyEvent.VK_0, cmd))
                .menu("Spreadsheet", Command.QUICK_KEYS, KeyStroke.getKeyStroke(KeyEvent.VK_K, cmd | shift))
                .menu("Spreadsheet", Command.HIGHLIGHT_AND_FOCUS, KeyStroke.getKeyStroke(KeyEvent.VK_F, cmd | shift))
                .global("Spreadsheet", Command.SELECT_COLUMN_LEFT, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, cmd))
                .global("Spreadsheet", Command.SELECT_COLUMN_RIGHT, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, cmd))
                .keypad(Command.SHOW_CONTROLS, KeyEvent.VK_MULTIPLY, 0)
                .keypad(Command.SET_ONSET, KeyEvent.VK_NUMPAD7, 0)
                .keypad(Command.PLAY, KeyEvent.VK_NUMPAD8, 0)
                .keypad(Command.SET_OFFSET, KeyEvent.VK_NUMPAD9, 0)
                .keypad(Command.SHUTTLE_BACK, KeyEvent.VK_NUMPAD4, 0)
                .keypad(Command.STOP, KeyEvent.VK_NUMPAD5, 0)
                .keypad(Command.SHUTTLE_FORWARD, KeyEvent.VK_NUMPAD6, 0)
                .keypad(Command.JOG_BACK, KeyEvent.VK_NUMPAD1, 0)
                .keypad(Command.PAUSE, KeyEvent.VK_NUMPAD2, 0)
                .keypad(Command.JOG_FORWARD, KeyEvent.VK_NUMPAD3, 0)
                .keypad(Command.NEW_CELL_CLOSING_PREVIOUS, KeyEvent.VK_NUMPAD0, 0)
                .keypad(Command.JUMP_BACK, KeyEvent.VK_SUBTRACT, 0)
                .keypad(Command.JUMP_FORWARD, KeyEvent.VK_SUBTRACT, shift)
                .keypad(Command.FIND_ONSET, KeyEvent.VK_ADD, 0)
                .keypad(Command.FIND_OFFSET, KeyEvent.VK_ADD, shift)
                .keypad(Command.NEW_CELL_IN_SELECTED_COLUMN, KeyEvent.VK_ENTER, 0)
                .build(() -> frame.isFocused(), held -> {
                    if (controller() != null) {
                        controller().setShiftMask(held);
                    }
                }, commands::refresh);
    }

    /** Makes a menu item for a command, with its shortcut. */
    private JMenuItem item(final Command command) {
        return withAccelerator(new JMenuItem(commands.get(command)), command);
    }

    /** Makes a check box menu item for a command, with its shortcut. */
    private JCheckBoxMenuItem checkItem(final Command command) {
        return withAccelerator(new JCheckBoxMenuItem(commands.get(command)), command);
    }

    private <T extends JMenuItem> T withAccelerator(final T item, final Command command) {
        for (Shortcuts.Shortcut shortcut : shortcuts.all()) {
            if (shortcut.getScope() != Shortcuts.Scope.KEYPAD
                    && shortcut.getCommand() == command) {
                item.setAccelerator(shortcut.getKey());
            }
        }
        return item;
    }

    /**
     * @return The menu bar, whose items run the commands defined by {@link #defineCommands()}.
     */
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setFocusable(false);

        JMenu fileMenu = new JMenu(resources.getString("fileMenu.text"));
        fileMenu.add(item(Command.NEW_PROJECT));
        fileMenu.add(item(Command.OPEN));
        fileMenu.add(item(Command.CLOSE_TAB));
        openRecentFileMenu = new JMenu(resources.getString("openRecentFileMenu.text"));
        openRecentFileMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(final MenuEvent e) {
                populateRecentFiles();
            }

            @Override
            public void menuDeselected(final MenuEvent e) {
            }

            @Override
            public void menuCanceled(final MenuEvent e) {
            }
        });
        fileMenu.add(openRecentFileMenu);
        fileMenu.addSeparator();
        fileMenu.add(item(Command.SAVE));
        fileMenu.add(item(Command.SAVE_AS));
        menuBar.add(fileMenu);

        JMenu spreadsheetMenu = new JMenu(resources.getString("spreadsheetMenu.text"));
        spreadsheetMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(final MenuEvent e) {
                // Delete Cells and New Cell to Left and Right follow the selection.
                commands.refresh();
            }

            @Override
            public void menuDeselected(final MenuEvent e) {
            }

            @Override
            public void menuCanceled(final MenuEvent e) {
            }
        });
        spreadsheetMenu.add(item(Command.NEW_MEDIA_COLUMN));
        spreadsheetMenu.add(item(Command.ADD_MEDIA));
        spreadsheetMenu.add(item(Command.COLUMN_LIST));
        spreadsheetMenu.add(item(Command.CODE_EDITOR));
        spreadsheetMenu.addSeparator();
        spreadsheetMenu.add(item(Command.NEW_CELL));
        spreadsheetMenu.add(item(Command.NEW_CELL_LEFT));
        spreadsheetMenu.add(item(Command.NEW_CELL_RIGHT));
        spreadsheetMenu.addSeparator();
        spreadsheetMenu.add(item(Command.DELETE_CELLS));
        spreadsheetMenu.addSeparator();
        spreadsheetMenu.add(item(Command.UNDO));
        spreadsheetMenu.add(item(Command.REDO));
        spreadsheetMenu.addSeparator();
        spreadsheetMenu.add(item(Command.EXPORT_JSON));
        spreadsheetMenu.add(item(Command.IMPORT_JSON));
        spreadsheetMenu.addSeparator();
        spreadsheetMenu.add(checkItem(Command.TEMPORAL_LAYOUT));
        JMenu zoomMenu = new JMenu(resources.getString("zoomMenu.text"));
        zoomMenu.add(item(Command.ZOOM_IN));
        zoomMenu.add(item(Command.ZOOM_OUT));
        zoomMenu.addSeparator();
        zoomMenu.add(item(Command.RESET_ZOOM));
        spreadsheetMenu.add(zoomMenu);
        spreadsheetMenu.add(checkItem(Command.MONOSPACED_VALUES));
        spreadsheetMenu.add(pastCellDimMenu());
        JMenuItem quickKeysItem = spreadsheetMenu.add(item(Command.QUICK_KEYS));
        quickKeysItem.setToolTipText(resources.getString("quickKeysMenuItem.toolTipText"));
        JMenuItem highlightAndFocusItem = spreadsheetMenu.add(item(Command.HIGHLIGHT_AND_FOCUS));
        highlightAndFocusItem.setToolTipText(resources.getString("highlightAndFocusMenuItem.toolTipText"));
        menuBar.add(spreadsheetMenu);

        JMenu playbackMenu = new JMenu(resources.getString("playbackMenu.text"));
        playbackMenu.add(checkItem(Command.SHOW_CONTROLS));
        playbackMenu.add(checkItem(Command.PLAYHEAD_ACROSS_COLUMNS));
        menuBar.add(playbackMenu);

        JMenu helpMenu = new JMenu(resources.getString("helpMenu.text"));
        helpMenu.add(item(Command.SHORTCUTS));
        helpMenu.add(item(Command.CITATION));
        menuBar.add(helpMenu);
        return menuBar;
    }

    private void populateRecentFiles() {
        openRecentFileMenu.removeAll();
        JMenuItem header = new JMenuItem(resources.getString("recentFilesHeader.text"));
        header.setEnabled(false);
        openRecentFileMenu.add(header);
        for (File file : Settings.get().getRecentProjects()) {
            JMenuItem item = new JMenuItem(file.toString());
            item.addActionListener(e -> open(file));
            openRecentFileMenu.add(item);
        }
    }

    private String quickKeyModeText() {
        return quickKeyMode ? "Disable Quick Key Mode" : "Enable Quick Key Mode";
    }

    private static String highlightAndFocusText(final boolean on) {
        return on ? "Disable Highlight and Focus Mode" : "Enable Highlight and Focus Mode";
    }

    /** Creates a cell at the current time in the column last given a cell or edited, as the New Cell menu item does. */
    public void newCell() {
        ProjectController selected = selectedController();
        selected.cells().newCell(selected.getVideoController().getCurrentTime());
    }

    /**
     * Shows the keyboard shortcuts.
     */
    public void showHotkeysDialog() {
        DefaultTableModel model = new NoEditTableModel();
        model.addColumn("Where");
        model.addColumn("Command");
        model.addColumn("Key");
        for (Shortcuts.Shortcut shortcut : shortcuts.all()) {
            model.addRow(new Object[]{shortcut.getGroup(), shortcut.getName(), shortcut.getKeyText()});
        }
        JTable table = new JTable(model);
        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getColumnModel().getColumn(1).setPreferredWidth(380);
        table.getColumnModel().getColumn(2).setPreferredWidth(110);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(620, 520));
        JOptionPane.showMessageDialog(frame, scrollPane, "Keyboard Shortcuts", JOptionPane.PLAIN_MESSAGE);
    }

    class OpenTask extends SwingWorker<ProjectController, Void> {
        private DatavyuFileChooser fileChooser;

        public OpenTask(final DatavyuFileChooser fileChooser) { this.fileChooser = fileChooser; }

        @Override
        public ProjectController doInBackground() {

            if (!fileChooser.getSelectedFile().exists()) {
                setProgress(2);
                return null;
            }

            setProgress(0);
            FileFilter filter = fileChooser.getFileFilter();
            setProgress(10);
            OpenController openController;

            openController = openProjectFile(fileChooser.getSelectedFile());

            if (openController != null && openController.getError() != null) {
                final String error = openController.getError();
                SwingUtilities.invokeLater(() -> warn(error));
                openController = null;
            }

            if (openController == null) {
                setProgress(1);
                return null;
            }

            ProjectController pController = new ProjectController(openController.getProject(), openController.getDataStore());
            pController.setProjectName(fileChooser.getSelectedFile().getName());

            pController.setLastSaveOption(filter);
            pController.setProjectDirectory(fileChooser.getSelectedFile().getParent());
            pController.setDatabaseFileName(fileChooser.getSelectedFile().getName());

            setProgress(40);

            // Set filename in spreadsheet window and database if the database name is undefined.


            // Display any changes to the database.
            // TODO: Check if this update of the progress reflects actual time (unlike windows)?
            setProgress(50);

            // The project we just opened doesn't really contain any unsaved changes.
            pController.markProjectAsUnchanged();
            pController.getDataStore().markAsUnchanged();

            // Update the list of recently opened files.
            Settings.get().rememberProject(fileChooser.getSelectedFile());
            Settings.get().save();

            setProgress(100);

            return pController;
        }
    }

    private static class NoEditTableModel extends DefaultTableModel {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    }

}
