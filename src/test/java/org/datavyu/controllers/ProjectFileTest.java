package org.datavyu.controllers;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.datavyu.models.project.Project;
import org.datavyu.models.project.TrackSettings;
import org.datavyu.models.project.ViewerSetting;
import org.datavyu.plugins.javafx.JavaFxVideoPlugin;
import org.testng.annotations.Test;

/**
 * The project entry of {@code .opf} files: what this version reads from files that older versions wrote, and what
 * it writes.
 */
public class ProjectFileTest {

    /** A project entry as Datavyu wrote it for a locked video track with two bookmarks. */
    private static final String WITH_BOOKMARKS = String.join("\n",
        "!project",
        "dbFile: study.csv",
        "name: study",
        "origpath: /Users/someone/study",
        "version: 5",
        "viewerSettings:",
        "- !vs",
        "  classifier: datavyu.video",
        "  feed: /Users/someone/study/clip.mp4",
        "  plugin: " + JavaFxVideoPlugin.PLUGIN_UUID,
        "  settingsId: '1'",
        "  trackSettings: !ts",
        "    bookmark: '1500'",
        "    bookmarks: [1500, 4200]",
        "    locked: true",
        "    version: 2",
        "  version: 3",
        "");

    private static Project open(final String yaml) {
        return new OpenProjectFileController().open(
            new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String save(final Project project) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new SaveProjectFileController().save(out, project);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static List<ViewerSetting> tracks(final Project project) {
        List<ViewerSetting> settings = new ArrayList<>();
        project.getViewerSettings().forEach(settings::add);
        return settings;
    }

    @Test
    public void aProjectWithBookmarksStillOpens() {
        List<ViewerSetting> tracks = tracks(open(WITH_BOOKMARKS));

        assertEquals(tracks.size(), 1);
        ViewerSetting track = tracks.get(0);
        assertEquals(track.getFilePath(), "/Users/someone/study/clip.mp4");
        assertEquals(track.getPluginName(), JavaFxVideoPlugin.VIEWER_CLASS_NAME);
        assertEquals(track.getSettingsId(), "1");
        assertTrue(track.getTrackSettings().isLocked());
    }

    @Test
    public void savingWritesNoBookmarks() {
        String saved = save(open(WITH_BOOKMARKS));

        assertFalse(saved.contains("bookmark"), saved);
        assertTrue(saved.contains("locked: true"), saved);
    }

    @Test
    public void aSavedTrackReopensWithTheOpenJfxPlayerAndItsLock() {
        ViewerSetting track = new ViewerSetting();
        track.setFilePath("/Users/someone/study/clip.mp4");
        track.setPluginNameAndUUID(JavaFxVideoPlugin.PLUGIN_UUID);
        track.setPluginClassifier(JavaFxVideoPlugin.NAMESPACE);
        track.setSettingsId("1");
        TrackSettings trackSettings = new TrackSettings();
        trackSettings.setLocked(true);
        track.setTrackSettings(trackSettings);
        Project project = new Project();
        project.setProjectName("study");
        project.setViewerSettings(List.of(track));

        ViewerSetting reopened = tracks(open(save(project))).get(0);

        assertEquals(reopened.getFilePath(), "/Users/someone/study/clip.mp4");
        assertEquals(reopened.getPluginUUID(), JavaFxVideoPlugin.PLUGIN_UUID);
        assertEquals(reopened.playerMatch(), ViewerSetting.PlayerMatch.SAME_PLAYER);
        assertTrue(reopened.getTrackSettings().isLocked());
    }
}
