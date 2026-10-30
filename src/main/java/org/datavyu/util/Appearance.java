package org.datavyu.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Whether the Mac is in dark or light appearance, read once at startup. The {@value #PROPERTY} system property
 * overrides it with {@code light} or {@code dark}, for screenshots and for trying a palette without changing the Mac.
 */
public final class Appearance {

    /** System property that forces the appearance: {@code light} or {@code dark}. */
    public static final String PROPERTY = "watavyu.appearance";

    private static final Logger logger = LogManager.getLogger(Appearance.class);

    /** How long startup waits for macOS to say its appearance before taking light. */
    private static final long TIMEOUT_MILLIS = 5_000;

    private Appearance() {
    }

    /** Returns whether the application should use its dark palette. */
    public static boolean isDark() {
        return isDark(System.getProperty(PROPERTY), Appearance::readInterfaceStyle);
    }

    /**
     * Returns whether the application should use its dark palette.
     *
     * @param override       the {@value #PROPERTY} property, or null
     * @param interfaceStyle reads macOS's {@code AppleInterfaceStyle}, which is {@code Dark} in dark appearance and
     *                       missing in light appearance
     */
    static boolean isDark(final String override, final InterfaceStyle interfaceStyle) {
        if (override != null && !override.isBlank()) {
            return override.trim().toLowerCase(Locale.ROOT).equals("dark");
        }
        String style = interfaceStyle.read();
        return style != null && style.trim().equalsIgnoreCase("dark");
    }

    /** Reads macOS's interface style. */
    @FunctionalInterface
    interface InterfaceStyle {
        /** Returns the style, or null if it is not set. */
        String read();
    }

    private static String readInterfaceStyle() {
        return run(List.of("defaults", "read", "-g", "AppleInterfaceStyle"), TIMEOUT_MILLIS);
    }

    /**
     * Runs a command and returns what it printed, or null if it failed or did not finish in time. Its output goes to a
     * file rather than a pipe, so waiting for it can time out: reading a pipe would wait for the command to end.
     *
     * @param command       the command and its arguments
     * @param timeoutMillis how long to wait for it
     */
    static String run(final List<String> command, final long timeoutMillis) {
        Path output = null;
        try {
            output = Files.createTempFile("watavyu-appearance", ".txt");
            Process process = new ProcessBuilder(command)
                    .redirectOutput(output.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                logger.warn("Reading the macOS appearance timed out; using light");
                return null;
            }
            return process.exitValue() == 0 ? Files.readString(output, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            logger.warn("Cannot read the macOS appearance; using light", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException e) {
                    logger.debug("Cannot delete " + output, e);
                }
            }
        }
    }
}
