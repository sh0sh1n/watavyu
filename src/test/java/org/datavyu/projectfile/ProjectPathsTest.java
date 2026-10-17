package org.datavyu.projectfile;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ProjectPathsTest {

    private static final Path FOLDER = Paths.get("/Users/someone/study");

    @Test
    public void aVideoInsideTheProjectFolderIsRelative() {
        assertEquals(ProjectPaths.stored(FOLDER, Paths.get("/Users/someone/study/media/cam 1.mp4")),
            "media/cam 1.mp4");
        assertEquals(ProjectPaths.stored(FOLDER, Paths.get("/Users/someone/study/cam.mp4")), "cam.mp4");
    }

    @Test
    public void aVideoElsewhereIsAbsolute() {
        assertEquals(ProjectPaths.stored(FOLDER, Paths.get("/Volumes/Share/cam.mp4")), "/Volumes/Share/cam.mp4");
        assertEquals(ProjectPaths.stored(FOLDER, Paths.get("/Users/someone/study-2/cam.mp4")),
            "/Users/someone/study-2/cam.mp4");
        assertEquals(ProjectPaths.stored(FOLDER, Paths.get("/Users/someone/study/../other/cam.mp4")),
            "/Users/someone/other/cam.mp4");
    }

    @Test
    public void storedPathsResolveAgainstTheProjectFolder() {
        assertEquals(ProjectPaths.resolve(FOLDER, "media/cam.mp4"), Paths.get("/Users/someone/study/media/cam.mp4"));
        assertEquals(ProjectPaths.resolve(FOLDER, "../shared/cam.mp4"), Paths.get("/Users/someone/shared/cam.mp4"));
        assertEquals(ProjectPaths.resolve(FOLDER, "/Volumes/Share/cam.mp4"), Paths.get("/Volumes/Share/cam.mp4"));
    }

    @Test
    public void aMovedProjectFolderStillFindsItsVideos() {
        String stored = ProjectPaths.stored(FOLDER, Paths.get("/Users/someone/study/media/cam.mp4"));
        assertEquals(ProjectPaths.resolve(Paths.get("/Volumes/Backup/study"), stored),
            Paths.get("/Volumes/Backup/study/media/cam.mp4"));
    }

    @Test
    public void theProjectNameIsTheFileNameWithoutTheExtension() {
        assertEquals(ProjectPaths.projectName(Paths.get("/a/session-04.watavyu.json")), "session-04");
        assertEquals(ProjectPaths.projectName(Paths.get("/a/notes.json")), "notes.json");
    }

    /** Review #9: macOS file names match in any case, so the extension does too. */
    @Test
    public void theExtensionMatchesInAnyCase() {
        assertEquals(ProjectPaths.projectName(Paths.get("/a/Study.WATAVYU.JSON")), "Study");
        assertFalse(ProjectPaths.isProjectFile("study.json"));
        assertFalse(ProjectPaths.isProjectFile(".json"));
    }
}
