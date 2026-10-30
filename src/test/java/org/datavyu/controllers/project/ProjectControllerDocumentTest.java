package org.datavyu.controllers.project;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import org.datavyu.models.Identifier;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.MediaClip;
import org.datavyu.models.db.Variable;
import org.datavyu.models.project.Project;
import org.datavyu.plugins.StreamViewer;
import org.datavyu.projectfile.ProjectDocument;
import org.datavyu.projectfile.ProjectDocument.AnnotationColumn;
import org.datavyu.projectfile.ProjectDocument.Clip;
import org.datavyu.projectfile.ProjectDocument.MediaColumn;
import org.datavyu.projectfile.ProjectDocument.Source;
import org.datavyu.views.VideoController;
import org.datavyu.views.discrete.SpreadSheetPanel;
import org.testng.annotations.Test;

import java.awt.Dimension;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** A project file holds every spreadsheet column in order, with what each clip's opened file says. */
public class ProjectControllerDocumentTest {

    @Test
    public void columnsAreSavedInSpreadsheetOrderWithWhatTheFileSays() throws Exception {
        Path folder = Files.createTempDirectory("study");
        File cam = Files.createFile(folder.resolve("cam.mp4")).toFile();
        DataStore dataStore = DataStoreFactory.newDataStore();
        Variable notes = dataStore.createVariable("notes");
        notes.setOrderIndex(0);
        Variable media = dataStore.createMediaVariable("media_1");
        media.setOrderIndex(1);
        Cell clip = media.createClip(2_500, MediaClip.of(cam.getAbsolutePath()));

        // The player opens at once and reports the file; the clip's volume and the column's lock then change.
        Identifier id = Identifier.generateIdentifier();
        StreamViewer player = mock(StreamViewer.class);
        when(player.getIdentifier()).thenReturn(id);
        when(player.getDuration()).thenReturn(60_000L);
        when(player.getFramesPerSecond()).thenReturn(29.97f);
        when(player.getVideoSize()).thenReturn(new Dimension(1920, 1080));
        VideoController videoController = mock(VideoController.class);
        doAnswer(call -> {
            call.<Consumer<StreamViewer>>getArgument(2).accept(player);
            call.<Consumer<StreamViewer>>getArgument(3).accept(player);
            return null;
        }).when(videoController).openStream(any(), any(), any(), any(), any());

        MediaPlayers players = new MediaPlayers(dataStore, videoController,
            column -> mock(MediaPlayers.ColumnWindow.class), Runnable::run);
        players.openAll();
        players.setVolume(clip, 0.7);
        media.setLocked(true);

        SpreadSheetPanel panel = mock(SpreadSheetPanel.class);
        when(panel.getMediaPlayers()).thenReturn(players);
        ProjectController controller = new ProjectController(new Project(), dataStore);
        controller.setSpreadSheetPanel(panel);

        ProjectDocument document = controller.toDocument(folder);

        assertEquals(document.columns(), List.of(
            new AnnotationColumn("notes", List.of("code01"), false, List.of()),
            new MediaColumn("media_1", false, true, List.of(
                new Clip(2_500, 0.7, new Source("cam.mp4", 60_000, 29.97, 1920, 1080))))));
    }
}
