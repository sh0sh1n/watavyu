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
package org.datavyu;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.TitleNotifier;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.util.MacOS;
import org.datavyu.util.Resources;
import org.datavyu.util.Settings;
import org.datavyu.views.AboutV;
import org.datavyu.views.DatavyuView;
import org.datavyu.views.VariableListV;
import org.datavyu.views.VideoController;
import org.datavyu.views.WindowPlacement;
import org.datavyu.views.discrete.SpreadSheetPanel;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * The application: starts the main window and the video controller, and quits.
 */
public final class Datavyu implements TitleNotifier {

    /** Logger for this class */
    private static Logger logger = LogManager.getLogger(Datavyu.class);

    /** The desired minimum initial width */
    private static final int INIT_MIN_X = 600;

    /** The desired minimum initial height */
    private static final int INIT_MIN_Y = 700;

    /** Settings key of the main window's bounds */
    private static final String MAIN_WINDOW = "main";

    /** Settings key of the video controller's location */
    private static final String VIDEO_CONTROLLER_WINDOW = "videoController";

    private static final Datavyu application = new Datavyu();

    // TODO: Move this to some OS specifics
    private static boolean osxPressAndHoldEnabled;

    /** The main window. It exists before its contents, which use it while they are built. */
    private static final JFrame mainFrame = new JFrame();

    /** The contents of the main window. */
    private static DatavyuView datavyuView;

    /** The video controller */
    private static VideoController videoController;

    /** The project controller */
    private static ProjectController projectController;

    static {
        logger.info("Working directory is: " + System.getProperty("user.dir"));
        logger.debug("Class path is: " + System.getProperty("java.class.path"));
        logger.debug("The java library path is: " + System.getProperty("java.library.path"));
    }

    /** This application is ready to open a file */
    public boolean readyToOpenFile = false;

    /** File path from the command line */
    private String commandLineFile;

    private Datavyu() {
    }

    /**
     * Get the single instance of the video controller used in Datavyu
     *
     * @return Video controller
     */
    public static VideoController getVideoController() {
        return videoController;
    }

    /**
     * Sets the video controller
     *
     * @param videoController The video controller
     */
    public static void setVideoController(VideoController videoController) {
        Datavyu.videoController = videoController;
    }

    /**
     * Gets application instance
     *
     * @return Instance of the Datavyu application
     */
    public static Datavyu getApplication() {
        return application;
    }

    /**
     * Main method launching the application
     *
     * @param args The command line arguments passed to Datavyu. The first, if any, is a file to open.
     */
    public static void main(final String[] args) {
        SwingUtilities.invokeLater(() -> application.start(args));
    }

    // TODO: Why do we have these two project controllers?
    public static ProjectController getProjectController() {
        if (datavyuView != null && datavyuView.getSpreadsheetPanel() != null
                && datavyuView.getSpreadsheetPanel().getProjectController() != null) {
            return datavyuView.getSpreadsheetPanel().getProjectController();
        }
        return projectController;
    }

    public static void setProjectController(ProjectController p) {
        projectController = p;
    }

    public static DatavyuView getView() {
        return datavyuView;
    }

    public void setCommandLineFile(String commandLineFile) {
        logger.info("Command line file set to " + commandLineFile);
        this.commandLineFile = commandLineFile;
    }

    /**
     * @return The main window.
     */
    public JFrame getMainFrame() {
        return mainFrame;
    }

    /**
     * Shows a window, centred on its owner the first time; see {@link WindowPlacement#show}.
     *
     * @param window The window to show.
     */
    public void show(final Window window) {
        WindowPlacement.show(window);
    }

    /**
     * Action for showing the video controller.
     */
    public void showVideoController() {
        show(videoController);
        videoController.setShouldBeVisible(true);
    }

    /**
     * Action for showing the variable list.
     */
    public void showVariableList() {
        JFrame mainFrame = Datavyu.getView().getFrame();
        VariableListV listVarView = new VariableListV(mainFrame, false, projectController.getDataStore());
        listVarView.registerListeners();
        show(listVarView);
    }

    /**
     * Action for showing the about window.
     */
    public void showAboutWindow() {
        JFrame mainFrame = Datavyu.getView().getFrame();
        AboutV aboutWindow = new AboutV(mainFrame, false);
        show(aboutWindow);
    }

