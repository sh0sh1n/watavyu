package org.datavyu.controllers.project;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import org.datavyu.controllers.component.MixerController;
import org.datavyu.controllers.component.TracksEditorController;
import org.datavyu.models.Identifier;
import org.datavyu.models.component.TrackModel;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.MediaClip;
import org.datavyu.models.db.Variable;
import org.datavyu.plugins.StreamViewer;
import org.datavyu.views.VideoController;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The spreadsheet's media columns and their players stay in step: clips added, moved or removed in the spreadsheet
 * open, move or close players, and tracks dragged, locked or removed move, lock or remove clips. Players here open at
 * once; in the app they open in the background.
 */
public class MediaPlayersTest {

    private Path folder;
    private DataStore dataStore;
    private VideoController videoController;
    private MixerController mixer;
    private TracksEditorController tracks;
    private List<StreamViewer> opened;
    private boolean failToOpen;
    private Map<Variable, FakeWindow> windows;
    private MediaPlayers players;

    /** A column's window, recording what it shows. */
    private static final class FakeWindow implements MediaPlayers.ColumnWindow {
        final List<Object> clips = new ArrayList<>();
        Object showing;
        boolean visible;
        boolean closed;

        @Override
        public void addClip(final Object clip, final Component video) {
            clips.add(clip);
        }

        @Override
        public void removeClip(final Object clip) {
            clips.remove(clip);
        }

        @Override
        public void showClip(final Object clip) {
            showing = clip;
        }

        @Override
        public void setShowing(final boolean showing) {
            visible = showing;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        folder = Files.createTempDirectory("study");
        dataStore = DataStoreFactory.newDataStore();
        mixer = mock(MixerController.class);
        tracks = mock(TracksEditorController.class);
        when(mixer.getTracksEditorController()).thenReturn(tracks);
        videoController = mock(VideoController.class);
        when(videoController.getMixerController()).thenReturn(mixer);
        opened = new ArrayList<>();
        failToOpen = false;
        doAnswer(call -> {
            if (failToOpen) {
                call.<Runnable>getArgument(4).run();
                return null;
            }
            StreamViewer player = player(call.getArgument(0), call.getArgument(1));
            call.<Consumer<StreamViewer>>getArgument(2).accept(player);
            call.<Consumer<StreamViewer>>getArgument(3).accept(player);
            opened.add(player);
            return null;
        }).when(videoController).openStream(any(), any(), any(), any(), any());
        windows = new HashMap<>();
        players = new MediaPlayers(dataStore, videoController, column -> {
            FakeWindow window = new FakeWindow();
            windows.put(column, window);
            return window;
        }, Runnable::run);
    }

    /** A player that reports a 10 s, 30 fps, 1280x720 file, and remembers the offset it is given. */
    private StreamViewer player(final Identifier id, final File file) {
        StreamViewer player = mock(StreamViewer.class);
        long[] offset = {0};
        when(player.getIdentifier()).thenReturn(id);
        when(player.getSourceFile()).thenReturn(file);
        when(player.getDuration()).thenReturn(10_000L);
        when(player.getFramesPerSecond()).thenReturn(30f);
        when(player.getVideoSize()).thenReturn(new Dimension(1280, 720));
        doAnswer(call -> offset[0] = call.<Long>getArgument(0)).when(player).setOffset(any(Long.class));
        boolean[] visible = {true};
        doAnswer(call -> {
            visible[0] = call.<Boolean>getArgument(0);
            // The player shows its column's window through the listener it was given.
            return null;
        }).when(player).setViewerVisible(any(Boolean.class));
        when(player.getOffset()).thenAnswer(call -> offset[0]);
        TrackModel track = new TrackModel();
        when(mixer.getTrackModel(id)).thenReturn(track);
        return player;
    }

    private String file(final String name) throws Exception {
        return Files.createFile(folder.resolve(name)).toString();
    }

    private Cell clip(final String column, final long onset, final String file) throws Exception {
        Variable media = dataStore.createMediaVariable(column);
        return media.createClip(onset, MediaClip.of(file));
    }

