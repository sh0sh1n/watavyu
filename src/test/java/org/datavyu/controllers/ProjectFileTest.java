package org.datavyu.controllers;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.datavyu.models.project.Project;
import org.datavyu.models.project.ViewerSetting;
import org.datavyu.plugins.javafx.JavaFxVideoPlugin;
import org.testng.annotations.Test;

/**
 * The project entry of {@code .opf} files: what this version reads from files that older versions wrote.
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

    /** S1: a project entry naming a Java class must not make that class. */
    @Test
    public void aProjectEntryCannotMakeArbitraryJavaObjects() {
        String yaml = WITH_BOOKMARKS.replace("name: study", "name: study\nextra: !!java.io.File [\"/tmp/x\"]");

        Project project = open(yaml);

        assertTrue(tracks(project).isEmpty(), "the entry is refused as a whole");
    }
}
