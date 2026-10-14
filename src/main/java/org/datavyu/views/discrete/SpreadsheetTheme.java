package org.datavyu.views.discrete;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Colors and fonts of the spreadsheet. The data font's size changes with zoom for the rest of the run; nothing here
 * is saved.
 */
public final class SpreadsheetTheme {

    public static final Color BACKGROUND_COLOR = new Color(249, 249, 249);

    /** Cell values. */
    public static final Color FOREGROUND_COLOR = new Color(58, 58, 58);

    /** Cell ordinals. */
    public static final Color ORDINAL_FOREGROUND_COLOR = new Color(175, 175, 175);

    /** Cell onsets and offsets. */
    public static final Color TIMESTAMP_FOREGROUND_COLOR = new Color(90, 90, 90);

    public static final Color SELECTED_COLOR = new Color(176, 197, 227);

    /** Timestamps of cells that overlap the cell before them. */
    public static final Color OVERLAP_COLOR = Color.RED;

    public static final Color BORDER_COLOR = new Color(175, 175, 175);

    /** Data font size at startup and after Reset Zoom, in points. */
    public static final int DEFAULT_DATA_FONT_SIZE = 14;

    private static final int LABEL_FONT_SIZE = 12;

    private static final String FONT_RESOURCE = "/fonts/DejaVuSansCondensed.ttf";

    private static final Logger logger = LogManager.getLogger(SpreadsheetTheme.class);

    private static final Font BASE_FONT = loadBaseFont();

    private static final Font LABEL_FONT = BASE_FONT.deriveFont((float) LABEL_FONT_SIZE);

    private static volatile Font dataFont = BASE_FONT.deriveFont((float) DEFAULT_DATA_FONT_SIZE);

    private SpreadsheetTheme() {
    }

    /**
     * @return The font of cell values.
     */
    public static Font dataFont() {
        return dataFont;
    }

    /**
     * @param size New size of the font of cell values, in points.
     */
    public static void setDataFontSize(final int size) {
        dataFont = BASE_FONT.deriveFont((float) size);
    }

    /**
     * @return The font of cell ordinals, onsets and offsets, and of column headers.
     */
    public static Font labelFont() {
        return LABEL_FONT;
    }

    private static Font loadBaseFont() {
        try (InputStream in = SpreadsheetTheme.class.getResourceAsStream(FONT_RESOURCE)) {
            if (in == null) {
                throw new IOException(FONT_RESOURCE + " is not on the class path");
            }
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException | FontFormatException e) {
            logger.error("Cannot load " + FONT_RESOURCE + "; using the default font", e);
            return new Font(Font.SANS_SERIF, Font.PLAIN, DEFAULT_DATA_FONT_SIZE);
        }
    }
}
