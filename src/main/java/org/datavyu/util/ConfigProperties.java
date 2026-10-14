/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.datavyu.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.Datavyu;
import org.jdesktop.application.LocalStorage;

import java.awt.*;
import java.io.*;

/**
 * Configuration properties that are loaded from the settings.xml file in the resource folder.
 *
 * This file gets copied from the resource folder to the local temp directory where this application managers its
 * settings through the Swing Application Framework.
 */
public final class ConfigProperties implements Serializable {

    /** Unique identifier for this serial version */
    private static final long serialVersionUID = 4L;

    /** The colour to use for the border */
    public static final Color DEFAULT_BORDER_COLOUR = new Color(175, 175, 175);

    /** Default data font size */
    private static final float DEFAULT_DATA_FONT_SIZE = 14;

    /** Default label font size */
    private static final float DEFAULT_LABEL_FONT_SIZE = 12;

    /** Default font */
    private static final Font DEFAULT_SPREAD_SHEET_DATA_FONT = new Font("Arial", Font.PLAIN, 14);

    /** Spread sheet data font */
    private Font spreadSheetDataFont;

    /** Default font for labels */
    private static final Font DEFAULT_SPREAD_SHEET_LABEL_FONT = new Font("Arial", Font.PLAIN, 12);

    /** Spread sheet label font */
    private Font spreadSheetLabelFont;

    /** Default spreadsheet background color */
    private static final Color DEFAULT_SPREAD_SHEET_BACKGROUND_COLOR = new Color(249, 249, 249);

    /** Spread sheet background color */
    private Color spreadSheetBackgroundColor;

    /** Default spreadsheet foreground color */
    private static final Color DEFAULT_SPREAD_SHEET_FOREGROUND_COLOR = new Color(58, 58, 58);

    /** Spreadsheet foreground color */
    private Color spreadSheetForegroundColor;

    /** Default spreadsheet ordinal foreground colour */
    private static final Color DEFAULT_SPREAD_SHEET_ORDINAL_FOREGROUND_COLOR = new Color(175, 175, 175);

    /** Foreground color for the spreadsheet ordinal */
    private Color spreadSheetOrdinalForegroundColor;

    /** Default spreadsheet time stamp foreground color */
    private static final Color DEFAULT_SPREAD_SHEET_TIME_STAMP_FOREGROUND_COLOR = new Color(90, 90, 90);

    /** Foreground color of for spreadsheet timestamp */
    private Color spreadSheetTimeStampForegroundColor;

    /** Default spreadsheet selected color */
    private static final Color DEFAULT_SPREAD_SHEET_SELECTED_COLOR = new Color(176, 197, 227);

    /** Spreadsheet selection color */
    private Color spreadSheetSelectedColor;

    /** Default spreadsheet overlap color */
    private static final Color DEFAULT_SPREAD_SHEET_OVERLAP_COLOR = Color.RED;

    /** Spreadsheet overlap color */
    private Color spreadSheetOverlapColor;

    /** Last chosen directory */
    private String lastChosenDirectory;

    /** Default value for warn on column names */
    private static final boolean DO_WARN_ON_COLUMN_NAMES = true;

    /** True if column name warnings should be displayed */
    private boolean doWarnOnIllegalColumnNames;

    /** Default for favorites folder */
    private static final String DEFAULT_FAVORITES_FOLDER = "favorites";

    /** Favorites folder */
    private String favoritesFolder;

    /** Default conversion url */
    private static final String DEFAULT_CONVERSION_URL = "https://handbrake.fr";

    /** URL for the conversion url */
    private String conversionUrl;

    /** This is the only instance for the configuration properties that is loaded at start-up */
    private static ConfigProperties configurationProperties = new ConfigProperties();

    /** The logger for this class */
    private static Logger logger = LogManager.getLogger(ConfigProperties.class);

