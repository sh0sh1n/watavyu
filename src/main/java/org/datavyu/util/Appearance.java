package org.datavyu.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
        try {
            Process process = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                    .redirectErrorStream(false)
                    .start();
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroy();
                return null;
            }
            return process.exitValue() == 0 ? output : null;
        } catch (IOException e) {
            logger.warn("Cannot read the macOS appearance; using light", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
