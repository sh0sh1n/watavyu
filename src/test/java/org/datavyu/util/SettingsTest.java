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
        settings.rememberProject(new File("/a.watavyu.json"));
        settings.rememberProject(new File("/b.watavyu.json"));
        settings.setWindowBounds("main", new Rectangle(10, 20, 800, 600));
        settings.save();

        Settings loaded = Settings.load(file);
        assertEquals(loaded.getLastChosenDirectory(), "/Users/someone/Movies");
        assertEquals(loaded.getRecentProjects(), Arrays.asList(new File("/b.watavyu.json"), new File("/a.watavyu.json")));
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
        settings.rememberProject(new File("/a.watavyu.json"));
        settings.rememberProject(new File("/b.watavyu.json"));
        settings.rememberProject(new File("/a.watavyu.json"));
        assertEquals(settings.getRecentProjects(), Arrays.asList(new File("/a.watavyu.json"), new File("/b.watavyu.json")));
    }

    @Test
    public void onlyTheFiveMostRecentProjectsAreKept() {
        Settings settings = Settings.load(file);
        for (int i = 1; i <= 7; i++) {
            settings.rememberProject(new File("/" + i + ".watavyu.json"));
        }
        assertEquals(settings.getRecentProjects(), Arrays.asList(new File("/7.watavyu.json"), new File("/6.watavyu.json"),
            new File("/5.watavyu.json"), new File("/4.watavyu.json"), new File("/3.watavyu.json")));
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

    /** Cell values are in sans until the values font is set to mono, which is saved as the word "mono". */
    @Test
    public void valuesAreSansUntilSetToMono() throws IOException {
        Settings settings = Settings.load(file);
        assertFalse(settings.isMonospacedValues());

        settings.setMonospacedValues(true);
        settings.save();
        assertTrue(Settings.load(file).isMonospacedValues());
        String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"spreadsheetFont\" : \"mono\""), json);

        Files.write(file, "{ \"spreadsheetFont\": \"serif\" }".getBytes(StandardCharsets.UTF_8));
        assertFalse(Settings.load(file).isMonospacedValues());
    }

    /** Past cells dim by a trial default until the owner picks one, and the setting stays between 0 and 100. */
    @Test
    public void pastCellDimIsAPercentage() throws IOException {
        Settings settings = Settings.load(file);
        assertEquals(settings.getPastCellDim(), Settings.DEFAULT_PAST_CELL_DIM);

        settings.setPastCellDim(80);
        settings.save();
        assertEquals(Settings.load(file).getPastCellDim(), 80);

        settings.setPastCellDim(140);
        assertEquals(settings.getPastCellDim(), 100);
        settings.setPastCellDim(-5);
        assertEquals(settings.getPastCellDim(), 0);

        Files.write(file, "{ \"pastCellDim\": 250 }".getBytes(StandardCharsets.UTF_8));
        assertEquals(Settings.load(file).getPastCellDim(), 100);
    }
}