    static {
        LocalStorage localStorage = Datavyu.getApplication().getContext().getLocalStorage();
        String localDirectory = localStorage.getDirectory().getAbsolutePath();

        // Copy the settings.xml file from the resources to the tmp folder where the Swing Application Framework
        // loads and stores the *.properties and *.xml files for this application with user defined properties.
        try {
            // It is important that the path into the resource with "/"
            File configurationFile = new File(localDirectory + "/" + Constants.CONFIGURATION_FILE);
            // Don't Copy the original settings.xml if already exists
            if (!configurationFile.exists()) {
                logger.info("Copying " + Constants.CONFIGURATION_FILE + " to " + localDirectory);

                configurationFile.getParentFile().mkdirs();
                InputStream inputStream = configurationProperties.getClass().getResourceAsStream(
                    "/" + Constants.CONFIGURATION_FILE);
                FileOutputStream fileOutputStream = new FileOutputStream(new File(localDirectory,
                    Constants.CONFIGURATION_FILE));
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(
                    fileOutputStream,
                    Constants.BUFFER_COPY_SIZE);
                int count;
                byte[] data = new byte[Constants.BUFFER_COPY_SIZE];
                while ((count = inputStream.read(data, 0, Constants.BUFFER_COPY_SIZE)) != -1) {
                    bufferedOutputStream.write(data, 0, count);
                }
                bufferedOutputStream.close();
                fileOutputStream.close();
                inputStream.close();
            }
        } catch (IOException io) {
            logger.error("Could not copy resource for settings " + io.getMessage());
        }

        // Try to load the configuration
        try {
            logger.info("Configuration loaded from directory " + localDirectory);
            configurationProperties = (ConfigProperties) SettingsDecoder.decode(
                localStorage.openInputFile(Constants.CONFIGURATION_FILE));
            logger.info("Loaded configuration properties: " + configurationProperties);
        } catch (IOException | RuntimeException io) {
            logger.error("Unable to load configuration file " + io.getMessage());
            logger.info("Setting default properties.");
            configurationProperties = new ConfigProperties();
        }

        // If values are not set/loaded set their defaults
        if (!configurationProperties.hasSpreadSheetDataFont()) {
            configurationProperties.setSpreadSheetDataFont(DEFAULT_SPREAD_SHEET_DATA_FONT);
        }
        if (!configurationProperties.hasSpreadSheetLabelFont()) {
            configurationProperties.setSpreadSheetLabelFont(DEFAULT_SPREAD_SHEET_LABEL_FONT);
        }
        if (!configurationProperties.hasSpreadSheetSelectedColor()) {
            configurationProperties.setSpreadSheetSelectedColor(DEFAULT_SPREAD_SHEET_SELECTED_COLOR);
        }
        if (!configurationProperties.hasSpreadSheetOverlapColor()) {
            configurationProperties.setSpreadSheetOverlapColor(DEFAULT_SPREAD_SHEET_OVERLAP_COLOR);
        }
        configurationProperties.setDoWarnOnIllegalColumnNames(DO_WARN_ON_COLUMN_NAMES);

        if (!configurationProperties.hasFavoritesFolder()) {
            configurationProperties.setFavoritesFolder(DEFAULT_FAVORITES_FOLDER);
        }
        if (!configurationProperties.hasConversionUrl()) {
            configurationProperties.setConversionUrl(DEFAULT_CONVERSION_URL);
        }
        if (!configurationProperties.hasSpreadSheetOrdinalForegroundColor()) {
            configurationProperties.setSpreadSheetOrdinalForegroundColor(DEFAULT_SPREAD_SHEET_ORDINAL_FOREGROUND_COLOR);
        }
        if (!configurationProperties.hasSpreadSheetTimeStampForegroundColor()) {
            configurationProperties.setSpreadSheetTimeStampForegroundColor(DEFAULT_SPREAD_SHEET_TIME_STAMP_FOREGROUND_COLOR);
        }
        if (!configurationProperties.hasSpreadSheetBackgroundColor()) {
            configurationProperties.setSpreadSheetBackgroundColor(DEFAULT_SPREAD_SHEET_BACKGROUND_COLOR);
        }
        if (!configurationProperties.hasSpreadSheetForegroundColor()) {
            configurationProperties.setSpreadSheetForegroundColor(DEFAULT_SPREAD_SHEET_FOREGROUND_COLOR);
        }
        try {
            Font defaultFont = Font.createFont(Font.TRUETYPE_FONT,
                    configurationProperties.getClass().getResourceAsStream(Constants.DEFAULT_FONT_FILE));
            Font defaultCellFont = Font.createFont(Font.TRUETYPE_FONT,
                    configurationProperties.getClass().getResourceAsStream(Constants.DEFAULT_CELL_FONT_FILE));
            configurationProperties.setSpreadSheetDataFont(defaultCellFont.deriveFont(DEFAULT_DATA_FONT_SIZE));
//            configurationProperties.setSpreadSheetDataFont(defaultFont.deriveFont(DEFAULT_DATA_FONT_SIZE));
            configurationProperties.setSpreadSheetLabelFont(defaultFont.deriveFont(DEFAULT_LABEL_FONT_SIZE));
        } catch (Exception e) {
            logger.error("Error, unable to load font " + Constants.DEFAULT_FONT_FILE + ". The error is " + e);
        }

        // In all cases save this setting for the next run
        save();
    }

