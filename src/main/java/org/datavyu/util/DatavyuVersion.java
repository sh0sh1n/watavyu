package org.datavyu.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * The application version, stamped from the Gradle project version at build time.
 */
public final class DatavyuVersion {

    private static final String VERSION_RESOURCE = "/org/datavyu/version.properties";

    private static final String VERSION = load();

    private DatavyuVersion() {}

    /**
     * Get the application version.
     *
     * @return The version, for example "0.1.0-SNAPSHOT".
     */
    public static String getVersion() {
        return VERSION;
    }

    private static String load() {
        try (InputStream in = DatavyuVersion.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + VERSION_RESOURCE);
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + VERSION_RESOURCE, e);
        }
    }
}