    @Test
    public void addedMediaGetsANewColumnPlayingFromTimeZero() throws Exception {
        dataStore.createVariable("media_1");

        Variable column = players.addMedia(new File(file("cam.mp4")));

        assertEquals(column.getName(), "media_2");
        assertTrue(column.isMedia());
        Cell clip = column.getCells().get(0);
        assertEquals(clip.getOnset(), 0L);
        assertSame(players.getPlayer(clip), opened.get(0));
    }

    @Test
    public void mediaThatDoesNotOpenAddsNoColumn() throws Exception {
        failToOpen = true;

        players.addMedia(new File(file("broken.mp4")));

        assertEquals(dataStore.getAllVariables(), List.of());
    }

    @Test
    public void missingMediaAddsNoColumn() throws Exception {
        players.addMedia(folder.resolve("missing.mp4").toFile());

        assertEquals(dataStore.getAllVariables(), List.of());
    }

    /** The file says how long it is, so the clip's offset follows. */
    @Test
    public void anOpenedClipTakesWhatItsFileSays() throws Exception {
        Cell clip = clip("media_1", 2_000, file("cam.mp4"));

        assertEquals(players.openAll(), List.of());

        assertEquals(clip.getClip().duration(), 10_000L);
        assertEquals(clip.getClip().frameRate(), 30.0);
        assertEquals(clip.getClip().width(), 1280);
        assertEquals(clip.getOffset(), 12_000L);
        verify(players.getPlayer(clip)).setOffset(2_000L);
    }

    @Test
    public void aMissingClipKeepsItsPlaceWithoutAPlayer() throws Exception {
        String missing = folder.resolve("missing.mp4").toString();
        Cell clip = clip("media_1", 2_000, missing);

        assertEquals(players.openAll(), List.of(missing));

        assertNull(players.getPlayer(clip));
        assertEquals(clip.getOnset(), 2_000L);
        assertEquals(dataStore.getAllVariables().size(), 1);
    }

    @Test
    public void editingAClipsOnsetMovesItsPlayer() throws Exception {
        Cell clip = clip("media_1", 0, file("cam.mp4"));
        players.openAll();
        StreamViewer player = players.getPlayer(clip);

        clip.setOnset(-1_500);

        verify(player).setOffset(-1_500L);
        verify(tracks).setTrackOffset(player.getIdentifier(), -1_500L);
    }

    @Test
    public void draggingATrackMovesItsClip() throws Exception {
        Cell clip = clip("media_1", 0, file("cam.mp4"));
        players.openAll();

        players.playerMoved(players.getPlayer(clip).getIdentifier(), 4_000);

        assertEquals(clip.getOnset(), 4_000L);
        assertEquals(clip.getOffset(), 14_000L);
    }

    @Test
    public void removingATrackRemovesItsColumn() throws Exception {
        Cell clip = clip("media_1", 0, file("cam.mp4"));
        players.openAll();

        players.playerRemoved(players.getPlayer(clip).getIdentifier());

        assertEquals(dataStore.getAllVariables(), List.of());
    }

    /** Deleting a clip or its column in the spreadsheet closes its player; undo puts both back. */
    @Test
    public void deletingAClipClosesItsPlayerAndUndoReopensIt() throws Exception {
        Cell clip = clip("media_1", 0, file("cam.mp4"));
        players.openAll();
        StreamViewer player = players.getPlayer(clip);
        Variable column = clip.getVariable();

        column.removeCell(clip);
        verify(videoController).shutdown(player.getIdentifier(), false);
        assertNull(players.getPlayer(clip));

        column.addCell(clip);
        assertSame(players.getPlayer(clip), opened.get(1));
    }

    @Test
    public void deletingAColumnClosesItsPlayersAndUndoReopensThem() throws Exception {
        Cell clip = clip("media_1", 0, file("cam.mp4"));
        players.openAll();
        StreamViewer player = players.getPlayer(clip);
        Variable column = clip.getVariable();

        dataStore.removeVariable(column);
        verify(videoController).shutdown(player.getIdentifier(), false);

        dataStore.addVariable(column);
        assertSame(players.getPlayer(clip), opened.get(1));
    }