    /**
     * Set the default values for all properties.
     *
     * Public to be accessible by the XMLReader for JavaBeans to create an object instance.
     */
    public ConfigProperties() {}

    /**
     * Get the static instance for the configuration.
     *
     * @return The configuration properties.
     */
    public static ConfigProperties getInstance() {
        return configurationProperties;
    }

    /**
     * Saves the configuration properties in local storage using the Swing Application Framework.
     */
    public static void save() {
        try {
            LocalStorage ls = Datavyu.getApplication().getContext().getLocalStorage();
            logger.info("Saving configuration properties " + configurationProperties + " to: " + ls.getDirectory());
            ls.save(configurationProperties, Constants.CONFIGURATION_FILE);
        } catch (IOException e) {
            logger.error("Unable to save configuration " + e.getMessage());
        }
    }

    /**
     * Get the spread sheet data font.
     *
     * @return The spreadsheet data font.
     */
    public Font getSpreadSheetDataFont() {
        return spreadSheetDataFont;
    }

    /**
     * Sets the spread sheet data font.
     *
     * @param font to use for spread sheet data.
     */
    public void setSpreadSheetDataFont(final Font font) {
        spreadSheetDataFont = font;
    }

    /**
     * Sets the spread sheet data font size.
     *
     * @param size to set.
     */
    public void setSpreadSheetDataFontSize(final float size) {
        setSpreadSheetDataFont(getSpreadSheetDataFont().deriveFont(size));
    }

    /**
     * Did we set the spread sheet data font?
     *
     * @return Returns true if the spread sheet data font is set; otherwise false.
     */
    public boolean hasSpreadSheetDataFont() {
        return spreadSheetDataFont != null;
    }

    /**
     * Get the spread sheet label font.
     *
     * @return The spread sheet data font.
     */
    public Font getSpreadSheetLabelFont() {
        return spreadSheetLabelFont;
    }

    /**
     * Sets the spreadsheet data font.
     *
     * @param font to use for spread sheet data.
     */
    public void setSpreadSheetLabelFont(final Font font) {
        spreadSheetLabelFont = font;
    }

    /**
     * Did we set the spread sheet label font?
     *
     * @return True if the spread sheet label font; otherwise false.
     */
    public boolean hasSpreadSheetLabelFont() {
        return spreadSheetLabelFont != null;
    }

    /**
     * Get the spread sheet background color.
     *
     * @return spread sheet background color.
     */
    public Color getSpreadSheetBackgroundColor() {
        return spreadSheetBackgroundColor;
    }

    /**
     * Sets the spread sheet background colour.
     *
     * @param color to use for the spread sheet background.
     */
    public void setSpreadSheetBackgroundColor(final Color color) {
        spreadSheetBackgroundColor = color;
    }

    /**
     * Did we set the spread sheet background color?
     *
     * @return True if the spread sheet background color was set; otherwise False.
     */
    public boolean hasSpreadSheetBackgroundColor() {
        return spreadSheetBackgroundColor != null;
    }

    /**
     * Get the spread sheet foreground color.
     *
     * @return spread sheet foreground color.
     */
    public Color getSpreadSheetForegroundColor() {
        return spreadSheetForegroundColor;
    }

    /**
     * Sets the spreadsheet foreground color.
     *
     * @param color to use for the spreadsheet foreground.
     */
    public void setSpreadSheetForegroundColor(final Color color) {
        spreadSheetForegroundColor = color;
    }

    /**
     * Did we set the spread sheet foreground color?
     *
     * @return The spread sheet foreground color.
     */
    public boolean hasSpreadSheetForegroundColor() {
        return spreadSheetForegroundColor != null;
    }

    /**
     * Get the spread sheet ordinal foreground color.
     *
     * @return The spread sheet ordinal foreground colour.
     */
    public Color getSpreadSheetOrdinalForegroundColor() {
        return spreadSheetOrdinalForegroundColor;
    }

    /**
     * Sets the spreadsheet ordinal foreground color.
     *
     * @param color to use for the spreadsheet foreground.
     */
    public void setSpreadSheetOrdinalForegroundColor(final Color color) {
        spreadSheetOrdinalForegroundColor = color;
    }

    /**
     * Did we set the spread sheet ordinal foreground color?
     *
     * @return True if we set the spread sheet ordinal foreground color; otherwise False.
     */
    public boolean hasSpreadSheetOrdinalForegroundColor() {
        return spreadSheetOrdinalForegroundColor != null;
    }

