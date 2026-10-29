package org.datavyu.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SettingsTest {

    private Path directory;
    private Path file;

    @BeforeMethod
    public void setUp() throws IOException {
        directory = Files.createTempDirectory("settings");
        file = directory.resolve("nested").resolve("settings.json");
    }

    @AfterMethod
    public void tearDown() throws IOException {
        try (var paths = Files.walk(directory)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    public void aMissingFileGivesTheDefaults() {
        Settings settings = Settings.load(file);
        assertNull(settings.getLastChosenDirectory());
        assertTrue(settings.getRecentProjects().isEmpty());
        assertFalse(settings.getWindowBounds("main").isPresent());
    }

    @Test
    public void savedSettingsReadBackTheSame() {
        Settings settings = Settings.load(file);
        settings.setLastChosenDirectory("/Users/someone/Movies");
        settings.rememberProject(new File("/a.opf"));
        settings.rememberProject(new File("/b.opf"));
        settings.setWindowBounds("main", new Rectangle(10, 20, 800, 600));
        settings.save();

        Settings loaded = Settings.load(file);
        assertEquals(loaded.getLastChosenDirectory(), "/Users/someone/Movies");
        assertEquals(loaded.getRecentProjects(), Arrays.asList(new File("/b.opf"), new File("/a.opf")));
        assertEquals(loaded.getWindowBounds("main"), Optional.of(new Rectangle(10, 20, 800, 600)));
    }

    @Test
    public void theFileIsReadableJson() throws IOException {
        Settings settings = Settings.load(file);
        settings.setLastChosenDirectory("/tmp");
        settings.save();
        String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"lastChosenDirectory\" : \"/tmp\""), json);
    }

    @Test
    public void reopeningAProjectMovesItToTheFront() {
        Settings settings = Settings.load(file);
        settings.rememberProject(new File("/a.opf"));
        settings.rememberProject(new File("/b.opf"));
        settings.rememberProject(new File("/a.opf"));
        assertEquals(settings.getRecentProjects(), Arrays.asList(new File("/a.opf"), new File("/b.opf")));
    }

    @Test
    public void onlyTheFiveMostRecentProjectsAreKept() {
        Settings settings = Settings.load(file);
        for (int i = 1; i <= 7; i++) {
            settings.rememberProject(new File("/" + i + ".opf"));
        }
        assertEquals(settings.getRecentProjects(), Arrays.asList(new File("/7.opf"), new File("/6.opf"),
            new File("/5.opf"), new File("/4.opf"), new File("/3.opf")));
    }

    @Test
    public void anUnreadableFileGivesTheDefaults() throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, "{ not json".getBytes(StandardCharsets.UTF_8));
        Settings settings = Settings.load(file);
        assertNull(settings.getLastChosenDirectory());
        assertTrue(settings.getRecentProjects().isEmpty());
    }

    @Test
    public void unknownAndMissingKeysAreTolerated() throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, "{\"future\": 1, \"recentProjects\": null}".getBytes(StandardCharsets.UTF_8));
        Settings settings = Settings.load(file);
        assertTrue(settings.getRecentProjects().isEmpty());
        assertFalse(settings.getWindowBounds("main").isPresent());
    }

    @Test
    public void theTestsUseTheirOwnSettingsDirectory() {
        String configured = System.getProperty(Settings.DIRECTORY_PROPERTY);
        assertTrue(configured != null && !configured.isEmpty(), "build.gradle.kts must set " + Settings.DIRECTORY_PROPERTY);
        assertEquals(Settings.defaultFile(), new File(configured, "settings.json").toPath());
    }

    /** Playback stops at the end of the last media clip unless the setting is turned off. */
    @Test
    public void playbackStopsAtTheEndOfMediaByDefault() throws IOException {
        Settings settings = Settings.load(file);
        assertTrue(settings.isStopAtEndOfMedia());

        settings.setStopAtEndOfMedia(false);
        settings.save();
        assertFalse(Settings.load(file).isStopAtEndOfMedia());

        Files.write(file, "{ \"recentProjects\": [] }".getBytes(StandardCharsets.UTF_8));
        assertTrue(Settings.load(file).isStopAtEndOfMedia());
    }

    /** The controls drawer and the playhead's line across the columns show until turned off. */
    @Test
    public void theControlsAndThePlayheadLineShowByDefault() throws IOException {
        Settings settings = Settings.load(file);
        assertTrue(settings.isShowControls());
        assertTrue(settings.isPlayheadAcrossColumns());

        settings.setShowControls(false);
        settings.setPlayheadAcrossColumns(false);
        settings.save();
        assertFalse(Settings.load(file).isShowControls());
        assertFalse(Settings.load(file).isPlayheadAcrossColumns());
    }

    /** A new spreadsheet starts with one empty media column unless the setting says otherwise. */
    @Test
    public void aNewSpreadsheetStartsWithOneMediaColumnByDefault() throws IOException {
        Settings settings = Settings.load(file);
        assertEquals(settings.getNewSpreadsheetMediaColumns(), 1);

        settings.setNewSpreadsheetMediaColumns(0);
        settings.save();
        assertEquals(Settings.load(file).getNewSpreadsheetMediaColumns(), 0);

        Files.write(file, "{ \"newSpreadsheetMediaColumns\": -2 }".getBytes(StandardCharsets.UTF_8));
        assertEquals(Settings.load(file).getNewSpreadsheetMediaColumns(), 0);
    }
}
