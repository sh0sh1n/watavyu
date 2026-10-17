package org.datavyu.controllers.project;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

import org.datavyu.controllers.component.MixerController;
import org.datavyu.models.Identifier;
import org.datavyu.models.component.TrackModel;
import org.datavyu.models.db.Argument;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.project.Project;
import org.datavyu.plugins.StreamViewer;
import org.datavyu.projectfile.ProjectDocument;
import org.datavyu.projectfile.ProjectDocument.Clip;
import org.datavyu.projectfile.ProjectDocument.Column;
import org.datavyu.projectfile.ProjectDocument.AnnotationColumn;
import org.datavyu.projectfile.ProjectDocument.Source;
import org.datavyu.projectfile.ProjectDocument.MediaColumn;
import org.datavyu.projectfile.ProjectJson;
import org.datavyu.projectfile.ProjectPaths;
import org.datavyu.views.VideoController;
import org.datavyu.views.discrete.SpreadSheetPanel;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.awt.Dimension;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;

/** What a project file holds for the open project: its open videos, its unavailable videos and its spreadsheet. */
public class ProjectControllerDocumentTest {

    private static final Path FOLDER = Paths.get("/Users/someone/study");

    private MixerController mixer;
    private LinkedHashSet<StreamViewer> viewers;

    @BeforeMethod
    public void setUp() {
        mixer = mock(MixerController.class);
        viewers = new LinkedHashSet<>();
    }

    private ProjectController controller(final Project project, final DataStore dataStore) {
        VideoController videoController = mock(VideoController.class);
        when(videoController.getStreamViewers()).thenReturn(viewers);
        when(videoController.getMixerController()).thenReturn(mixer);
        SpreadSheetPanel panel = mock(SpreadSheetPanel.class);
        when(panel.getVideoController()).thenReturn(videoController);
        ProjectController controller = new ProjectController(project, dataStore);
        controller.setSpreadSheetPanel(panel);
        return controller;
    }

    private StreamViewer video(final String path, final long offset, final boolean locked) {
        Identifier id = Identifier.generateIdentifier();
        StreamViewer viewer = mock(StreamViewer.class);
        when(viewer.getIdentifier()).thenReturn(id);
        when(viewer.getSourceFile()).thenReturn(new File(path));
        when(viewer.getOffset()).thenReturn(offset);
        when(viewer.getDuration()).thenReturn(60_000L);
        when(viewer.getVolumeLevel()).thenReturn(0.7f);
        when(viewer.isViewerVisible()).thenReturn(true);
        when(viewer.getVideoSize()).thenReturn(new Dimension(1920, 1080));
        when(viewer.getFramesPerSecond()).thenReturn(29.97f);
        TrackModel track = new TrackModel();
        track.setLocked(locked);
        when(mixer.getTrackModel(id)).thenReturn(track);
        viewers.add(viewer);
        return viewer;
    }

    @Test
    public void anOpenVideoIsSavedAsAMediaColumnWithOneClip() throws Exception {
        video("/Users/someone/study/media/cam 1.mp4", -2_500, true);

        ProjectDocument document = controller(new Project(), DataStoreFactory.newDataStore()).toDocument(FOLDER);

        assertEquals(document.columns(), List.of(new MediaColumn("media_1", false, true, List.of(
            new Clip(-2_500, 0.7, new Source("media/cam 1.mp4", 60_000, 29.97, 1920, 1080))))));
    }

    @Test
    public void mediaComesBeforeTheSpreadsheetsColumns() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        dataStore.createVariable("Notes", Argument.Type.TEXT);
        video("/Volumes/Share/cam.mp4", 0, false);

        ProjectDocument document = controller(new Project(), dataStore).toDocument(FOLDER);

