package org.datavyu.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.Datavyu;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Holds any MacOS specific functions.
 */
public class MacOS {
    /** The logger for this class */
    private static Logger logger = LogManager.getLogger(MacOS.class);

    /**
     * Get options for dialog on Mac with three responses.
     *
     * @param yesOption The option for yes.
     * @param noOption The option for no.
     * @param cancelOption The option for cancel.
     *
     * @return A string array with three options in the order: yesOption, cancelOption, noOption.
     */
    public static String[] getOptions(String yesOption, String noOption, String cancelOption) {
        return new String[]{yesOption, cancelOption, noOption};
    }

    /**
     * Get options for dialog on Mac with two responses.
     *
     * @param defaultOption The default option.
     * @param alternativeOption The alternative option.
     *
     * @return A string array with two options in the order: defaultOption, alternativeOption.
     */
    public static String[] getOptions(String defaultOption, String alternativeOption) {
        return new String[]{defaultOption, alternativeOption};
    }

    /**
     * Test whether the Apple press and hold functionality is enable.d
     *
     * @return True if press and hold is enabled; otherwise false.
     */
    public static boolean isOSXPressAndHoldEnabled() {
        try {
            Process process = new ProcessBuilder(new String[]{"bash", "-c", "defaults read -g ApplePressAndHoldEnabled"})
                    .redirectErrorStream(true)
                    .directory(new File("./"))
                    .start();
            ArrayList<String> output = new ArrayList<String>();
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                output.add(line);
            }
            return !(output.size() > 0 && output.get(0).equals("0"));
        } catch (Exception e) {
            logger.error("Error when checking press and hold on OSX " + e.getMessage());
        }
        return false;
    }

    /**
     * Sets the press and hold value.
     *
     * @param pressAndHold The press and hold state.
     */
    public static void setOSXPressAndHoldValue(boolean pressAndHold) {
        try {
            Process process = new ProcessBuilder(new String[]{
                    "bash", "-c", "defaults write -g ApplePressAndHoldEnabled -bool " + (pressAndHold ? "true" : "false")})
                    .redirectErrorStream(true)
                    .directory(new File("./"))
                    .start();
            StringBuilder stringBuilder = new StringBuilder();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line).append(System.getProperty("line.separator"));
            }
            logger.info("Press and hold for OSX command returned: " + stringBuilder);
        } catch (Exception e) {
            logger.error("Error occurred when processing press and hold " + e);
        }
    }

    /**
     * Load compile handler for MacOS.
     *
     * @return True if load was successful; otherwise false.
     */
    public static boolean loadCompileHandler() {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }

        Desktop desktop = Desktop.getDesktop();
        Datavyu application = Datavyu.getApplication();
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(event -> application.showAboutWindow());
        }
        if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler(event -> event.getFiles().forEach(file -> {
                if (application.readyToOpenFile) {
                    application.getView().openExternalFile(file);
                } else {
                    application.setCommandLineFile(file.getAbsolutePath());
                }
            }));
        }
        if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler((event, response) -> {
                if (application.safeQuit()) {
                    application.getMainFrame().setVisible(false);
                    application.shutdown();
                    response.performQuit();
                } else {
                    response.cancelQuit();
                }
            });
        }
        return true;
    }

}
