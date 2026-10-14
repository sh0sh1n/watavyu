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
package org.datavyu.models.project;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.plugins.javafx.JavaFxVideoPlugin;

import java.io.*;
import java.util.UUID;


/**
 * Stores user settings for a data viewer
 */
public final class ViewerSetting {

    /** Logger for this class */
    private static Logger logger = LogManager.getLogger(ViewerSetting.class);

    /** Track settings associated with this data viewer */
    private TrackSettings trackSettings;

    /** Fully qualified name of the plugin */
    private String pluginName;

    /** Fully qualified UUID of the plugin */
    private UUID pluginUUID;

    /** Plugin classifier */
    // TODO: Consider refactoring this name. I remember this has to do with the file name matching to plugin but unused.
    private String pluginClassifier;

    /** Absolute file path to the data source */
    private String filePath;

    /** Playback offset in milliseconds */
    private long offset;

    /** Identifier of settings file */
    private String settingsId;

    /** The settings data */
    private byte[] settingsData;

    /** Whether a track can be opened, now that the OpenJFX player is the only one. */
    public enum PlayerMatch {
        /** The track was recorded with the OpenJFX player. */
        SAME_PLAYER,
        /** The track was recorded with another video player; it opens with the OpenJFX player after a notice. */
        OTHER_VIDEO_PLAYER,
        /** The track was recorded with a viewer of another kind, or the project does not say; it cannot open. */
        NONE
    }

    public ViewerSetting() { }

    /**
     * @return Whether the OpenJFX player can open this track.
     */
    public PlayerMatch playerMatch() {
        if (JavaFxVideoPlugin.VIEWER_CLASS_NAME.equals(pluginName)) {
            return PlayerMatch.SAME_PLAYER;
        }
        return JavaFxVideoPlugin.NAMESPACE.equals(pluginClassifier) ? PlayerMatch.OTHER_VIDEO_PLAYER : PlayerMatch.NONE;
    }

    /**
     * @return track settings associated with this data viewer.
     */
    public TrackSettings getTrackSettings() {
        return trackSettings;
    }

    /**
     * @param trackSettings track settings used by this data viewer.
     */
    public void setTrackSettings(final TrackSettings trackSettings) {
        this.trackSettings = trackSettings;
    }

    /**
     * @return Absolute file path to the data source.
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * @param filePath Absolute file path to the data source.
     */
    public void setFilePath(final String filePath) {
        this.filePath = filePath;
    }

    /**
     * Retained for backwards compatibility.
     *
     * @return
     */
    @Deprecated
    public long getOffset() {
        return offset;
    }

    /**
     * Retained for backwards compatibility.
     *
     * @param offset
     */
    @Deprecated
    public void setOffset(final long offset) {
        this.offset = offset;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(final String pluginName) {
        this.pluginName = pluginName;
        if (pluginUUID == null && JavaFxVideoPlugin.VIEWER_CLASS_NAME.equals(pluginName)) {
            setPluginNameAndUUID(JavaFxVideoPlugin.PLUGIN_UUID);
        }
    }

    /**
     * The method will find the plugin name associated with
     * the pluginUUID
     *
     * @param pluginUUID plugin UUID
     */
    public void setPluginNameAndUUID(final UUID pluginUUID) {
        this.pluginUUID =  pluginUUID;
        if (pluginName == null && JavaFxVideoPlugin.PLUGIN_UUID.equals(pluginUUID)) {
            setPluginName(JavaFxVideoPlugin.VIEWER_CLASS_NAME);
        }
    }

    /**
     * @param pluginClassifier the pluginClassifier to set
     */
    public void setPluginClassifier(final String pluginClassifier) {
        this.pluginClassifier = pluginClassifier;
    }

    /**
     * @return String identifier for these settings.
     */
    public String getSettingsId() {
        return settingsId;
    }

    /**
     * Set the identifier for these settings.
     *
     * @param settingsId Identifier to use.
     */
    public void setSettingsId(final String settingsId) {
        this.settingsId = settingsId;
    }

    /**
     * Copy viewer settings from the given input stream into an internal buffer.
     * The settings can be read using {@link #getSettingsInputStream()}.
     *
     * @param is InputStream to copy from.
     */
    public void copySettings(final InputStream is) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            IOUtils.copy(is, os);
            settingsData = os.toByteArray();
        } catch (IOException e) {
            logger.error("Failed to save the copy settings. Error: ", e);
        }
        IOUtils.closeQuietly(os);
    }

    /**
     * @return InputStream to use for reading settings.
     */
    public InputStream getSettingsInputStream() {
        return new ByteArrayInputStream(settingsData);
    }
}
