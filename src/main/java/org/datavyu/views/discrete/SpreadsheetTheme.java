package org.datavyu.views.discrete;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.util.Appearance;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Colours and fonts of the application. The palette follows the Mac's appearance at startup ({@link Palette}). Fonts
 * are IBM Plex, bundled: times and ordinals in Plex Mono, column headers in Plex Sans SemiBold, and cell values in
 * Plex Sans or Plex Mono as the {@code spreadsheetFont} setting says. The values' size changes with zoom for the rest
 * of the run; their face can change at any time, since cells read it each time they paint.
 */
public final class SpreadsheetTheme {

    /** The palette of this run. */
    public static final Palette PALETTE = Palette.forAppearance(Appearance.isDark());

    /** System property that stops FlatLaf loading its native library. */
    static final String FLATLAF_NATIVE_LIBRARY = "flatlaf.useNativeLibrary";

    /** Data font size at startup and after Reset Zoom, in points. */
    public static final int DEFAULT_DATA_FONT_SIZE = 14;

    private static final int LABEL_FONT_SIZE = 12;

    private static final int HEADER_FONT_SIZE = 13;

    private static final Logger logger = LogManager.getLogger(SpreadsheetTheme.class);

    private static final Font SANS = load("/fonts/IBMPlexSans-Regular.ttf", Font.SANS_SERIF);

    private static final Font SANS_SEMIBOLD = load("/fonts/IBMPlexSans-SemiBold.ttf", Font.SANS_SERIF);

    private static final Font MONO = load("/fonts/IBMPlexMono-Regular.ttf", Font.MONOSPACED);

    private static final Font LABEL_FONT = MONO.deriveFont((float) LABEL_FONT_SIZE);

    private static final Font HEADER_FONT = SANS_SEMIBOLD.deriveFont((float) HEADER_FONT_SIZE);

    private static volatile boolean monospacedValues;

    private static volatile Font dataFont = SANS.deriveFont((float) DEFAULT_DATA_FONT_SIZE);

    private SpreadsheetTheme() {
    }

    /** Returns the font of cell values. */
    public static Font dataFont() {
        return dataFont;
    }

    /**
     * Sets the size of the font of cell values, keeping its face.
     *
     * @param size New size of the font of cell values, in points.
     */
    public static void setDataFontSize(final int size) {
        dataFont = (monospacedValues ? MONO : SANS).deriveFont((float) size);
    }

    /**
     * Sets cell values in Plex Mono or Plex Sans, keeping their size.
     *
     * @param monospaced whether cell values are set in Plex Mono rather than Plex Sans; their size is kept
     */
    public static void setMonospacedValues(final boolean monospaced) {
        monospacedValues = monospaced;
        setDataFontSize(dataFont.getSize());
    }

    /** Returns whether cell values are set in Plex Mono. */
    public static boolean isMonospacedValues() {
        return monospacedValues;
    }

    /** Returns the font of cell ordinals, onsets and offsets. */
    public static Font labelFont() {
        return LABEL_FONT;
    }

    /** Returns the font of column headers. */
    public static Font headerFont() {
        return HEADER_FONT;
    }

    /**
     * Returns Plex Sans at that size, for labels in the controls drawer and dialogs.
     *
     * @param size its size, in points
     */
    public static Font uiFont(final float size) {
        return SANS.deriveFont(size);
    }

    /**
     * Returns Plex Mono at that size, for times outside cells.
     *
     * @param size its size, in points
     */
    public static Font timeFont(final float size) {
        return MONO.deriveFont(size);
    }

    /**
     * Installs FlatLaf's macOS look, light or dark with the palette, coloured from the palette, so menus, tabs,
     * fields, scroll bars and dialogs match the spreadsheet. Call on the EDT before any window is made.
     */
    public static void installLookAndFeel() {
        // FlatLaf's native library only adds window decorations Watavyu does not use; loading it is a restricted
        // call that Java 25 warns about.
        System.setProperty(FLATLAF_NATIVE_LIBRARY, "false");
        FlatLaf.setGlobalExtraDefaults(lookAndFeelDefaults(PALETTE));
        if (PALETTE.dark()) {
            FlatMacDarkLaf.setup();
        } else {
            FlatMacLightLaf.setup();
        }
    }

    /**
     * Returns FlatLaf's theme variables and keys for a palette, as a theme properties file would give them.
     *
     * @param palette the palette
     */
    static Map<String, String> lookAndFeelDefaults(final Palette palette) {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("@background", Palette.toHex(palette.chrome()));
        defaults.put("@foreground", Palette.toHex(palette.text()));
        defaults.put("@accentColor", Palette.toHex(palette.accent()));
        defaults.put("Component.borderColor", Palette.toHex(palette.rule()));
        defaults.put("Component.disabledBorderColor", Palette.toHex(palette.rule()));
        defaults.put("Separator.foreground", Palette.toHex(palette.rule()));
        defaults.put("Button.background", Palette.toHex(palette.cell()));
        defaults.put("Button.disabledBackground", Palette.toHex(palette.cell()));
        defaults.put("TextComponent.background", Palette.toHex(palette.cell()));
        defaults.put("TextField.background", Palette.toHex(palette.cell()));
        defaults.put("FormattedTextField.background", Palette.toHex(palette.cell()));
        defaults.put("TextArea.background", Palette.toHex(palette.cell()));
        defaults.put("TextPane.background", Palette.toHex(palette.cell()));
        defaults.put("Viewport.background", Palette.toHex(palette.ground()));
        defaults.put("ScrollPane.background", Palette.toHex(palette.ground()));
        defaults.put("ScrollBar.track", Palette.toHex(palette.ground()));
        defaults.put("TabbedPane.background", Palette.toHex(palette.chrome()));
        defaults.put("TabbedPane.selectedBackground", Palette.toHex(palette.ground()));
        defaults.put("TabbedPane.underlineColor", Palette.toHex(palette.accent()));
        defaults.put("TabbedPane.contentAreaColor", Palette.toHex(palette.rule()));
        defaults.put("MenuBar.background", Palette.toHex(palette.chrome()));
        defaults.put("ToolTip.background", Palette.toHex(palette.cell()));
        defaults.put("ToolTip.foreground", Palette.toHex(palette.text()));
        return defaults;
    }

    private static Font load(final String resource, final String fallbackFamily) {
        try (InputStream in = SpreadsheetTheme.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException(resource + " is not on the class path");
            }
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException | FontFormatException e) {
            logger.error("Cannot load " + resource + "; using " + fallbackFamily, e);
            return new Font(fallbackFamily, Font.PLAIN, DEFAULT_DATA_FONT_SIZE);
        }
    }
}