    @Test
    public void aClipsVolumeAndLockComeFromItsPlayer() throws Exception {
        Cell clip = clip("media_1", 0, file("cam.mp4"));
        players.openAll();
        StreamViewer player = players.getPlayer(clip);
        when(player.getVolumeLevel()).thenReturn(0.7f);
        mixer.getTrackModel(player.getIdentifier()).setLocked(true);

        players.updateClips();

        assertEquals(clip.getClip().volume(), 0.7);
        assertTrue(clip.getVariable().isLocked());
    }

    @Test
    public void aLockedColumnsTrackOpensLocked() throws Exception {
        Variable media = dataStore.createMediaVariable("media_1");
        media.setLocked(true);
        Cell clip = media.createClip(0, MediaClip.of(file("cam.mp4")));

        verify(mixer).setTrackLocked(players.getPlayer(clip).getIdentifier(), true);
    }

    @Test
    public void hidingAMediaColumnHidesItsWindow() throws Exception {
        Variable media = dataStore.createMediaVariable("media_1");
        media.setHidden(true);
        Cell clip = media.createClip(0, MediaClip.of(file("cam.mp4")));
        StreamViewer player = players.getPlayer(clip);
        verify(player).setViewerVisible(false);

        clip.getVariable().setHidden(false);

        verify(player).setViewerVisible(true);
    }

    @Test
    public void annotationColumnsHaveNoPlayers() throws Exception {
        dataStore.createVariable("notes").createCell();

        players.openAll();

        verify(videoController, never()).openStream(any(), any(), any(), any(), any());
        verify(videoController, never()).openStream(any(), eq(null), any(), any());
    }

    private Cell addClip(final Variable column, final long onset, final String name) throws Exception {
        return column.createClip(onset, MediaClip.of(file(name)));
    }

    /** All of a column's clips show in one window, which shows the clip in play and black in the gaps. */
    @Test
    public void aColumnsClipsShareOneWindowShowingTheClipInPlay() throws Exception {
        Variable media = dataStore.createMediaVariable("media_1");
        Cell first = addClip(media, 0, "a.mp4");
        Cell second = addClip(media, 20_000, "b.mp4");
        FakeWindow window = windows.get(media);

        assertEquals(window.clips, List.of(first, second));
        assertEquals(windows.size(), 1);

        players.clockForceSync(5_000);
        assertSame(window.showing, first);
        verify(players.getPlayer(first), org.mockito.Mockito.atLeastOnce()).setInPlay(true);
        verify(players.getPlayer(second), never()).setInPlay(true);

        players.clockForceSync(15_000);
        assertNull(window.showing);

        players.clockForceSync(25_000);
        assertSame(window.showing, second);
    }

    @Test
    public void whereClipsOverlapTheLaterOneWins() throws Exception {
        Variable media = dataStore.createMediaVariable("media_1");
        Cell first = addClip(media, 0, "a.mp4");
        Cell second = addClip(media, 5_000, "b.mp4");

        players.clockPeriodicSync(7_000);

        assertSame(windows.get(media).showing, second);
        verify(players.getPlayer(first), org.mockito.Mockito.atLeastOnce()).setInPlay(false);
    }

    /** Moving a clip changes what plays at once, even while paused. */
    @Test
    public void movingAClipChangesWhatIsInPlay() throws Exception {
        Variable media = dataStore.createMediaVariable("media_1");
        Cell clip = addClip(media, 0, "a.mp4");
        FakeWindow window = windows.get(media);
        assertSame(window.showing, clip);

        clip.setOnset(1_000);

        assertNull(window.showing);
    }

    @Test
    public void theWindowClosesWithItsLastClip() throws Exception {
        Variable media = dataStore.createMediaVariable("media_1");
        Cell first = addClip(media, 0, "a.mp4");
        Cell second = addClip(media, 20_000, "b.mp4");
        FakeWindow window = windows.get(media);

        media.removeCell(first);
        assertEquals(window.clips, List.of(second));
        assertTrue(!window.closed);

        media.removeCell(second);
        assertTrue(window.closed);
    }

