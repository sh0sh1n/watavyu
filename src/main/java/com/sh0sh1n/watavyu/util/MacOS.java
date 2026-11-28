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

    /**
     * The defaults domain of a JVM that the JDK's own {@code java} launcher runs, as under {@code ./gradlew run}: the
     * bundle identifier in the launcher's embedded Info.plist.
     */
    static final String JAVA_LAUNCHER_DOMAIN = "net.java.openjdk.java";

    /** The system property in which the app's launcher gives its bundle identifier (BUILDING.md › Distribution). */
    static final String BUNDLE_ID_PROPERTY = "watavyu.bundleId";

    /** The defaults key that turns the accent popup on or off while a key is held. */
    private static final String PRESS_AND_HOLD = "ApplePressAndHoldEnabled";

    /**
     * Turns off the accent popup shown while a key is held, for this app alone, so a held key repeats, as holding a
     * navigation chord or a jog key needs. It is set in the app's own defaults domain, and only if the user has not
     * set it there, never for every app. macOS reads it as the app starts, so it takes effect from the next launch,
     * and it is done on a thread of its own, so the launch does not wait for the {@code defaults} command.
     */
    public static void turnOffPressAndHoldInBackground() {
        Thread.ofPlatform().daemon().name("press-and-hold").start(MacOS::turnOffPressAndHold);
    }

    private static void turnOffPressAndHold() {
        String domain = pressAndHoldDomain(System.getProperty(BUNDLE_ID_PROPERTY));
        try {
            if (!isSet(domain, PRESS_AND_HOLD)) {
                Defaults written = defaults(turnOffPressAndHold(domain));
                logger.info("Press and hold turned off in " + domain + " (" + written.exitCode() + "): "
                    + String.join(" ", written.lines()));
            }
        } catch (IOException e) {
            logger.error("Could not turn off press and hold in " + domain + ": " + e);
        } catch (InterruptedException e) {
            logger.info("Press and hold left as it is in " + domain + ": interrupted");
        }
    }

    /**
     * Returns the defaults domain that holds this app's settings: its bundle identifier, which the app's launcher
     * gives, or outside the app that of the JDK's launcher.
     *
     * @param bundleId the app's bundle identifier, or null outside the app
     */
    static String pressAndHoldDomain(final String bundleId) {
        return bundleId == null || bundleId.isBlank() ? JAVA_LAUNCHER_DOMAIN : bundleId;
    }

    /** Returns the {@code defaults} arguments that turn press and hold off in a domain. */
    static List<String> turnOffPressAndHold(final String domain) {
        return List.of("write", domain, PRESS_AND_HOLD, "-bool", "false");
    }

    /**
     * Returns whether a defaults domain holds a value for a key. {@code defaults read} fails if it does not, worded
     * one way for a domain that does not exist and another for a key missing from one that does.
     *
     * @param domain the domain, such as {@code com.sh0sh1n.watavyu}
     * @param key    the key
     * @throws IOException          if {@code defaults} cannot be run
     * @throws InterruptedException if interrupted while it runs
     */
    static boolean isSet(final String domain, final String key) throws IOException, InterruptedException {
        return defaults(List.of("read", domain, key)).exitCode() == 0;
    }

    /**
     * What the {@code defaults} command did.
     *
     * @param exitCode its exit status, 0 when it did what was asked
     * @param lines    the lines it printed, errors included
     */
    record Defaults(int exitCode, List<String> lines) {
    }

    /** Runs the {@code defaults} command with these arguments, and waits for it to finish. */
    static Defaults defaults(final List<String> arguments) throws IOException, InterruptedException {
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
            return new Defaults(process.waitFor(), List.copyOf(lines));
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