        assertEquals(((MediaColumn) document.columns().get(0)).clips().get(0).source().file(), "/Volumes/Share/cam.mp4");
        assertEquals(document.columns().get(1), new AnnotationColumn("Notes", List.of("var"), false, List.of()));
    }

    @Test
    public void newMediaIsNumberedAfterTheNamesInUse() throws Exception {
        MediaColumn missing = new MediaColumn("media_1", true, false, List.of(
            new Clip(1_000, 0.5, new Source("media/missing.mp4", 5_000, 25, 640, 480))));
        Project project = new Project();
        project.addUnavailableMedia(ProjectPaths.absolutePaths(missing, FOLDER));
        DataStore dataStore = DataStoreFactory.newDataStore();
        dataStore.createVariable("test", Argument.Type.TEXT);
        video("/Users/someone/study/test.mp4", 0, false);
        video("/Users/someone/study/other.mp4", 0, false);

        ProjectDocument document = controller(project, dataStore).toDocument(FOLDER);

        assertEquals(document.columns().stream().map(Column::name).toList(),
            List.of("media_2", "media_3", "media_1", "test"));
        assertEquals(document.columns().get(2), missing);
    }

    @Test
    public void openedMediaKeepsItsColumnsName() throws Exception {
        MediaColumn column = new MediaColumn("media_2", false, false, List.of(
            new Clip(0, 1, new Source("cam.mp4", 60_000, 30, 1920, 1080))));
        Project project = new Project();
        project.addUnavailableMedia(column);
        video("/Users/someone/study/new.mp4", 0, false);
        StreamViewer viewer = video("/Users/someone/study/cam.mp4", 0, false);
        project.mediaOpened(column, viewer.getIdentifier());

        ProjectDocument document = controller(project, DataStoreFactory.newDataStore()).toDocument(FOLDER);

        assertEquals(document.columns().stream().map(Column::name).toList(), List.of("media_1", "media_2"));
    }

    @Test
    public void mediaNamedLikeAnotherColumnIsRenumbered() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        dataStore.createVariable("media_1", Argument.Type.TEXT);
        Project project = new Project();
        StreamViewer viewer = video("/Users/someone/study/cam.mp4", 0, false);
        project.setMediaName(viewer.getIdentifier(), "media_1");

        ProjectDocument document = controller(project, dataStore).toDocument(FOLDER);

        assertEquals(document.columns().get(0).name(), "media_2");
    }

    /** Review #2: media that is not open is held with absolute paths, so it is saved right from any folder. */
    @Test
    public void mediaThatIsNotOpenIsSavedRelativeToTheNewFolder() throws Exception {
        MediaColumn missing = new MediaColumn("media_1", false, false, List.of(
            new Clip(0, 1, new Source("media/missing.mp4", 5_000, 25, 640, 480))));
        Project project = new Project();
        project.addUnavailableMedia(ProjectPaths.absolutePaths(missing, FOLDER));
        ProjectController controller = controller(project, DataStoreFactory.newDataStore());

        MediaColumn here = (MediaColumn) controller.toDocument(FOLDER).columns().get(0);
        MediaColumn elsewhere = (MediaColumn) controller.toDocument(Paths.get("/Users/someone/copy")).columns().get(0);

        assertEquals(here, missing);
        assertEquals(elsewhere.clips().get(0).source().file(), "/Users/someone/study/media/missing.mp4");
    }

    /** Review #1: a spreadsheet column taking the name of media that is not open does not stop saving. */
    @Test
    public void mediaThatIsNotOpenGivesWayToASpreadsheetColumnsName() throws Exception {
        MediaColumn missing = new MediaColumn("media_1", false, false, List.of());
        Project project = new Project();
        project.addUnavailableMedia(missing);
        DataStore dataStore = DataStoreFactory.newDataStore();
        dataStore.createVariable("media_1", Argument.Type.TEXT);

        ProjectDocument document = controller(project, dataStore).toDocument(FOLDER);

        assertEquals(document.columns().stream().map(Column::name).toList(), List.of("media_2", "media_1"));
        ProjectJson.toJson(document);
    }

    /** Review #6: media columns keep the project file's order, whichever video opens first. */
    @Test
    public void mediaColumnsKeepTheFilesOrder() throws Exception {
        MediaColumn first = new MediaColumn("media_1", false, false, List.of());
        MediaColumn second = new MediaColumn("media_2", false, false, List.of());
        MediaColumn third = new MediaColumn("media_3", false, false, List.of());
        Project project = new Project();
        project.setMediaColumns(List.of(first, second, third));
        project.addUnavailableMedia(first);
        StreamViewer openedFirst = video("/Users/someone/study/c.mp4", 0, false);
        project.mediaOpened(third, openedFirst.getIdentifier());
        StreamViewer openedSecond = video("/Users/someone/study/b.mp4", 0, false);
        project.mediaOpened(second, openedSecond.getIdentifier());
        video("/Users/someone/study/new.mp4", 0, false);

        ProjectDocument document = controller(project, DataStoreFactory.newDataStore()).toDocument(FOLDER);

        assertEquals(document.columns().stream().map(Column::name).toList(),
            List.of("media_1", "media_2", "media_3", "media_4"));
    }

    @Test
    public void aVideoWithoutAKnownSizeCannotBeSaved() {
        StreamViewer viewer = video("/Users/someone/study/cam.mp4", 0, false);
        when(viewer.getVideoSize()).thenReturn(null);

        expectThrows(UserWarningException.class,
            () -> controller(new Project(), DataStoreFactory.newDataStore()).toDocument(FOLDER));
    }
}