    /** A missing clip still plays where it is, as black: the later clip wins even when it cannot be shown. */
    @Test
    public void aMissingLaterClipShowsBlack() throws Exception {
        Variable media = dataStore.createMediaVariable("media_1");
        addClip(media, 0, "a.mp4");
        Cell missing = media.createClip(5_000,
            new MediaClip(folder.resolve("gone.mp4").toString(), 10_000, 30, 640, 480, 1));

        players.clockForceSync(7_000);

        // The window has no video for the missing clip, so it shows black.
        assertSame(windows.get(media).showing, missing);
        assertTrue(!windows.get(media).clips.contains(missing));
    }

    @Test
    public void inPlayIsTheLatestStartingClipHoldingTheTime() throws Exception {
        Variable media = dataStore.createMediaVariable("media_1");
        Cell a = media.createClip(0, new MediaClip("/a.mp4", 10_000, 30, 640, 480, 1));
        Cell b = media.createClip(5_000, new MediaClip("/b.mp4", 10_000, 30, 640, 480, 1));
        Cell unknown = media.createClip(6_000, MediaClip.of("/c.mp4"));
        List<MediaPlayers.Span> spans = List.of(
            new MediaPlayers.Span(a, 0, 10_000, null),
            new MediaPlayers.Span(b, 5_000, 15_000, null),
            new MediaPlayers.Span(unknown, 6_000, 6_000, null));

        assertSame(MediaPlayers.inPlay(spans, 0), a);
        assertSame(MediaPlayers.inPlay(spans, 4_999), a);
        assertSame(MediaPlayers.inPlay(spans, 5_000), b);
        assertSame(MediaPlayers.inPlay(spans, 6_000), b);
        assertSame(MediaPlayers.inPlay(spans, 14_999), b);
        assertNull(MediaPlayers.inPlay(spans, 15_000));
        assertNull(MediaPlayers.inPlay(spans, -1));
    }

    @Test
    public void hidingAColumnHidesItsWindow() throws Exception {
        Variable media = dataStore.createMediaVariable("media_1");
        Cell clip = addClip(media, 0, "a.mp4");

        media.setHidden(true);

        verify(players.getPlayer(clip)).setViewerVisible(false);
    }

    /** Added media joins the one selected media column at the current time; otherwise each file gets a column. */
    @Test
    public void addedMediaJoinsTheSelectedMediaColumn() throws Exception {
        Variable media = dataStore.createMediaVariable("media_1");
        addClip(media, 0, "a.mp4");
        dataStore.deselectAll();
        media.setSelected(true);

        players.addMediaFiles(List.of(new File(file("b.mp4"))), 12_000);

        assertEquals(dataStore.getAllVariables(), List.of(media));
        assertEquals(media.getCellsTemporally().get(1).getOnset(), 12_000L);
        assertEquals(windows.get(media).clips.size(), 2);
    }

    @Test
    public void addedMediaWithoutASelectedMediaColumnGetsNewColumns() throws Exception {
        dataStore.createVariable("notes").setSelected(true);

        players.addMediaFiles(List.of(new File(file("a.mp4")), new File(file("b.mp4"))), 12_000);

        assertEquals(dataStore.getAllVariables().stream().map(Variable::getName).toList(),
            List.of("notes", "media_1", "media_2"));
        assertEquals(dataStore.getVariable("media_2").getCells().get(0).getOnset(), 0L);
    }

    @Test
    public void aClipThatDoesNotOpenLeavesItsColumn() throws Exception {
        Variable media = dataStore.createMediaVariable("media_1");
        Cell kept = addClip(media, 0, "a.mp4");
        failToOpen = true;

        players.addClip(media, new File(file("broken.mp4")), 5_000);

        assertEquals(media.getCells(), List.of(kept));
        assertEquals(dataStore.getAllVariables(), List.of(media));
    }
}
