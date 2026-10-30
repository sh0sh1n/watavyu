package org.datavyu.util;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * User-interface text for a class, from the properties file {@code resources/<ClassName>.properties} in
 * the class's package. Keys the file lacks fall back to the application-wide
 * {@code org/datavyu/resources/Datavyu.properties}.
 */
public final class Resources {

    private static final String APPLICATION_BUNDLE = "org.datavyu.resources.Datavyu";

    private final ResourceBundle bundle;

    private final ResourceBundle application;

    private Resources(final Class<?> owner) {
        this.bundle = bundleOrNull(owner.getPackageName() + ".resources." + owner.getSimpleName());
        this.application = ResourceBundle.getBundle(APPLICATION_BUNDLE);
    }

    /**
     * @param owner The class whose resources to read.
     * @return Its resources.
     */
    public static Resources of(final Class<?> owner) {
        return new Resources(owner);
    }

    /**
     * @param key  The key.
     * @param args Values for {@link String#format} placeholders in the text, if any.
     * @return The text, formatted when arguments are given, or null if neither file has the key.
     */
    public String getString(final String key, final Object... args) {
        String text = lookup(key);
        return text == null || args.length == 0 ? text : String.format(text, args);
    }

    private String lookup(final String key) {
        for (ResourceBundle candidate : new ResourceBundle[]{bundle, application}) {
            if (candidate != null && candidate.containsKey(key)) {
                return candidate.getString(key).trim();
            }
        }
        return null;
    }

    private static ResourceBundle bundleOrNull(final String name) {
        try {
            return ResourceBundle.getBundle(name);
        } catch (MissingResourceException e) {
            return null;
        }
    }
}
