package org.datavyu.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.beans.XMLDecoder;
import java.io.InputStream;

/**
 * Reads a JavaBeans XML settings file, skipping properties the bean no longer has.
 *
 * The Swing Application Framework's LocalStorage.load aborts on the first decoding error, so a settings file written
 * by an older version that still carries a removed property would be discarded entirely.
 */
public final class SettingsDecoder {

    private static Logger logger = LogManager.getLogger(SettingsDecoder.class);

    private SettingsDecoder() {}

    /**
     * Decodes the first object in the stream.
     *
     * @param inputStream The XML encoded bean; closed when done.
     *
     * @return The decoded object, with every readable property set.
     */
    public static Object decode(InputStream inputStream) {
        try (XMLDecoder decoder = new XMLDecoder(inputStream)) {
            decoder.setExceptionListener(e -> logger.warn("Skipping unreadable setting: " + e));
            return decoder.readObject();
        }
    }
}
