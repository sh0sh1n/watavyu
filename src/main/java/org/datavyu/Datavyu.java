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
package org.datavyu;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.util.MacOS;
import org.datavyu.util.Resources;
import org.datavyu.util.Settings;
import org.datavyu.views.AboutV;
import org.datavyu.views.DatavyuView;
import org.datavyu.views.WindowPlacement;
import org.datavyu.views.discrete.SpreadSheetPanel;
import org.datavyu.views.discrete.SpreadsheetTheme;
import org.datavyu.util.Dialogs;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

/**
 * The application: starts the main window, opens files given to it, and quits. One is made, by {@link #main}, and
 * given to what needs it; nothing reaches it through a static field.
 */
public final class Datavyu {

    /** System property that makes window title bars follow the Mac's appearance, as the palette does. */
    static final String TITLE_BAR_APPEARANCE = "apple.awt.application.appearance";

    static {
        // AWT reads it when it starts, which is after this class loads.
        System.setProperty(TITLE_BAR_APPEARANCE, "system");
    }

    /** Logger for this class */
    private static Logger logger = LogManager.getLogger(Datavyu.class);

    /** The desired minimum initial width */
    private static final int INIT_MIN_X = 600;

    /** The desired minimum initial height */
    private static final int INIT_MIN_Y = 700;

    /** Settings key of the main window's bounds */
    private static final String MAIN_WINDOW = "main";

    static {
        logger.info("Working directory is: " + System.getProperty("user.dir"));
        logger.debug("Class path is: " + System.getProperty("java.class.path"));
        logger.debug("The java library path is: " + System.getProperty("java.library.path"));
    }

    // TODO: Move this to some OS specifics
    private boolean osxPressAndHoldEnabled;

    /** The main window. It exists before its contents, which use it while they are built. */
    private JFrame mainFrame;

    /** The contents of the main window. */
    private DatavyuView datavyuView;

    /** This application is ready to open a file */
    public boolean readyToOpenFile = false;

    /** File path from the command line */
    private String commandLineFile;

    Datavyu() {
    }

    /**
     * Main method launching the application
     *
     * @param args The command line arguments passed to Datavyu. The first, if any, is a file to open.
     */
    public static void main(final String[] args) {
        SwingUtilities.invokeLater(() -> new Datavyu().start(args));
    }

    /** Returns the main window's contents. */
    public DatavyuView getView() {
        return datavyuView;
    }

    public void setCommandLineFile(String commandLineFile) {
        logger.info("Command line file set to " + commandLineFile);
        this.commandLineFile = commandLineFile;
    }

    /**
     * Returns the main window.
     */
    public JFrame getMainFrame() {
        return mainFrame;
    }

    /**
     * Action for showing the about window.
     */
    public void showAboutWindow() {
        WindowPlacement.show(new AboutV(mainFrame, false));
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
        List<SpreadSheetPanel> spreadsheets = new ArrayList<>();
        for (Component tab : datavyuView.getTabbedPane().getComponents()) {
            if (tab instanceof SpreadSheetPanel) {
                spreadsheets.add((SpreadSheetPanel) tab);
            }
        }
        return mayClose(spreadsheets, "UnsavedDialog.message");
    }

    /**
     * Function to check whether or not it is OK to close this tab
     */
    public boolean safeQuit(Component tab) {
        return mayClose(List.of((SpreadSheetPanel) tab), "UnsavedDialog.tabmessage");
    }

    /**
     * Asks about each spreadsheet with unsaved changes whether to save it, discard its changes or cancel.
     *
     * @param spreadsheets the spreadsheets to close
     * @param message      the resource key of the question
     * @return whether they may close: false if the user cancelled or a save did not happen
     */
    private boolean mayClose(final List<SpreadSheetPanel> spreadsheets, final String message) {
        Resources resourceMap = Resources.of(Datavyu.class);
        String saveOption = "Save";
        String discardOption = "Don't save";
        String[] options = MacOS.getOptions(saveOption, discardOption, Dialogs.CANCEL);
        return UnsavedWork.mayClose(spreadsheets, spreadsheet -> spreadsheet.getProjectController().isChanged(),
            spreadsheet -> {
                datavyuView.getTabbedPane().setSelectedComponent(spreadsheet);
                String projectName = spreadsheet.getProjectController().getProjectName();
                if (projectName == null) {
                    projectName = spreadsheet.getDataStore().getName();
                }
                int selected = JOptionPane.showOptionDialog(mainFrame,
                    resourceMap.getString(message, projectName),
                    resourceMap.getString("UnsavedDialog.title"),
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, saveOption);
                return UnsavedWork.choice(selected, options, saveOption, discardOption);
            },
            spreadsheet -> datavyuView.save());
    }

    /**
     * Creates and shows the main window, then opens the file given on the command line.
     */
    private void start(final String[] args) {
        // Key repeat instead of the accent popup while holding keys (B2: this changes the setting for every app).
        if (MacOS.isOSXPressAndHoldEnabled()) {
            osxPressAndHoldEnabled = true;
            MacOS.setOSXPressAndHoldValue(false);
        }
        SpreadsheetTheme.installLookAndFeel();
        mainFrame = new JFrame();
        SpreadsheetTheme.setMonospacedValues(Settings.get().isMonospacedValues());
        logger.info("Palette: " + SpreadsheetTheme.PALETTE.name());
        MacOS.loadCompileHandler(this);
        if (args.length > 0) {
            commandLineFile = args[0];
        }

        datavyuView = new DatavyuView(this, mainFrame);
        if (!WindowPlacement.restoreAndTrack(mainFrame, MAIN_WINDOW, true)) {
            mainFrame.pack();
            mainFrame.setSize(Math.max(mainFrame.getWidth(), INIT_MIN_X), Math.max(mainFrame.getHeight(), INIT_MIN_Y));
            mainFrame.setLocationRelativeTo(null);
        }
        mainFrame.setVisible(true);

        datavyuView.startFirstSpreadsheet();
        datavyuView.checkForAutosavedFile();
        ready();
    }

    private void ready() {
        readyToOpenFile = true;
        if (commandLineFile != null) {
            datavyuView.openExternalFile(new File(commandLineFile));
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
}
