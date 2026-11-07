package org.datavyu.util;

import org.datavyu.Datavyu;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
     * Test whether the Apple press and hold functionality is enabled.
     *
     * @return True if press and hold is enabled; otherwise false.
     */
    public static boolean isOSXPressAndHoldEnabled() {
        try {
            List<String> output = defaults("read", "-g", "ApplePressAndHoldEnabled");
            return output.isEmpty() || !output.get(0).equals("0");
        } catch (IOException e) {
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
            List<String> output = defaults("write", "-g", "ApplePressAndHoldEnabled", "-bool",
                pressAndHold ? "true" : "false");
            logger.info("Press and hold for OSX command returned: " + String.join(System.lineSeparator(), output));
        } catch (IOException e) {
            logger.error("Error occurred when processing press and hold " + e);
        }
    }

    /** Runs the {@code defaults} command with these arguments, and returns the lines it prints, errors included. */
    private static List<String> defaults(final String... arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("defaults");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (BufferedReader output = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            return output.lines().toList();
        } catch (UncheckedIOException e) {
            // Reading the lines wraps a failure to read them.
            throw e.getCause();
        }
    }

    /**
     * Load compile handler for MacOS.
     *
     * @param application the running application, which the handlers ask to open files, show About and quit
     * @return True if load was successful; otherwise false.
     */
    public static boolean loadCompileHandler(final Datavyu application) {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }

        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(event -> application.showAboutWindow());
        }
        if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler(event -> event.getFiles().forEach(file -> {
                if (application.isReadyToOpenFile()) {
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