    /**
     * Sets the spread sheet time tamp foreground color.
     *
     * @param color to use for the spread sheet foreground.
     */
    public void setSpreadSheetTimeStampForegroundColor(final Color color) {
        spreadSheetTimeStampForegroundColor = color;
    }

    /**
     * Did we set the spread sheet time stamp foreground color?
     *
     * @return True if we set the spread sheet time stamp foreground color; otherwise False.
     */
    public boolean hasSpreadSheetTimeStampForegroundColor() {
        return spreadSheetTimeStampForegroundColor != null;
    }

    /**
     * Get the spread sheet time stamp foreground color.
     *
     * @return The spread sheet timestamp foreground colour.
     */
    public Color getSpreadSheetTimeStampForegroundColor() {
        return spreadSheetTimeStampForegroundColor;
    }

    /**
     * Get the spread sheet selected color.
     *
     * @return The spread sheet selections color.
     */
    public Color getSpreadSheetSelectedColor() {
        return spreadSheetSelectedColor;
    }

    /**
     * Sets the spread sheet selected color.
     *
     * @param color to use for spread sheet selections.
     */
    public void setSpreadSheetSelectedColor(final Color color) {
        spreadSheetSelectedColor = color;
    }

    /**
     * Did we set the spread sheet selected color?
     *
     * @return True if we set the spread sheet selected color; otherwise False.
     */
    public boolean hasSpreadSheetSelectedColor() {
        return spreadSheetSelectedColor != null;
    }

    /**
     * Get the spread sheet overlap color.
     *
     * @return The spread sheet overlap color.
     */
    public Color getSpreadSheetOverlapColor() {
        return spreadSheetOverlapColor;
    }

    /**
     * Sets the spread sheet overlap color.
     *
     * @param color to use for spread sheet overlap color.
     */
    public void setSpreadSheetOverlapColor(final Color color) {
        spreadSheetOverlapColor = color;
    }

    /**
     * Did we set the spread sheet overlap color?
     *
     * @return True if we set the spread sheet overlap color; otherwise False.
     */
    public boolean hasSpreadSheetOverlapColor() {
        return spreadSheetOverlapColor != null;
    }

    /**
     * Get the last chosen directory.
     *
     * @return The last chosen directory; null if none was ever chosen.
     */
    public String getLastChosenDirectory() {
        return lastChosenDirectory;
    }

    /**
     * Sets the last chosen directory.
     *
     * @param directory last chosen.
     */
    public void setLastChosenDirectory(final String directory) {
        lastChosenDirectory = directory;
    }

    /**
     * The directory file choosers open in: the last chosen directory if it still exists, otherwise the user's home.
     *
     * Not a bean property, so the fallback is never written to the settings file.
     *
     * @return The directory to start in.
     */
    public File resolveStartDirectory() {
        return FileSystemUtils.existingDirectoryOr(lastChosenDirectory, new File(System.getProperty("user.home")));
    }

    /**
     * Get the flag for that we set to warn on illegal column names.
     *
     * @return whether or not to display warnings for illegal column names
     */    
    public boolean getDoWarnOnIllegalColumnNames()
    {
        return doWarnOnIllegalColumnNames;
    }
    
    /**
     * Set the warn flag for illegal column names.
     *
     * @param doWarn whether or not to display warnings for illegal column names.
     */    
    public void setDoWarnOnIllegalColumnNames(final boolean doWarn) {
        doWarnOnIllegalColumnNames = doWarn;
    }

    /**
     * Get the favorites folder.
     *
     * @return favorites folder is returned.
     */
    public String getFavoritesFolder(){
        return favoritesFolder;
    }

    /**
     * Set the favorites folder.
     *
     * @param favoritesFolder for the favorites folder.
     */
    public void setFavoritesFolder(String favoritesFolder){
        this.favoritesFolder = favoritesFolder;
    }

    /**
     * Did we set the favorites folder?
     *
     * @return True if the favorites folder was set; otherwise False.
     */
    public boolean hasFavoritesFolder() {
        return favoritesFolder != null;
    }

    /**
     * Get the conversion url.
     *
     * @return conversion url.
     */
    public String getConversionUrl() {
        return conversionUrl;
    }

    /**
     * Sets the conversion url.
     *
     * @param conversionUrl The conversion url.
     */
    public void setConversionUrl(String conversionUrl) {
        this.conversionUrl = conversionUrl;
    }

    /**
     * Did we set the conversion URL?
     *
     * @return True if we set the url; otherwise false.
     */
    public boolean hasConversionUrl() {
        return conversionUrl != null;
    }
}
