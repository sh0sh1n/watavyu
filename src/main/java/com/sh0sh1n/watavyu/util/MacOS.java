package com.sh0sh1n.watavyu.util;

import com.sh0sh1n.watavyu.Watavyu;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

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

    /** The app's bundle identifier, the defaults domain of the app as a bundle (build.gradle.kts › Distribution). */
    public static final String BUNDLE_ID = "com.sh0sh1n.watavyu";

    /** The defaults key that turns the accent popup on or off while a key is held. */
    private static final String PRESS_AND_HOLD = "ApplePressAndHoldEnabled";

    /**
     * Turns off the accent popup shown while a key is held, for this app alone, so a held key repeats, as holding a
     * navigation chord or a jog key needs. It is set in the app's own defaults domain, and only if the user has not
     * set it there, never for every app; macOS reads it as the app starts, so it takes effect from the next launch.
     */
    public static void turnOffPressAndHold() {
        String domain = pressAndHoldDomain(System.getProperty("jpackage.app-path"));
        try {
            List<String> read = defaults(readPressAndHold(domain));
            if (isUnset(read)) {
                List<String> output = defaults(turnOffPressAndHold(domain));
                logger.info("Press and hold turned off in " + domain + ": " + String.join(" ", output));
            }
        } catch (IOException e) {
            logger.error("Could not turn off press and hold in " + domain + ": " + e);
        }
    }

    /**
     * Returns the defaults domain that holds this app's settings: its bundle identifier when it runs as the app,
     * whose launcher names itself in {@code jpackage.app-path}, and otherwise {@code java}, the name of the program a
     * bare JVM runs as, as under {@code ./gradlew run}.
     *
     * @param appPath the app launcher's path, or null outside the app
     */
    static String pressAndHoldDomain(final String appPath) {
        return appPath == null ? "java" : BUNDLE_ID;
    }

    /** Returns the {@code defaults} arguments that read press and hold in a domain. */
    static List<String> readPressAndHold(final String domain) {
        return List.of("read", domain, PRESS_AND_HOLD);
    }

    /** Returns the {@code defaults} arguments that turn press and hold off in a domain. */
    static List<String> turnOffPressAndHold(final String domain) {
        return List.of("write", domain, PRESS_AND_HOLD, "-bool", "false");
    }

    /** Returns whether {@code defaults read} found no value, which it says on its error output. */
    static boolean isUnset(final List<String> readOutput) {
        return readOutput.stream().anyMatch(line -> line.contains("does not exist"));
    }

    /** Runs the {@code defaults} command with these arguments, and returns the lines it prints, errors included. */
    private static List<String> defaults(final List<String> arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("defaults");
        command.addAll(arguments);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (BufferedReader output = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            for (String line = output.readLine(); line != null; line = output.readLine()) {
                lines.add(line);
            }
            return lines;
        }
    }

    /**
     * Load compile handler for MacOS.
     *
     * @param application the running application, which the handlers ask to open files, show About and quit
     * @return True if load was successful; otherwise false.
     */
    public static boolean loadCompileHandler(final Watavyu application) {
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
                    application.getWindow().openExternalFile(file);
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
