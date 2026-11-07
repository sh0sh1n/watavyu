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
import java.util.Objects;
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

    /** Guards {@link #data} and saving it, as more than one thread may use the settings. */
    private final Object lock = new Object();

    private final Path file;

    private final Data data;

    private Settings(final Path file, final Data data) {
        this.file = file;
        this.data = data;
    }

    /**
     * Returns the settings of this run, loaded on first use.
     */
    public static Settings get() {
        return Loaded.INSTANCE;
    }

    /** Holds the settings of this run, which the JVM loads once, on the first {@link #get()}. */
    private static final class Loaded {
        static final Settings INSTANCE = load(defaultFile());
    }

    /**
     * Returns the settings file this run uses.
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
                // A file holding only null reads as null.
                Data data = MAPPER.readValue(file.toFile(), Data.class);
                if (data != null) {
                    return new Settings(file, data.normalized());
                }
                logger.warn("No settings in " + file + "; using defaults");
            } catch (IOException e) {
                logger.warn("Cannot read settings from " + file + "; using defaults", e);
            }
        }
        return new Settings(file, new Data());
    }

    /**
     * Writes the settings to their file, creating its directory if needed. A failure is logged, not thrown: losing
     * these settings must never stop the application from closing.
     */
    public void save() {
        synchronized (lock) {
            Path temporary = null;
            try {
                Path folder = Objects.requireNonNull(file.toAbsolutePath().getParent(), "the settings file is in a folder");
                Files.createDirectories(folder);
                temporary = Files.createTempFile(folder, FILE_NAME, ".tmp");
                MAPPER.writeValue(temporary.toFile(), data);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                logger.error("Cannot save settings to " + file, e);
                deleteQuietly(temporary);
            }
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
     * Returns the folder last chosen in a file dialog, or null if none was.
     */
    public String getLastChosenDirectory() {
        synchronized (lock) {
            return data.lastChosenDirectory;
        }
    }

    public void setLastChosenDirectory(final String directory) {
        synchronized (lock) {
            data.lastChosenDirectory = directory;
        }
    }

    /**
     * Where a file dialog opens: the folder last chosen if it still exists, otherwise the user's home. The fallback
     * is not remembered, so a folder on a disconnected drive is used again once it is back.
     *
     * @return An existing directory.
     */
    public File resolveStartDirectory() {
        synchronized (lock) {
            return FileSystemUtils.existingDirectoryOr(data.lastChosenDirectory, new File(System.getProperty("user.home")));
        }
    }

    /**
     * Returns recently opened or saved projects, most recent first.
     */
    public List<File> getRecentProjects() {
        synchronized (lock) {
            List<File> files = new ArrayList<>();
            for (String path : data.recentProjects) {
                files.add(new File(path));
            }
            return files;
        }
    }

    /**
     * Moves a project to the front of the recent projects, dropping the oldest beyond {@link #RECENT_PROJECT_LIMIT}.
     *
     * @param project The project or data file opened or saved.
     */
    public void rememberProject(final File project) {
        synchronized (lock) {
            String path = project.getAbsolutePath();
            data.recentProjects.remove(path);
            data.recentProjects.add(0, path);
            while (data.recentProjects.size() > RECENT_PROJECT_LIMIT) {
                data.recentProjects.remove(data.recentProjects.size() - 1);
            }
        }
    }

    /**
     * Returns whether playback stops at the end of the last media clip; true unless turned off.
     */
    public boolean isStopAtEndOfMedia() {
        synchronized (lock) {
            return data.stopAtEndOfMedia;
        }
    }

    public void setStopAtEndOfMedia(final boolean stop) {
        synchronized (lock) {
            data.stopAtEndOfMedia = stop;
        }
    }

    /**
     * Returns whether the playback controls show beside the spreadsheet; true unless hidden.
     */
    public boolean isShowControls() {
        synchronized (lock) {
            return data.showControls;
        }
    }

    public void setShowControls(final boolean show) {
        synchronized (lock) {
            data.showControls = show;
        }
    }

    /**
     * Returns whether the playhead's line runs across the spreadsheet's columns as well as its own strip; true unless
     * turned off.
     */
    public boolean isPlayheadAcrossColumns() {
        synchronized (lock) {
            return data.playheadAcrossColumns;
        }
    }

    public void setPlayheadAcrossColumns(final boolean across) {
        synchronized (lock) {
            data.playheadAcrossColumns = across;
        }
    }

    /**
     * Returns how many empty media columns a new spreadsheet starts with: 1 unless set otherwise, never below 0.
     */
    public int getNewSpreadsheetMediaColumns() {
        synchronized (lock) {
            return Math.max(data.newSpreadsheetMediaColumns, 0);
        }
    }

    public void setNewSpreadsheetMediaColumns(final int columns) {
        synchronized (lock) {
            data.newSpreadsheetMediaColumns = columns;
        }
    }

    /**
     * Returns whether cell values are set in a monospaced font: true when {@code spreadsheetFont} is {@code mono},
     * false for {@code sans} or anything else.
     */
    public boolean isMonospacedValues() {
        synchronized (lock) {
            return MONO.equals(data.spreadsheetFont);
        }
    }

    public void setMonospacedValues(final boolean monospaced) {
        synchronized (lock) {
            data.spreadsheetFont = monospaced ? MONO : SANS;
        }
    }

    /**
     * Returns how far the times and values of cells the needle has passed fade toward the cell colour, from 0
     * (unchanged) to 100 (hidden): {@value #DEFAULT_PAST_CELL_DIM} unless set otherwise, clamped to that range.
     */
    public int getPastCellDim() {
        synchronized (lock) {
            return Math.max(0, Math.min(100, data.pastCellDim));
        }
    }

    public void setPastCellDim(final int percent) {
        synchronized (lock) {
            data.pastCellDim = Math.max(0, Math.min(100, percent));
        }
    }

    /**
     * Returns the window's bounds when it was last moved or resized.
     * @param window The window's key, see {@link org.datavyu.views.WindowPlacement}.
     */
    public Optional<Rectangle> getWindowBounds(final String window) {
        synchronized (lock) {
            Bounds bounds = data.windows.get(window);
            return bounds == null ? Optional.empty()
                    : Optional.of(new Rectangle(bounds.x, bounds.y, bounds.width, bounds.height));
        }
    }

    public void setWindowBounds(final String window, final Rectangle rectangle) {
        synchronized (lock) {
            Bounds bounds = new Bounds();
            bounds.x = rectangle.x;
            bounds.y = rectangle.y;
            bounds.width = rectangle.width;
            bounds.height = rectangle.height;
            data.windows.put(window, bounds);
        }
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
