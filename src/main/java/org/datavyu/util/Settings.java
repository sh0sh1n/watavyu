package org.datavyu.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The user's settings: the last folder chosen in a file dialog, recently opened projects, window positions, and how
 * playback and the spreadsheet look. They are stored as JSON in {@code settings.json} under
 * {@code ~/Library/Application Support/Watavyu}, or under the directory named by the {@value #DIRECTORY_PROPERTY}
 * system property, which the tests set so they never touch the user's settings.
 *
 * <p>A missing or unreadable file gives the defaults. Changes stay in memory until {@link #save()}.
 */
public final class Settings {

    /** System property naming the directory that holds the settings file. */
    public static final String DIRECTORY_PROPERTY = "watavyu.settingsDir";

    /** Number of recently opened projects remembered. */
    public static final int RECENT_PROJECT_LIMIT = 5;

    private static final String FILE_NAME = "settings.json";

    /** How far past cells fade unless set otherwise, a trial value until the owner picks one (U5). */
    public static final int DEFAULT_PAST_CELL_DIM = 40;

    private static final String SANS = "sans";

    private static final String MONO = "mono";

    private static final Logger logger = LogManager.getLogger(Settings.class);

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static Settings instance;

    private final Path file;

    private final Data data;

    private Settings(final Path file, final Data data) {
        this.file = file;
        this.data = data;
    }

    /**
     * @return The settings of this run, loaded on first use.
     */
    public static synchronized Settings get() {
        if (instance == null) {
            instance = load(defaultFile());
        }
        return instance;
    }

    /**
     * @return The settings file this run uses.
     */
    static Path defaultFile() {
        String directory = System.getProperty(DIRECTORY_PROPERTY);
        Path base = directory != null && !directory.isEmpty()
                ? Paths.get(directory)
                : Paths.get(System.getProperty("user.home"), "Library", "Application Support", "Watavyu");
        return base.resolve(FILE_NAME);
    }

    /**
     * Reads settings from a file.
     *
     * @param file The settings file. It need not exist.
     * @return The settings in the file, or the defaults if it is missing or cannot be read.
     */
    public static Settings load(final Path file) {
        if (Files.isRegularFile(file)) {
            try {
                Data data = MAPPER.readValue(file.toFile(), Data.class);
                return new Settings(file, data.normalized());
            } catch (IOException | RuntimeException e) {
                logger.warn("Cannot read settings from " + file + "; using defaults", e);
            }
        }
        return new Settings(file, new Data());
    }

    /**
     * Writes the settings to their file, creating its directory if needed. A failure is logged, not thrown: losing
     * these settings must never stop the application from closing.
     */
    public synchronized void save() {
        Path temporary = null;
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            temporary = Files.createTempFile(file.toAbsolutePath().getParent(), FILE_NAME, ".tmp");
            MAPPER.writeValue(temporary.toFile(), data);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            logger.error("Cannot save settings to " + file, e);
            deleteQuietly(temporary);
        }
    }

    private static void deleteQuietly(final Path temporary) {
        if (temporary != null) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException e) {
                logger.warn("Cannot delete " + temporary, e);
            }
        }
    }

    /**
     * @return The folder last chosen in a file dialog, or null if none was.
     */
    public synchronized String getLastChosenDirectory() {
        return data.lastChosenDirectory;
    }

    public synchronized void setLastChosenDirectory(final String directory) {
        data.lastChosenDirectory = directory;
    }

    /**
     * Where a file dialog opens: the folder last chosen if it still exists, otherwise the user's home. The fallback
     * is not remembered, so a folder on a disconnected drive is used again once it is back.
     *
     * @return An existing directory.
     */
    public synchronized File resolveStartDirectory() {
        return FileSystemUtils.existingDirectoryOr(data.lastChosenDirectory, new File(System.getProperty("user.home")));
    }

    /**
     * @return Recently opened or saved projects, most recent first.
     */
    public synchronized List<File> getRecentProjects() {
        List<File> files = new ArrayList<>();
        for (String path : data.recentProjects) {
            files.add(new File(path));
        }
        return files;
    }

    /**
     * Moves a project to the front of the recent projects, dropping the oldest beyond {@link #RECENT_PROJECT_LIMIT}.
     *
     * @param project The project or data file opened or saved.
     */
    public synchronized void rememberProject(final File project) {
        String path = project.getAbsolutePath();
        data.recentProjects.remove(path);
        data.recentProjects.add(0, path);
        while (data.recentProjects.size() > RECENT_PROJECT_LIMIT) {
            data.recentProjects.remove(data.recentProjects.size() - 1);
        }
    }

    /**
     * @return Whether playback stops at the end of the last media clip; true unless turned off.
     */
    public synchronized boolean isStopAtEndOfMedia() {
        return data.stopAtEndOfMedia;
    }

    public synchronized void setStopAtEndOfMedia(final boolean stop) {
        data.stopAtEndOfMedia = stop;
    }

    /**
     * @return Whether the playback controls show beside the spreadsheet; true unless hidden.
     */
    public synchronized boolean isShowControls() {
        return data.showControls;
    }

    public synchronized void setShowControls(final boolean show) {
        data.showControls = show;
    }

    /**
     * @return Whether the playhead's line runs across the spreadsheet's columns as well as its own strip; true unless
     * turned off.
     */
    public synchronized boolean isPlayheadAcrossColumns() {
        return data.playheadAcrossColumns;
    }

    public synchronized void setPlayheadAcrossColumns(final boolean across) {
        data.playheadAcrossColumns = across;
    }

    /**
     * @return How many empty media columns a new spreadsheet starts with: 1 unless set otherwise, never below 0.
     */
    public synchronized int getNewSpreadsheetMediaColumns() {
        return Math.max(data.newSpreadsheetMediaColumns, 0);
    }

    public synchronized void setNewSpreadsheetMediaColumns(final int columns) {
        data.newSpreadsheetMediaColumns = columns;
    }

    /**
     * @return Whether cell values are set in a monospaced font: true when {@code spreadsheetFont} is {@code mono},
     * false for {@code sans} or anything else.
     */
    public synchronized boolean isMonospacedValues() {
        return MONO.equals(data.spreadsheetFont);
    }

    public synchronized void setMonospacedValues(final boolean monospaced) {
        data.spreadsheetFont = monospaced ? MONO : SANS;
    }

    /**
     * @return How far the times and values of cells the needle has passed fade toward the cell colour, from 0
     * (unchanged) to 100 (hidden): {@value #DEFAULT_PAST_CELL_DIM} unless set otherwise, clamped to that range.
     */
    public synchronized int getPastCellDim() {
        return Math.max(0, Math.min(100, data.pastCellDim));
    }

    public synchronized void setPastCellDim(final int percent) {
        data.pastCellDim = Math.max(0, Math.min(100, percent));
    }

    /**
     * @param window The window's key, see {@link org.datavyu.views.WindowPlacement}.
     * @return The window's bounds when it was last moved or resized.
     */
    public synchronized Optional<Rectangle> getWindowBounds(final String window) {
        Bounds bounds = data.windows.get(window);
        return bounds == null ? Optional.empty()
                : Optional.of(new Rectangle(bounds.x, bounds.y, bounds.width, bounds.height));
    }

    public synchronized void setWindowBounds(final String window, final Rectangle rectangle) {
        Bounds bounds = new Bounds();
        bounds.x = rectangle.x;
        bounds.y = rectangle.y;
        bounds.width = rectangle.width;
        bounds.height = rectangle.height;
        data.windows.put(window, bounds);
    }

    /** The file's contents. Field names are the JSON keys. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class Data {
        String lastChosenDirectory;
        List<String> recentProjects = new ArrayList<>();
        Map<String, Bounds> windows = new LinkedHashMap<>();
        boolean stopAtEndOfMedia = true;
        boolean showControls = true;
        boolean playheadAcrossColumns = true;
        int newSpreadsheetMediaColumns = 1;
        String spreadsheetFont = SANS;
        int pastCellDim = DEFAULT_PAST_CELL_DIM;

        /** Replaces null collections from a hand-edited file, so callers never see null. */
        Data normalized() {
            recentProjects = recentProjects == null ? new ArrayList<>() : new ArrayList<>(recentProjects);
            recentProjects.removeIf(path -> path == null);
            windows = windows == null ? new LinkedHashMap<>() : new LinkedHashMap<>(windows);
            windows.values().removeIf(bounds -> bounds == null);
            return this;
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class Bounds {
        int x;
        int y;
        int width;
        int height;
    }
}
