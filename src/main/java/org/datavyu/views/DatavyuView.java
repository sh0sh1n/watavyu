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
package org.datavyu.views;

import org.datavyu.util.Resources;
import org.datavyu.util.Settings;
import com.fasterxml.jackson.core.JsonParseException;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.Datavyu;
import org.datavyu.controllers.*;
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.event.component.FileDropEvent;
import org.datavyu.event.component.FileDropEventListener;
import org.datavyu.models.db.*;
import org.datavyu.plugins.StreamViewer;
import org.datavyu.projectfile.ProjectDocument;
import org.datavyu.undoableedits.RemoveCellEdit;
import org.datavyu.undoableedits.RemoveVariableEdit;
import org.datavyu.undoableedits.SpreadsheetUndoManager;
import org.datavyu.util.ArrayDirection;
import org.datavyu.util.DragAndDrop.TransparentPanel;
import org.datavyu.util.FileFilters.*;
import org.datavyu.views.discrete.SpreadSheetPanel;
import org.datavyu.views.discrete.SpreadsheetTheme;
import org.datavyu.views.discrete.SpreadsheetColumn;
import org.datavyu.views.discrete.layouts.SheetLayoutFactory.SheetLayoutType;

import javax.swing.*;
import javax.swing.Action;
import javax.swing.event.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.undo.UndoableEdit;
import javax.swing.undo.UndoableEditSupport;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private static Logger logger = LogManager.getLogger(DatavyuView.class);
    private static boolean redraw = true;

    /**
     * undo system elements
     */
    private SpreadsheetUndoManager spreadsheetUndoManager; // history list
    private UndoableEditSupport undoSupport; // event support

    /**
     * The spreadsheet panel for this view.
     */
    private SpreadSheetPanel panel;
    private DataviewProgressBar progressBar;
    private OpenTask task;
    /**
     * the code editor's controller
     */
    private VocabEditorController vec = VocabEditorController.getController();
    /** The main window */
    private final JFrame frame;

    /** Text of this view's menus and dialogs */
    private final Resources resources = Resources.of(DatavyuView.class);

    /** Commands of the menu bar, by name */
    private final Map<String, Action> actions = new LinkedHashMap<>();

    private final Shortcuts shortcuts;

    private JMenu openRecentFileMenu;
    private JMenuItem newCellMenuItem;
    private JMenuItem newCellLeftMenuItem;
    private JMenuItem newCellRightMenuItem;
    private JMenuItem changeVarNameMenuItem;
    private JMenuItem hideSelectedColumnsMenuItem;
    private JMenuItem deleteColumnMenuItem;
    private JMenuItem deleteCellMenuItem;
    private JMenuItem exportJSON;
    private JMenuItem importJSON;
    private JCheckBoxMenuItem weakTemporalAlignmentMenuItem;

    private boolean quickKeyMode = false;

    private JTabbedPane tabbedPane;

    /**
     * Fills the main window with the menu bar and an empty spreadsheet. The caller places and shows it.
     *
     * @param frame The main window.
     */
    public DatavyuView(final JFrame frame) {
        this.frame = frame;
        frame.setName("mainFrame");
        frame.setTitle(Resources.of(Datavyu.class).getString("Application.title"));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        createActions();
        shortcuts = createShortcuts();
        frame.setJMenuBar(createMenuBar());
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(shortcuts);

        frame.setGlassPane(new TransparentPanel());
        frame.getGlassPane().setVisible(true);

        tabbedPane = new JTabbedPane();

        tabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                JTabbedPane tabbedPane = (JTabbedPane) e.getSource();

                for (Component tab : tabbedPane.getComponents()) {
                    if (tab instanceof SpreadSheetPanel) {
                        VideoController videoController = ((SpreadSheetPanel) tab).getVideoController();

                        videoController.stopAction();
                        for (StreamViewer streamViewer : videoController.getStreamViewers()) {
                            streamViewer.setViewerVisible(false);
                        }
                        videoController.setVisible(false);
                    }
                }

                if (tabbedPane.getComponentCount() > 0 && tabbedPane.getSelectedIndex() >= 0) {

                    SpreadSheetPanel spreadSheetPanel = (SpreadSheetPanel) tabbedPane.getSelectedComponent();
                    if (Datavyu.getView() != null) {
                        Datavyu.setProjectController(spreadSheetPanel.getProjectController());

                        Datavyu.getVideoController().setVisible(false);
                        Datavyu.setVideoController(spreadSheetPanel.getVideoController());
                        Datavyu.getView().panel = spreadSheetPanel;
                        spreadSheetPanel.revalidate();
                        Datavyu.getView().tabbedPane.revalidate();

                        if (spreadSheetPanel.getVideoController().shouldBeVisible()) {
                            spreadSheetPanel.getVideoController().setVisible(true);

                            for (StreamViewer d : spreadSheetPanel.getVideoController().getStreamViewers()) {
                                d.setViewerVisible(true);
                            }
                        }
                    }

                    updateTitle();
                    vec.updateView();
                }
            }
        });

        frame.getContentPane().add(tabbedPane, BorderLayout.CENTER);

        panel = new SpreadSheetPanel(new ProjectController(), null);
        panel.getProjectController().setSpreadSheetPanel(panel);
        panel.setVideoController(new VideoController(frame, false));

        Datavyu.setProjectController(panel.getProjectController());
        panel.registerListeners();
        panel.addFileDropEventListener(this);

        tabbedPane.add(panel);
        tabbedPane.setSelectedComponent(panel);
        tabbedPane.setTabComponentAt(0, new TabWithCloseButton(tabbedPane));

        // initialize the undo/redo system
        spreadsheetUndoManager = new SpreadsheetUndoManager();
        undoSupport = new UndoableEditSupport();
        undoSupport.addUndoableEditListener(new UndoAdapter());
        refreshUndoRedo();

        WindowListener exitListener = new WindowAdapter() {
            @Override
            public void windowIconified(WindowEvent e) {
                frame.setState(WindowEvent.WINDOW_ICONIFIED);
                Datavyu.getVideoController().setVisible(false);
                for (StreamViewer dv : Datavyu.getVideoController().getStreamViewers()) {
                    dv.setViewerVisible(false);
                }
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
                frame.setState(WindowEvent.WINDOW_DEICONIFIED);
                Datavyu.getVideoController().setVisible(true);
                for (StreamViewer dv : Datavyu.getVideoController().getStreamViewers()) {
                    dv.setViewerVisible(true);
                }
            }

            @Override
            public void windowClosing(WindowEvent e) {
                Datavyu.getApplication().exit();
            }
        };
        frame.addWindowListener(exitListener);
    }

    public UndoableEditSupport getUndoSupport() {
        return undoSupport;
    }

    /**
     * @return The main window.
     */
    public JFrame getFrame() {
        return frame;
    }

    /**
     * @return The selected spreadsheet.
     */
    public JComponent getComponent() {
        return (JComponent) tabbedPane.getSelectedComponent();
    }


    public void checkForAutosavedFile() {
        // Check for autosaved file (crash condition)
        try {
            File tempFile = File.createTempFile("test", "");
            String path = FilenameUtils.getFullPath(tempFile.getPath());
            tempFile.delete();
            File folder = new File(path);
            File[] listOfFiles = folder.listFiles();
            if (listOfFiles != null) {
                for (File f : listOfFiles) {
                    // the last time datavyu crashed
                    if ((f.isFile()) &&
                            ((FilenameUtils.wildcardMatchOnSystem(f.getName(), "~*" + ProjectDocument.EXTENSION)) ||
                                    (FilenameUtils.wildcardMatchOnSystem(f.getName(), "~*.opf")) ||
                                    (FilenameUtils.wildcardMatchOnSystem(f.getName(), "~*.csv")))) {

                        // The autosave is deleted once it has opened, or if the user does not want it.
                        if (JOptionPane.showConfirmDialog(null,
                                "Watavyu quit before \"" + recoveredName(f.getName())
                                    + "\" was saved. Recover the unsaved project?",
                                "Watavyu",
                                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                            if (recover(f)) {
                                f.delete();
                                saveAs();
                            }
                        } else {
                            f.delete();
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Auto save failed. Error: ", e);
        }
        // Initialize auto save feature
        AutoSaveController.setInterval(1);
    }


    /**
     * Update the title of the application.
     */
    public void updateTitle() {
        // Show the project name instead of database.
        Resources resourceMap = Resources.of(Datavyu.class);
        ProjectController projectController = getSpreadsheetPanel().getProjectController();
        String postFix = projectController.isChanged() ? "  *" : "  ";
        String extension = "";
        final FileFilter lastSaveOption = projectController.getLastSaveOption();

        if (lastSaveOption instanceof ProjectFileFilter) {
            extension = ProjectDocument.EXTENSION;
        } else if (lastSaveOption instanceof CsvFilter) {
            extension = ".csv";
        } else if (lastSaveOption instanceof OpfFilter) {
            extension = ".opf";
        }

        String projectName = projectController.getProjectName();
        projectName = (projectName == null) ? "untitled" : projectName;

        String title = windowTitle(resourceMap.getString("Application.title"), projectName + extension + postFix,
                isQuickKeyMode());
        String tabTitle = projectName + postFix;

        Datavyu.getProjectController().getProject().setDatabaseFileName(projectName + extension);
        this.getSpreadsheetPanel().setName(tabTitle);
        tabbedPane.setTitleAt(tabbedPane.getSelectedIndex(), tabTitle);

        frame.setTitle(title);
    }


    /**
     * @param applicationTitle The application's name.
     * @param document         The project's file name and changed marker.
     * @param quickKeyMode     Whether quick-key mode is on. Highlight-and-focus mode is on by default, so its state is
     *                         shown only on its button and menu item.
     * @return The main window's title.
     */
    static String windowTitle(final String applicationTitle, final String document, final boolean quickKeyMode) {
        String title = applicationTitle + " - " + document;
        return quickKeyMode ? title + " <QUICK KEY MODE>" : title;
    }

    /**
     * Action for creating a new project.
     */
    public void showNewProjectForm() {
        new NewProjectController();
    }

    /**
     * Action for closing current tab
     */
    public void closeTab() {
        if(Datavyu.getApplication().safeQuit(getTabbedPane().getSelectedComponent())) {
            getTabbedPane().remove(getTabbedPane().getSelectedComponent());
        }
        if(getTabbedPane().getTabCount() == 0) {
            Datavyu.getApplication().exit();
        }
    }

    /**
     * Action for saving the current project. A project not yet saved as a project file, including one opened from an
     * older Datavyu file, is saved through {@link #saveAs()}.
     *
     * @return whether the project was saved
     */
    public boolean save() {
        ProjectController projectController = Datavyu.getProjectController();
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
                ProjectController projectController = Datavyu.getProjectController();
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
        ProjectController projectController = Datavyu.getProjectController();
        try {
            new SaveController().saveProject(file, projectController.toDocument(file.getAbsoluteFile().getParentFile()
                .toPath()));
            projectController.markProjectAsUnchanged();
            projectController.getDataStore().markAsUnchanged();
            return true;
        } catch (UserWarningException e) {
            logger.error("Save failed. Error: ", e);
            Datavyu.getApplication().showWarningDialog(e);
            return false;
        }
    }

    public void toggleQuickKeys() {
        quickKeyMode = !quickKeyMode;
        actions.get(QUICK_KEYS).putValue(Action.NAME, quickKeyModeText());
        updateTitle();
    }

    public void toggleHighlightAndFocusMode() {
        Datavyu.getVideoController().getMixerController().enableHighlightAndFocusHandler(null);
        actions.get(HIGHLIGHT_AND_FOCUS).putValue(Action.NAME,
                highlightAndFocusText(Datavyu.getVideoController().getCellHighlightAndFocus()));
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
        ProjectController projectController = Datavyu.getProjectController();

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
            File file = new File(fc.getSelectedFile().getParent(), dbFileName);

            exportJSON.exportAsJSON(dbFileName, projectController.getDataStore());
        } catch (Exception e) {
            logger.error("Failed export to JSON. Error: ", e);
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

        Datavyu.getView().getSpreadsheetPanel().redrawCells();

    }

    private void importJSON(DatavyuFileChooser fc){
        ProjectController projectController = Datavyu.getProjectController();

        try{
            OpenDataStoreFileController importJSON = new OpenDataStoreFileController();

            String dbFileName = fc.getSelectedFile().getPath();
            if (!dbFileName.endsWith(".json")) {
                logger.error("The selected file is not a JSON File.");
            }

            File file = fc.getSelectedFile();

            importJSON.importJSONToSpreadsheet(file, getSpreadsheetPanel());
        } catch (UserWarningException e) {
            logger.error("Failed export to JSON. Error: ", e);
            Datavyu.getApplication().showWarningDialog(e);
        } catch (JsonParseException e) {
            logger.error("Failed export to JSON. Error: ", e);
            Datavyu.getApplication().showWarningDialog(e.getMessage());
        } catch (IOException e) {
            logger.error("Failed export to JSON. Error: ", e);
            Datavyu.getApplication().showWarningDialog(e.getMessage());
        }
    }



    /**
     * Action for exporting the current project as a particular file.
     */
    public void exportFile() {
        DatavyuFileChooser fileChooser = new DatavyuFileChooser();

        fileChooser.addChoosableFileFilter(CellCsvFilter.INSTANCE);
        fileChooser.setFileFilter(CellCsvFilter.INSTANCE);

        int result = fileChooser.showSaveDialog(getComponent());

        if (result == JFileChooser.APPROVE_OPTION) {
            exportToCSV(fileChooser);
        }
    }

    private void exportToCSV(final DatavyuFileChooser fc) {
        ProjectController projectController = Datavyu.getProjectController();

        try {
            ExportDatabaseFileController exportC = new ExportDatabaseFileController();

            String dbFileName = fc.getSelectedFile().getPath();
            if (!dbFileName.endsWith(".csv")) {
                dbFileName = dbFileName.concat(".csv");
            }

            // Only save if the project file does not exists or if the user
            // confirms a file overwrite in the case that the file exists.
            if (!canSave(fc.getSelectedFile().getParent(), dbFileName)) {
                return;
            }
            File f = new File(fc.getSelectedFile().getParent(), dbFileName);

            exportC.exportAsCells(dbFileName, projectController.getDataStore());
        } catch (Exception e) {
            logger.error("Failed export to CSV. Error: ", e);
        }
    }

    /**
     * Action for exporting the current project as a particular file.
     */
    public void exportFileByFrame() {
        DatavyuFileChooser fileChooser = new DatavyuFileChooser();

        fileChooser.addChoosableFileFilter(FrameCsvFilter.INSTANCE);
        fileChooser.setFileFilter(FrameCsvFilter.INSTANCE);

        int result = fileChooser.showSaveDialog(getComponent());

        if (result == JFileChooser.APPROVE_OPTION) {
            exportToCSVByFrame(fileChooser);
        }
    }

    private void exportToCSVByFrame(final DatavyuFileChooser fc) {
        ProjectController projController = Datavyu.getProjectController();

        try {
            ExportDatabaseFileController exportC = new ExportDatabaseFileController();

            String dbFileName = fc.getSelectedFile().getPath();
            if (!dbFileName.endsWith(".csv")) {
                dbFileName = dbFileName.concat(".csv");
            }

            // Only save if the project file does not exists or if the user
            // confirms a file overwrite in the case that the file exists.
            if (!canSave(fc.getSelectedFile().getParent(), dbFileName)) {
                return;
            }

            exportC.exportByFrame(dbFileName, projController.getDataStore());

        } catch (Exception e) {
            logger.error("Export to CSV by frame failed. Error: ", e);
        }
    }

    private boolean canSave(final String directory, final String file) {
        File newFile = new File(directory, file);
        return (newFile.exists() && Datavyu.getApplication().overwriteExisting()) || !newFile.exists();
    }

    /**
     * Loading a Datavyu project
     */
    public void open() {

        DatavyuFileChooser fileChooser = new DatavyuFileChooser();

        fileChooser.addChoosableFileFilter(ProjectFileFilter.INSTANCE);
        fileChooser.addChoosableFileFilter(OpfFilter.INSTANCE);

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
                Datavyu.getApplication().showWarningDialog("Error: File is already open.");
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
        } else if (name.endsWith(".opf")) {
            return OpfFilter.INSTANCE;
        } else if (name.endsWith(".csv")) {
            return CsvFilter.INSTANCE;
        }
        return null;
    }

    /**
     * Helper method for opening a file from disk.
     *
     * @param fileChooser The file chooser to use.
     */
    private void open(final DatavyuFileChooser fileChooser) {
        progressBar = new DataviewProgressBar(this.getFrame(), false);
        task = new OpenTask(fileChooser);
        task.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                String test = "";
                if ("progress".equals(evt.getPropertyName())) {
                    int val = (Integer) evt.getNewValue();
                    String msg;
                    switch (val) {
                        case 0:
                            msg = "Preparing spreadsheet";
                            break;
                        case 10:
                            msg = "Opening project";
                            break;
                        case 40:
                            msg = "Project opened";
                            break;
                        case 50:
                            msg = "Loading project into spreadsheet";
                            break;
                        case 100:
                            msg = "Completed!";
                            break;
                        default:
                            msg = "Error loading project!";
                    }

                    progressBar.setProgress(val, msg);
                }
            }
        });
        task.execute();
        try {
            ProjectController p = task.get();
            if (p != null) {
                Datavyu.setProjectController(p);
                createNewSpreadsheet(p);
            }

            progressBar.close();
        } catch (Exception e) {
            logger.error("Failed opening project. Error: ", e);
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
        ProjectController projectController = Datavyu.getProjectController();
        if (projectController == null || !autosave.getName().equals(projectController.getDatabaseFileName())) {
            return false;
        }
        projectController.setLastSaveOption(null);
        projectController.setProjectName(recoveredName(autosave.getName()));
        projectController.projectChanged();
        return true;
    }

    /**
     * @param autosave the name of an autosaved file: {@code ~}, the project's name, {@code _} and a number, then the
     *                 extension
     * @return the project's name
     */
    static String recoveredName(final String autosave) {
        String name = autosave.endsWith(ProjectDocument.EXTENSION)
            ? autosave.substring(0, autosave.length() - ProjectDocument.EXTENSION.length())
            : FilenameUtils.removeExtension(autosave);
        return name.replaceFirst("^~", "").replaceFirst("_\\d+$", "");
    }

    private OpenController openDatabase(final File databaseFile) {

        // Set the database to the freshly loaded database.
        OpenController openController = new OpenController();
        openController.openDataStore(databaseFile);

        // Make a project for the new database.
        if (openController.getDataStore() != null) {
            return openController;
        }
        return null;
    }

    private OpenController openProjectFile(final File projectFile) {
        OpenController openController = new OpenController();
        openController.openProjectFile(projectFile);
        return openController;
    }

    private OpenController openProject(final File projectFile) {
        OpenController openController = new OpenController();
        openController.openProject(projectFile);

        // Check to make sure that this project file isn't already open
        if ((openController.getProject() != null) && (openController.getDataStore() != null)) {
            return openController;
        }
        return null;
    }

    /**
     * Handles the event for files being dropped onto a component. Only the
     * first file received will be opened.
     *
     * @param evt The event to handle.
     */
    @Override
    public void filesDropped(final FileDropEvent evt) {

        if (!Datavyu.getApplication().safeQuit()) {
            return;
        }

        DatavyuFileChooser fileChooser = new DatavyuFileChooser();
        fileChooser.setVisible(false);

        for (File file : evt.getFiles()) {
            open(file);
        }
    }

    /**
     * Action for creating a new variable.
     */
    public void showNewVariableForm() {
        new NewVariableController();
    }

    /**
     * Action for editing vocabs.
     */
    public void showVocabEditor() { vec.showView(); }

    /**
     * Action for showing the variable list.
     */
    public void showVariableList() { Datavyu.getApplication().showVariableList(); }

    /**
     * Action for showing the quicktime video controller.
     */
    public void showVideoController() {
        Datavyu.getApplication().showVideoController();
    }

    /**
     * Action for showing the about window.
     */
    public void showAboutWindow() {
        Datavyu.getApplication().showAboutWindow();
    }

    /**
     * Action for showing how to cite Watavyu.
     */
    public void showCitationDialog() {
        Datavyu.getApplication().show(new CitationDialog(getFrame()));
    }

    public ProjectController createNewSpreadsheet(ProjectController pc) {
        Datavyu.setProjectController(pc);

        // Data controller needs to be registered before load for the cell positioning highlighting
        VideoController dcv = new VideoController(frame, false);
        Datavyu.setVideoController(dcv);


        pc.setSpreadSheetPanel(new SpreadSheetPanel(pc, null));
        SpreadSheetPanel panel = pc.getSpreadSheetPanel();
        panel.registerListeners();
        panel.addFileDropEventListener(this);
        panel.setVideoController(dcv);

        tabbedPane.add(panel);
        tabbedPane.setTabComponentAt(tabbedPane.indexOfComponent(panel), new TabWithCloseButton(tabbedPane));
        tabbedPane.setSelectedComponent(panel);
        panel.clearCellSelection();
        setSheetLayout();
        pc.loadProject();

        return pc; //return value not used
    }

    //no usages as of 5/5/2014
    public ProjectController createNewSpreadsheet(DataStore ds) {
        ProjectController pc = new ProjectController();
        pc.setDataStore(ds);

        return createNewSpreadsheet(pc);
    }

    public ProjectController createNewSpreadsheet(String name) {
        ProjectController pc = new ProjectController();
        pc.setDataStore(DataStoreFactory.newDataStore());
        pc.setProjectName(name);
        pc = createNewSpreadsheet(pc);

        return pc; //return value not used
    }

    /**
     * Action for removing columns from the database.
     */
    public void deleteColumn() {
//        DataStore ds = Datavyu.getProjectController().getDataStore();
//        List<Variable> selectedVariables = ds.getSelectedVariables();

        List<Variable> selectedVariables = null;
        selectedVariables = Datavyu.getProjectController().getDataStore().getSelectedVariables();
        // record the effect
        UndoableEdit edit = new RemoveVariableEdit(selectedVariables);

        // perform the operation
        new DeleteColumnController(selectedVariables);

        // notify the listeners
        Datavyu.getView().getUndoSupport().postEdit(edit);
    }

    /**
     * Action for hiding columns.
     */
    public void hideColumn() {
        logger.info("Hidding columns");
        List<Variable> selectedVariables = null;
        selectedVariables = Datavyu.getProjectController().getDataStore().getSelectedVariables();
        for (Variable var : selectedVariables) {
            var.setHidden(true);
            var.setSelected(false);
        }
        getComponent().revalidate();
        getComponent().requestFocus();
    }

    /**
     * Action for showing all columns.
     */
    public void showAllColumns() {
        logger.info("Showing all columns");
        DataStore ds = Datavyu.getProjectController().getDataStore();

        for (Variable var : ds.getAllVariables()) {
            if (var.isHidden()) {
                var.setHidden(false);
            }
        }

        getComponent().revalidate();
    }

    /**
     * Action for changing variable name.
     */
    public void changeColumnName() {
        // Only one column should be selected, but just in case, we'll only
        // change the first column
//        Variable var = Datavyu.getProjectController().getDataStore().getSelectedVariables().get(0);
        Variable var = null;
        var = Datavyu.getProjectController().getDataStore().getSelectedVariables().get(0);

        for (SpreadsheetColumn sCol : panel.getColumns()) {
            if (sCol.getVariable().equals(var)) {
                sCol.showChangeVarNameDialog();

                break;
            }
        }
    }

    /**
     * Action for removing cells from the database.
     */
    public void deleteCells() {
        List<Cell> selectedCells = null;
        selectedCells = Datavyu.getProjectController().getDataStore().getSelectedCells();

        // record the effect
        UndoableEdit edit = new RemoveCellEdit(selectedCells);
        // perform the operation
        new DeleteCellController(selectedCells);


        // notify the listeners
        Datavyu.getView().getUndoSupport().postEdit(edit);
    }

    /**
     * Set the SheetLayoutType for the spreadsheet.
     */
    private void setSheetLayout() {
        SheetLayoutType type = SheetLayoutType.Ordinal;

        if (weakTemporalAlignmentMenuItem.isSelected()) {
            type = SheetLayoutType.WeakTemporal;
        }

        panel.setLayoutType(type);
    }

    public SheetLayoutType getSheetLayout() {
        if(weakTemporalAlignmentMenuItem.isSelected()) {
            return SheetLayoutType.WeakTemporal;
        } else {
            return SheetLayoutType.Ordinal;
        }
    }

    public void setRedraw(boolean b) {
        redraw = b;
    }

    public boolean checkAllTabsForChanges() {
        boolean changes = false;
        for (Component tab : tabbedPane.getComponents()) {
            if (tab instanceof SpreadSheetPanel) {
                SpreadSheetPanel sp = (SpreadSheetPanel) tab;
                if (sp.getProjectController().isChanged()) {
                    changes = true;
                }
            }
        }
        return changes;
    }

    public boolean checkIfFileAlreadyOpen(String filepath) {
        for (Component tab : tabbedPane.getComponents()) {
            if (tab instanceof SpreadSheetPanel) {
                SpreadSheetPanel sp = (SpreadSheetPanel) tab;
                if (sp.getProjectController().getFullPath() != null &&
                        sp.getProjectController().getFullPath().equals(filepath)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Undo Action.
     */
    public void undo() {
        spreadsheetUndoManager.undo();
        refreshUndoRedo();
    }

    /**
     * Redo Action.
     */
    public void redo() {
        spreadsheetUndoManager.redo();
        refreshUndoRedo();
    }



    public void zoomIn() {
        changeFontSize(ZOOM_INTERVAL);
    }

    public void zoomOut() {
        changeFontSize(-ZOOM_INTERVAL);
    }

    /**
     * Function to reset the zoom level to the default size.
     *
     * @param evt The event that triggered this action.
     */
    public void resetZoom() {
        changeFontSize(SpreadsheetTheme.DEFAULT_DATA_FONT_SIZE - SpreadsheetTheme.dataFont().getSize());
    }

    /**
     * The method to invoke when the use selects the spreadsheet menu item.
     *
     * @param evt The event that triggered this action.
     */
    private void spreadsheetMenuSelected(final MenuEvent evt) {
        Resources rMap = Resources.of(DatavyuView.class);

        int totalNumberOfVisibleColumns = Datavyu.getProjectController().getDataStore().getVisibleVariables().size();

        if (totalNumberOfVisibleColumns == 0) {
            newCellMenuItem.setEnabled(false);
            exportJSON.setEnabled(false);
        } else {
            newCellMenuItem.setEnabled(true);
            exportJSON.setEnabled(true);
        }

        importJSON.setEnabled(true);

        List<Variable> selectedCols = Datavyu.getProjectController().getDataStore().getSelectedVariables();

        if (selectedCols.isEmpty()) {
            deleteColumnMenuItem.setEnabled(false);
            hideSelectedColumnsMenuItem.setEnabled(false);
            changeVarNameMenuItem.setEnabled(false);
        } else if (selectedCols.size() == 1) {
            deleteColumnMenuItem.setText(rMap.getString("deleteColumnMenuItemSingle.text"));
            deleteColumnMenuItem.setEnabled(true);
            hideSelectedColumnsMenuItem.setText(rMap.getString("hideSelectedColumnsMenuItemSingle.text"));
            hideSelectedColumnsMenuItem.setEnabled(true);
            changeVarNameMenuItem.setEnabled(true);
        } else {
            deleteColumnMenuItem.setText(rMap.getString("deleteColumnMenuItemPlural.text"));
            deleteColumnMenuItem.setEnabled(true);
            hideSelectedColumnsMenuItem.setText(rMap.getString("hideSelectedColumnsMenuItemPlural.text"));
            hideSelectedColumnsMenuItem.setEnabled(true);
            changeVarNameMenuItem.setEnabled(false);
        }

        List<Cell> selectedCells = Datavyu.getProjectController().getDataStore().getSelectedCells();

        if (selectedCells.isEmpty()) {
            deleteCellMenuItem.setEnabled(false);
        } else if (selectedCells.size() == 1) {
            deleteCellMenuItem.setText(rMap.getString("deleteCellMenuItemSingle.text"));
            deleteCellMenuItem.setEnabled(true);
        } else {
            deleteCellMenuItem.setText(rMap.getString("deleteCellMenuItemPlural.text"));
            deleteCellMenuItem.setEnabled(true);
        }

        if (panel.getAdjacentSelectedCells(ArrayDirection.LEFT) == 0) {
            newCellLeftMenuItem.setEnabled(false);
        } else if (panel.getAdjacentSelectedCells(ArrayDirection.LEFT) == 1) {
            newCellLeftMenuItem.setText(rMap.getString("newCellLeftMenuItemSingle.text"));
            newCellLeftMenuItem.setEnabled(true);
        } else {
            newCellLeftMenuItem.setText(rMap.getString("newCellLeftMenuItemPlural.text"));
            newCellLeftMenuItem.setEnabled(true);
        }

        if (panel.getAdjacentSelectedCells(ArrayDirection.RIGHT) == 0) {
            newCellRightMenuItem.setEnabled(false);
        } else if (panel.getAdjacentSelectedCells(ArrayDirection.RIGHT) == 1) {
            newCellRightMenuItem.setText(rMap.getString("newCellRightMenuItemSingle.text"));
            newCellRightMenuItem.setEnabled(true);
        } else {
            newCellRightMenuItem.setText(rMap.getString("newCellRightMenuItemPlural.text"));
            newCellRightMenuItem.setEnabled(true);
        }
    }

    public void newCellLeft() {
        List<Cell> selectedCells = null;

        selectedCells = Datavyu.getProjectController().getDataStore().getSelectedCells();

        new CreateNewCellController(selectedCells, ArrayDirection.LEFT);
    }

    public void newCellRight() {
        List<Cell> selectedCells = null;

        selectedCells = Datavyu.getProjectController().getDataStore().getSelectedCells();

        new CreateNewCellController(selectedCells, ArrayDirection.RIGHT);
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
        panel.revalidate();
        panel.repaint();
    }

    /**
     * Returns SpreadSheetPanel
     *
     * @return SpreadSheetPanel panel
     */
    public SpreadSheetPanel getSpreadsheetPanel() {
        return (SpreadSheetPanel) tabbedPane.getSelectedComponent();
    }

    public void refreshUndoRedo() {

        // refresh undo
        actions.get(UNDO).putValue(Action.NAME, spreadsheetUndoManager.getUndoPresentationName());
        actions.get(UNDO).setEnabled(spreadsheetUndoManager.canUndo());

        // refresh redo
        actions.get(REDO).putValue(Action.NAME, spreadsheetUndoManager.getRedoPresentationName());
        actions.get(REDO).setEnabled(spreadsheetUndoManager.canRedo());

        // Display any changes.
        //showSpreadsheet();

        panel.revalidate();
        panel.repaint();
    }

    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    // Names of commands whose menu text or state changes
    private static final String UNDO = "Undo";
    private static final String REDO = "Redo";
    private static final String QUICK_KEYS = "Quick Key Mode";
    private static final String HIGHLIGHT_AND_FOCUS = "Highlight and Focus Mode";

    private Action action(final String name, final Runnable command) {
        Action action = new AbstractAction(name) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                command.run();
            }
        };
        actions.put(name, action);
        return action;
    }

    /**
     * Creates the commands of the menu bar. Their names are also the names of their keyboard shortcuts.
     */
    private void createActions() {
        Resources r = resources;
        action(r.getString("newMenuItem.text"), this::showNewProjectForm);
        action(r.getString("openMenuItem.text"), this::open);
        action(r.getString("closeTabMenuItem.text"), this::closeTab);
        action(r.getString("saveMenuItem.text"), this::save);
        action(r.getString("saveAsMenuItem.text"), this::saveAs);
        action(r.getString("exportMenuItem.text"), this::exportFile);
        action(r.getString("exportByFrameMenuItem.text"), this::exportFileByFrame);

        action(r.getString("newColumnMenuItem.text"), this::showNewVariableForm);
        action(r.getString("columnListMenuItem.text"), this::showVariableList);
        action(r.getString("codeEditorMenuItem.text"), this::showVocabEditor);
        action(r.getString("newCellMenuItem.text"), this::newCell);
        action(r.getString("newCellLeftMenuItem.text"), this::newCellLeft);
        action(r.getString("newCellRightMenuItem.text"), this::newCellRight);
        action(r.getString("changeVarNameMenuItem.text"), this::changeColumnName);
        action(r.getString("hideSelectedColumnsMenuItem.text"), () -> {
            hideColumn();
            getSpreadsheetPanel().deselectAll();
        });
        action(r.getString("showAllColumnsMenuItem.text"), () -> {
            showAllColumns();
            getSpreadsheetPanel().deselectAll();
        });
        action(r.getString("deleteColumnMenuItem.text"), this::deleteColumn);
        action(r.getString("deleteCellMenuItem.text"), this::deleteCells);
        action(UNDO, this::undo);
        action(REDO, this::redo);
        action(r.getString("exportJSONMenuItem.text"), this::exportToJSON);
        action(r.getString("importJSONMenuItem.text"), this::importJSONToSpreadsheet);
        action(r.getString("weakTemporalAlignmentMenuItem.text"), () -> {
            setRedraw(true);
            setSheetLayout();
        }).putValue(Action.SELECTED_KEY, false);
        action(r.getString("zoomInMenuItem.text"), this::zoomIn);
        action(r.getString("zoomOutMenuItem.text"), this::zoomOut);
        action(r.getString("resetZoomMenuItem.text"), this::resetZoom);
        action(QUICK_KEYS, this::toggleQuickKeys).putValue(Action.NAME, quickKeyModeText());
        action(HIGHLIGHT_AND_FOCUS, this::toggleHighlightAndFocusMode)
                .putValue(Action.NAME, highlightAndFocusText(VideoController.HIGHLIGHT_AND_FOCUS_BY_DEFAULT));

        action(r.getString("videoControllerMenuItem.text"), this::showVideoController);

        action(r.getString("hotkeysMenuItem.text"), this::showHotkeysDialog);
        action(r.getString("citationMenuItem.text"), this::showCitationDialog);
    }

    private Action command(final String key) {
        Action action = actions.get(resources.getString(key));
        if (action == null) {
            throw new IllegalArgumentException("No command for " + key);
        }
        return action;
    }

    private static VideoController controller() {
        return Datavyu.getVideoController();
    }

    private static Action run(final Runnable command) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                command.run();
            }
        };
    }

    /**
     * @return Every keyboard shortcut of the application.
     */
    private Shortcuts createShortcuts() {
        final int cmd = Shortcuts.COMMAND;
        final int shift = InputEvent.SHIFT_DOWN_MASK;
        return Shortcuts.builder()
                .menu("File", resources.getString("newMenuItem.text"),
                        KeyStroke.getKeyStroke(KeyEvent.VK_N, cmd), command("newMenuItem.text"))
                .menu("File", resources.getString("openMenuItem.text"),
                        KeyStroke.getKeyStroke(KeyEvent.VK_O, cmd), command("openMenuItem.text"))
                .menu("File", resources.getString("closeTabMenuItem.text"),
                        KeyStroke.getKeyStroke(KeyEvent.VK_W, cmd), command("closeTabMenuItem.text"))
                .menu("File", resources.getString("saveMenuItem.text"),
                        KeyStroke.getKeyStroke(KeyEvent.VK_S, cmd), command("saveMenuItem.text"))
                .menu("File", resources.getString("saveAsMenuItem.text"),
                        KeyStroke.getKeyStroke(KeyEvent.VK_S, cmd | shift), command("saveAsMenuItem.text"))
                .global("Spreadsheet", resources.getString("newCellLeftMenuItem.text"),
                        KeyStroke.getKeyStroke(KeyEvent.VK_L, cmd), command("newCellLeftMenuItem.text"))
                .global("Spreadsheet", resources.getString("newCellRightMenuItem.text"),
                        KeyStroke.getKeyStroke(KeyEvent.VK_R, cmd), command("newCellRightMenuItem.text"))
                .menu("Spreadsheet", resources.getString("deleteCellMenuItem.text"),
                        KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, cmd), command("deleteCellMenuItem.text"))
                .menu("Spreadsheet", UNDO, KeyStroke.getKeyStroke(KeyEvent.VK_Z, cmd), actions.get(UNDO))
                .menu("Spreadsheet", REDO, KeyStroke.getKeyStroke(KeyEvent.VK_Y, cmd), actions.get(REDO))
                .menu("Spreadsheet", resources.getString("weakTemporalAlignmentMenuItem.text"),
                        KeyStroke.getKeyStroke(KeyEvent.VK_T, cmd), command("weakTemporalAlignmentMenuItem.text"))
                .global("Spreadsheet", resources.getString("zoomInMenuItem.text"),
                        KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, cmd), command("zoomInMenuItem.text"))
                .global("Spreadsheet", resources.getString("zoomOutMenuItem.text"),
                        KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, cmd), command("zoomOutMenuItem.text"))
                .menu("Spreadsheet", resources.getString("resetZoomMenuItem.text"),
                        KeyStroke.getKeyStroke(KeyEvent.VK_0, cmd), command("resetZoomMenuItem.text"))
                .menu("Spreadsheet", QUICK_KEYS, KeyStroke.getKeyStroke(KeyEvent.VK_K, cmd | shift),
                        actions.get(QUICK_KEYS))
                .menu("Spreadsheet", HIGHLIGHT_AND_FOCUS, KeyStroke.getKeyStroke(KeyEvent.VK_F, cmd | shift),
                        actions.get(HIGHLIGHT_AND_FOCUS))
                .reference("Spreadsheet", "Select column to left", "⌘←")
                .reference("Spreadsheet", "Select column to right", "⌘→")
                .keypad("Point cell: new cell with onset and offset at the current time", KeyEvent.VK_DIVIDE, 0,
                        run(() -> controller().pressPointCell()))
                .keypad("Show or hide tracks", KeyEvent.VK_MULTIPLY, 0, run(() -> controller().pressShowTracksSmall()))
                .keypad("Set cell onset", KeyEvent.VK_NUMPAD7, 0, run(() -> controller().pressSetCellOnset()))
                .keypad("Play", KeyEvent.VK_NUMPAD8, 0, run(() -> controller().pressPlay()))
                .keypad("Set cell offset", KeyEvent.VK_NUMPAD9, 0, run(() -> controller().pressSetCellOffsetNine()))
                .keypad("Shuttle back", KeyEvent.VK_NUMPAD4, 0, run(() -> controller().pressShuttleBack()))
                .keypad("Stop", KeyEvent.VK_NUMPAD5, 0, run(() -> controller().pressStop()))
                .keypad("Shuttle forward", KeyEvent.VK_NUMPAD6, 0, run(() -> controller().pressShuttleForward()))
                .keypad("Jog back one frame", KeyEvent.VK_NUMPAD1, 0, run(() -> controller().jogBackAction()))
                .keypad("Pause", KeyEvent.VK_NUMPAD2, 0, run(() -> controller().pressPause()))
                .keypad("Jog forward one frame", KeyEvent.VK_NUMPAD3, 0, run(() -> controller().jogForwardAction()))
                .keypad("New cell, setting the previous cell's offset", KeyEvent.VK_NUMPAD0, 0,
                        run(() -> controller().pressCreateNewCellSettingOffset()))
                .keypad("Set cell offset", KeyEvent.VK_DECIMAL, 0, run(() -> controller().pressSetCellOffsetPeriod()))
                .keypad("Jump back by the jump time", KeyEvent.VK_SUBTRACT, 0, run(() -> controller().pressJumpBack()))
                .keypad("Jump forward by the jump time", KeyEvent.VK_SUBTRACT, shift,
                        run(() -> controller().pressJumpForward()))
                .keypad("Go to the onset field's time", KeyEvent.VK_ADD, 0, run(() -> controller().pressFind()))
                .keypad("Go to the offset field's time", KeyEvent.VK_ADD, shift, run(() -> {
                    controller().findOffsetAction();
                    controller().pressFind();
                }))
                .keypad("New cell", KeyEvent.VK_ENTER, 0, run(() -> controller().pressCreateNewCell()))
                .reference("Code editor", "Add column", "⌃M")
                .reference("Code editor", "Add code to the selected column", "⌃A")
                .reference("Code editor", "Apply changes", "⌃S")
                .reference("Code editor", "Delete", "⌃⌦")
                .reference("Code editor", "Close", "Esc")
                .build(() -> frame.isFocused(), held -> {
                    if (controller() != null) {
                        controller().setShiftMask(held);
                    }
                });
    }

    private JMenuItem item(final String key) {
        return item(command(key));
    }

    private JMenuItem item(final Action action) {
        return withAccelerator(new JMenuItem(action), action);
    }

    private <T extends JMenuItem> T withAccelerator(final T item, final Action action) {
        for (Shortcuts.Shortcut shortcut : shortcuts.all()) {
            if (shortcut.getKey() != null && shortcut.getScope() != Shortcuts.Scope.KEYPAD
                    && shortcut.getAction() == action) {
                item.setAccelerator(shortcut.getKey());
            }
        }
        return item;
    }

    /**
     * @return The menu bar, whose items run the actions created by {@link #createActions()}.
     */
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setFocusable(false);

        JMenu fileMenu = new JMenu(resources.getString("fileMenu.text"));
        fileMenu.add(item("newMenuItem.text"));
        fileMenu.add(item("openMenuItem.text"));
        fileMenu.add(item("closeTabMenuItem.text"));
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
        fileMenu.add(item("saveMenuItem.text"));
        fileMenu.add(item("saveAsMenuItem.text"));
        fileMenu.add(item("exportMenuItem.text"));
        fileMenu.add(item("exportByFrameMenuItem.text"));
        menuBar.add(fileMenu);

        JMenu spreadsheetMenu = new JMenu(resources.getString("spreadsheetMenu.text"));
        spreadsheetMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(final MenuEvent e) {
                spreadsheetMenuSelected(e);
            }

            @Override
            public void menuDeselected(final MenuEvent e) {
            }

            @Override
            public void menuCanceled(final MenuEvent e) {
            }
        });
        spreadsheetMenu.add(item("newColumnMenuItem.text"));
        spreadsheetMenu.add(item("columnListMenuItem.text"));
        spreadsheetMenu.add(item("codeEditorMenuItem.text"));
        spreadsheetMenu.addSeparator();
        newCellMenuItem = spreadsheetMenu.add(item("newCellMenuItem.text"));
        newCellLeftMenuItem = spreadsheetMenu.add(item("newCellLeftMenuItem.text"));
        newCellRightMenuItem = spreadsheetMenu.add(item("newCellRightMenuItem.text"));
        spreadsheetMenu.addSeparator();
        changeVarNameMenuItem = spreadsheetMenu.add(item("changeVarNameMenuItem.text"));
        hideSelectedColumnsMenuItem = spreadsheetMenu.add(item("hideSelectedColumnsMenuItem.text"));
        spreadsheetMenu.add(item("showAllColumnsMenuItem.text"));
        spreadsheetMenu.addSeparator();
        deleteColumnMenuItem = spreadsheetMenu.add(item("deleteColumnMenuItem.text"));
        deleteCellMenuItem = spreadsheetMenu.add(item("deleteCellMenuItem.text"));
        spreadsheetMenu.addSeparator();
        spreadsheetMenu.add(item(actions.get(UNDO)));
        spreadsheetMenu.add(item(actions.get(REDO)));
        spreadsheetMenu.addSeparator();
        exportJSON = spreadsheetMenu.add(item("exportJSONMenuItem.text"));
        importJSON = spreadsheetMenu.add(item("importJSONMenuItem.text"));
        spreadsheetMenu.addSeparator();
        weakTemporalAlignmentMenuItem = withAccelerator(
                new JCheckBoxMenuItem(command("weakTemporalAlignmentMenuItem.text")),
                command("weakTemporalAlignmentMenuItem.text"));
        spreadsheetMenu.add(weakTemporalAlignmentMenuItem);
        JMenu zoomMenu = new JMenu(resources.getString("zoomMenu.text"));
        zoomMenu.add(item("zoomInMenuItem.text"));
        zoomMenu.add(item("zoomOutMenuItem.text"));
        zoomMenu.addSeparator();
        zoomMenu.add(item("resetZoomMenuItem.text"));
        spreadsheetMenu.add(zoomMenu);
        JMenuItem quickKeysItem = spreadsheetMenu.add(item(actions.get(QUICK_KEYS)));
        quickKeysItem.setToolTipText(resources.getString("quickKeysMenuItem.toolTipText"));
        JMenuItem highlightAndFocusItem = spreadsheetMenu.add(item(actions.get(HIGHLIGHT_AND_FOCUS)));
        highlightAndFocusItem.setToolTipText(resources.getString("highlightAndFocusMenuItem.toolTipText"));
        menuBar.add(spreadsheetMenu);

        JMenu controllerMenu = new JMenu(resources.getString("controllerMenu.text"));
        controllerMenu.add(item("videoControllerMenuItem.text"));
        menuBar.add(controllerMenu);

        JMenu helpMenu = new JMenu(resources.getString("helpMenu.text"));
        helpMenu.add(item("hotkeysMenuItem.text"));
        helpMenu.add(item("citationMenuItem.text"));
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

    /**
     * Creates a cell in the selected column, as the New Cell menu item does.
     */
    public void newCell() {
        new CreateNewCellController().createDefaultCell();
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

            if (fileChooser != null && !fileChooser.getSelectedFile().exists()) {
                setProgress(2);
                return null;
            }

            if (fileChooser != null &&
                    tabbedPane != null && Datavyu.getProjectController() != null &&
                    tabbedPane.getTabCount() == 1 &&
                    Datavyu.getProjectController().getProjectName() == null &&
                    !Datavyu.getProjectController().isChanged() &&
                    fileChooser.getSelectedFile().exists()) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        tabbedPane.remove(0);
                    }
                });
            }


            setProgress(0);
            FileFilter filter = fileChooser.getFileFilter();
            setProgress(10);
            OpenController openController;

            if (filter == ProjectFileFilter.INSTANCE) {
                openController = openProjectFile(fileChooser.getSelectedFile());
            } else if (filter == OpfFilter.INSTANCE) {
                // Opening an older Datavyu project file
                openController = openProject(fileChooser.getSelectedFile());
            } else {
                // Opening a database file
                openController = openDatabase(fileChooser.getSelectedFile());
            }

            if (openController != null && openController.getError() != null) {
                final String error = openController.getError();
                SwingUtilities.invokeLater(() -> Datavyu.getApplication().showWarningDialog(error));
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

    private class NoEditTableModel extends DefaultTableModel {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    }

    /**
     * An undo/redo adapter. The adapter is notified when an undo edit occur.
     * The adaptor extract the edit from the event, add it to the UndoManager, and refresh the GUI
     */
    private class UndoAdapter implements UndoableEditListener {
        @Override
        public void undoableEditHappened(UndoableEditEvent evt) {
            UndoableEdit edit = evt.getEdit();
            spreadsheetUndoManager.addEdit(edit);
            refreshUndoRedo();
        }
    }
}