    /**
     * Show a warning dialog to the user.
     *
     * @param s The message to present to the user.
     */
    public void showWarningDialog(final String s) {
        JFrame mainFrame = Datavyu.getView().getFrame();
        Resources resourceMap = Resources.of(Datavyu.class);
        JOptionPane.showMessageDialog(mainFrame, s, resourceMap.getString("WarningDialog.title"),
                JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Show a warning dialog to the user.
     *
     * @param e The UserWarningException to present to the user.
     */
    public void showWarningDialog(final UserWarningException e) {
        showWarningDialog(e.getMessage());
    }

    /**
     * User quits- check for save needed. Note that this can be used even in
     * situations when the application is not truly "quitting", but just the
     * database information is being lost (e.g. on an "open" or "new"
     * instruction). In all interpretations, "true" indicates that all unsaved
     * changes are to be discarded.
     *
     * @return True for quit, false otherwise.
     */
    public boolean safeQuit() {
        JFrame mainFrame = Datavyu.getApplication().getMainFrame();
        Resources resourceMap = Resources.of(Datavyu.class);
        if (getView().checkAllTabsForChanges()) {
            for (Component tab : getView().getTabbedPane().getComponents()) {
                if (tab instanceof SpreadSheetPanel) {
                    SpreadSheetPanel sp = (SpreadSheetPanel) tab;
                    getView().getTabbedPane().setSelectedComponent(sp);
                    // Ask to save if this spreadsheet has been changed
                    if (sp.getProjectController().isChanged()) {
                        String cancelOption = "Cancel";
                        String noOption = "Don't save";
                        String yesOption = "Save";

                        String[] options = MacOS.getOptions(yesOption, noOption, cancelOption);

                        // Get name of spreadsheet.  Check in both project and datastore.
                        String projectName = sp.getProjectController().getProjectName();

                        if (projectName == null) {
                            projectName = sp.getDataStore().getName();
                        }

                        int selectedOption = JOptionPane.showOptionDialog(mainFrame,
                                resourceMap.getString("UnsavedDialog.message",projectName),
                                resourceMap.getString("UnsavedDialog.title"),
                                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                                null, options, yesOption);

                        if (selectedOption == 0) {
                            getView().save();
                        }

                        return !(selectedOption == 1);
                    }
                }
            }
            // User has been asked whether or not to save each file, we can return now
            return true;
        } else {
            // Project hasn't been changed.
            return true;
        }
    }

    /**
     * Function to check whether or not it is OK to close this tab
     */
    public boolean safeQuit(Component tab) {
        JFrame mainFrame = Datavyu.getApplication().getMainFrame();
        Resources resourceMap = Resources.of(Datavyu.class);
        SpreadSheetPanel spreadsheetPanel = (SpreadSheetPanel) tab;
        if (spreadsheetPanel.getProjectController().isChanged()) {
            getView().getTabbedPane().setSelectedComponent(spreadsheetPanel);

            String cancelOption = "Cancel";
            String noOption = "Don't save";
            String yesOption = "Save";

            String[] options = MacOS.getOptions(yesOption, noOption, cancelOption);

            // Get project name.
            String projName = spreadsheetPanel.getProjectController().getProjectName();
            if (projName == null) {
                projName = spreadsheetPanel.getDataStore().getName();
            }
            
            int selectedOption = JOptionPane.showOptionDialog(mainFrame,
                    resourceMap.getString("UnsavedDialog.tabmessage", projName),
                    resourceMap.getString("UnsavedDialog.title"),
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, yesOption);

            if (selectedOption == 0) {
                getView().save();
            }

            // Options are ordered yes | cancel | no
            return !(selectedOption == 1);
        } else {
            return true;
        }
    }

    /**
     * If the user is trying to save over an existing file, prompt them whether
     * they they wish to continue.
     *
     * @return True for overwrite, false otherwise.
     */
    public boolean overwriteExisting() {
        JFrame mainFrame = Datavyu.getApplication().getMainFrame();
        Resources resourceMap = Resources.of(Datavyu.class);
        String defaultOption = "Cancel";
        String alternativeOption = "Overwrite";
        String[] options = MacOS.getOptions(defaultOption, alternativeOption);

        int selectedOption = JOptionPane.showOptionDialog(
                mainFrame,
                resourceMap.getString("OverwriteDialog.message"),
                resourceMap.getString("OverwriteDialog.title"),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                defaultOption);

        return selectedOption == 1;
    }

    /**
     * Creates and shows the main window and the video controller, then opens the file given on the command line.
     */
    private void start(final String[] args) {
        // Key repeat instead of the accent popup while holding keys (B2: this changes the setting for every app).
        if (MacOS.isOSXPressAndHoldEnabled()) {
            osxPressAndHoldEnabled = true;
            MacOS.setOSXPressAndHoldValue(false);
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            logger.error("Cannot use the system look and feel", e);
        }
        MacOS.loadCompileHandler();
        if (args.length > 0) {
            commandLineFile = args[0];
        }

        datavyuView = new DatavyuView(mainFrame);
        if (!WindowPlacement.restoreAndTrack(mainFrame, MAIN_WINDOW, true)) {
            mainFrame.pack();
            mainFrame.setSize(Math.max(mainFrame.getWidth(), INIT_MIN_X), Math.max(mainFrame.getHeight(), INIT_MIN_Y));
            mainFrame.setLocationRelativeTo(null);
        }
        mainFrame.setVisible(true);

        // Create video controller.
        projectController = new ProjectController();
        videoController = datavyuView.getSpreadsheetPanel().getVideoController();

        if (!WindowPlacement.restoreAndTrack(videoController, VIDEO_CONTROLLER_WINDOW, false)) {
            // Below the main window, but neither below the bottom of the primary screen nor above its top.
            final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int x = mainFrame.getX();
            int y = mainFrame.getY() + mainFrame.getHeight();
            y = (int) Math.max(Math.min(y, screenSize.getHeight() - videoController.getHeight()), 0);
            videoController.setLocation(x, y);
        }
        videoController.setVisible(true);
        datavyuView.checkForAutosavedFile();

        // The DB we create by default doesn't really have any unsaved changes.
        projectController.getDataStore().markAsUnchanged();
        ready();
    }

    private void ready() {
        readyToOpenFile = true;
        if (commandLineFile != null) {
            getView().openExternalFile(new File(commandLineFile));
            commandLineFile = null;
        }
    }

    /**
     * Quits, after asking about unsaved changes. Does nothing if the user cancels.
     */
    public void exit() {
        if (safeQuit()) {
            shutdown();
            System.exit(0);
        }
    }

    /**
     * Clean up after ourselves.
     */
    public void shutdown() {
        if (osxPressAndHoldEnabled) {
            MacOS.setOSXPressAndHoldValue(true);
        }

        logger.info("Saving settings.");
        Settings.get().save();
    }

    @Override
    public void updateTitle() {
        // Update the main title
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (datavyuView != null) {
                    datavyuView.updateTitle();
                }
            }
        });
    }
}
