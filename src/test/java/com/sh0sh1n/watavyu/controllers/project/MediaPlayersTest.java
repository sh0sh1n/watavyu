package com.sh0sh1n.watavyu.controllers.project;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.models.Identifier;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.Cell.ClipFile;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.MediaClip;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.Project;
import com.sh0sh1n.watavyu.playback.StreamViewer;
import com.sh0sh1n.watavyu.util.ClockTimer;
import com.sh0sh1n.watavyu.views.VideoController;
import org.mockito.invocation.InvocationOnMock;
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
import java.util.function.DoubleConsumer;

/**
 * The spreadsheet's media columns and their players stay in step: clips added, moved or removed in the spreadsheet
 * open, move or close players, and each column's window shows the clip in play and sets its volume. Players here open
 * at once; in the app they open in the background.
 */
public class MediaPlayersTest {

    private Path folder;
    private DataStore dataStore;
    private VideoController videoController;
    private List<StreamViewer> opened;
    private boolean failToOpen;
    /** When set, players do not open at once; their openings wait here, to be finished by the test. */
    private List<InvocationOnMock> pending;
    private Map<Column, FakeWindow> windows;
    private MediaPlayers players;
    private ProjectController spreadsheet;

    /** A column's window, recording what it shows. */
    private static final class FakeWindow implements MediaPlayers.ColumnWindow {
        final List<Object> clips = new ArrayList<>();
        Object showing;
        Double volume;
        DoubleConsumer volumeListener;
        Runnable closeListener;
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
        public void showClip(final Object clip, final Double clipVolume) {
            showing = clip;
            volume = clipVolume;
        }

        @Override
        public void setVolumeListener(final DoubleConsumer listener) {
            volumeListener = listener;
        }

        @Override
        public void setCloseListener(final Runnable listener) {
            closeListener = listener;
        }

        @Override
        public void setShowing(final boolean showing) {
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
        videoController = mock(VideoController.class);
        opened = new ArrayList<>();
        failToOpen = false;
        pending = null;
        doAnswer(call -> {
            if (pending != null) {
                pending.add(call);
                return null;
            }
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
        when(videoController.getMediaPlayers()).thenReturn(players);
        spreadsheet = new ProjectController(new Project(), dataStore);
        spreadsheet.setVideoController(videoController);
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
        return player;
    }

    private String file(final String name) throws Exception {
        return Files.createFile(folder.resolve(name)).toString();
    }

    private Cell clip(final String column, final long onset, final String file) throws Exception {
        Column media = dataStore.createMediaColumn(column);
        return media.createClip(onset, MediaClip.of(file));
    }

    @Test
    public void addedMediaGetsANewColumnPlayingFromTimeZero() throws Exception {
        dataStore.createColumn("media_1");

        Column column = players.addMedia(new File(file("cam.mp4")));

        assertEquals(column.getName(), "media_2");
        assertTrue(column.isMedia());
        Cell clip = column.getCells().get(0);
        assertEquals(clip.getOnset(), 0L);
        assertSame(players.getPlayer(clip), opened.get(0));
    }

    /** Media that does not open keeps its column, its clip marked as one that could not be opened. */
    @Test
    public void mediaThatDoesNotOpenKeepsItsColumn() throws Exception {
        failToOpen = true;

        Column column = players.addMedia(new File(file("broken.mp4")));

        assertEquals(dataStore.getAllColumns(), List.of(column));
        assertEquals(column.getCells().get(0).getClipFile(), ClipFile.FAILED);
    }

    /** Missing media keeps its column, its clip without a player, as a missing file in a project does. */
    @Test
    public void missingMediaKeepsItsColumn() throws Exception {
        Column column = players.addMedia(folder.resolve("missing.mp4").toFile());

        assertEquals(dataStore.getAllColumns(), List.of(column));
        assertNull(players.getPlayer(column.getCells().get(0)));
        assertEquals(column.getCells().get(0).getClipFile(), ClipFile.MISSING, "missing, rather than there but unopenable");
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
        assertEquals(clip.getClipFile(), ClipFile.MISSING);
        assertEquals(clip.getOnset(), 2_000L);
        assertEquals(dataStore.getAllColumns().size(), 1);
    }

    @Test
    public void editingAClipsOnsetMovesItsPlayer() throws Exception {
        Cell clip = clip("media_1", 0, file("cam.mp4"));
        players.openAll();
        StreamViewer player = players.getPlayer(clip);

        clip.setOnset(1_500);

        verify(player).setOffset(1_500L);
        verify(videoController, org.mockito.Mockito.atLeastOnce()).updateMaxViewerDuration();
    }

    /** Leaving a spreadsheet's tab hides its media; coming back shows it again, except a hidden column's. */
    @Test
    public void mediaShowsWithItsSpreadsheetUnlessItsColumnIsHidden() throws Exception {
        Cell shown = clip("media_1", 0, file("cam.mp4"));
        Cell hidden = clip("media_2", 0, file("room.mp4"));
        players.openAll();
        hidden.getColumn().setHidden(true);
        org.mockito.Mockito.clearInvocations(players.getPlayer(shown), players.getPlayer(hidden));

        players.setShowing(false);
        verify(players.getPlayer(shown)).setViewerVisible(false);
        verify(players.getPlayer(hidden)).setViewerVisible(false);

        players.setShowing(true);
        verify(players.getPlayer(shown)).setViewerVisible(true);
        verify(players.getPlayer(hidden), org.mockito.Mockito.times(2)).setViewerVisible(false);
    }

    /** Deleting a clip or its column in the spreadsheet closes its player; undo puts both back. */
    @Test
    public void deletingAClipClosesItsPlayerAndUndoReopensIt() throws Exception {
        Cell clip = clip("media_1", 0, file("cam.mp4"));
        players.openAll();
        StreamViewer player = players.getPlayer(clip);
        Column column = clip.getColumn();

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
        Column column = clip.getColumn();

        dataStore.removeColumn(column);
        verify(videoController).shutdown(player.getIdentifier(), false);

        dataStore.addColumn(column, dataStore.getAllColumns().size());
        assertSame(players.getPlayer(clip), opened.get(1));
    }

    /** Closing a spreadsheet's tab closes its players and windows, and it stops following the spreadsheet (B19). */
    @Test
    public void closingClosesEveryPlayerAndWindow() throws Exception {
        Cell clip = clip("media_1", 0, file("cam.mp4"));
        players.openAll();
        StreamViewer player = players.getPlayer(clip);

        players.close();

        verify(videoController).shutdown(player.getIdentifier(), false);
        assertNull(players.getPlayer(clip));
        assertTrue(windows.get(clip.getColumn()).closed);
        clip("media_2", 0, file("mic.mp4"));
        assertEquals(opened.size(), 1);
    }

    /** A player still opening when the spreadsheet closes is closed once it opens, and gets no window. */
    @Test
    public void aPlayerOpeningWhenClosedIsClosedOnceItOpens() throws Exception {
        pending = new ArrayList<>();
        Cell clip = clip("media_1", 0, file("cam.mp4"));
        players.openAll();
        assertEquals(pending.size(), 1);

        players.close();
        InvocationOnMock opening = pending.get(0);
        StreamViewer late = player(opening.getArgument(0), opening.getArgument(1));
        opening.<Consumer<StreamViewer>>getArgument(2).accept(late);
        opening.<Consumer<StreamViewer>>getArgument(3).accept(late);

        verify(videoController).shutdown(late.getIdentifier(), false);
        assertNull(players.getPlayer(clip));
        assertTrue(windows.isEmpty());
    }

    @Test
    public void closingStopsFollowingTheClock() throws Exception {
        ClockTimer clock = mock(ClockTimer.class);
        VideoController playback = mock(VideoController.class);
        when(playback.getClockTimer()).thenReturn(clock);
        MediaPlayers followed = new MediaPlayers(dataStore, playback, column -> new FakeWindow(), Runnable::run);
        verify(clock).registerListener(followed);

        followed.close();

        verify(clock).unRegisterListener(followed);
    }

    @Test
    public void hidingAMediaColumnHidesItsWindow() throws Exception {
        Column media = dataStore.createMediaColumn("media_1");
        media.setHidden(true);
        Cell clip = media.createClip(0, MediaClip.of(file("cam.mp4")));
        StreamViewer player = players.getPlayer(clip);
        verify(player).setViewerVisible(false);

        clip.getColumn().setHidden(false);

        verify(player).setViewerVisible(true);
    }

    @Test
    public void annotationColumnsHaveNoPlayers() throws Exception {
        dataStore.createColumn("notes").createCell();

        players.openAll();

        verify(videoController, never()).openStream(any(), any(), any(), any(), any());
        verify(videoController, never()).openStream(any(), eq(null), any(), any());
    }

    private Cell addClip(final Column column, final long onset, final String name) throws Exception {
        return column.createClip(onset, MediaClip.of(file(name)));
    }

    /** All of a column's clips show in one window, which shows the clip in play and black in the gaps. */
    @Test
    public void aColumnsClipsShareOneWindowShowingTheClipInPlay() throws Exception {
        Column media = dataStore.createMediaColumn("media_1");
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
        Column media = dataStore.createMediaColumn("media_1");
        Cell first = addClip(media, 0, "a.mp4");
        Cell second = addClip(media, 5_000, "b.mp4");

        players.clockPeriodicSync(7_000);

        assertSame(windows.get(media).showing, second);
        verify(players.getPlayer(first), org.mockito.Mockito.atLeastOnce()).setInPlay(false);
    }

    /** Moving a clip changes what plays at once, even while paused. */
    @Test
    public void movingAClipChangesWhatIsInPlay() throws Exception {
        Column media = dataStore.createMediaColumn("media_1");
        Cell clip = addClip(media, 0, "a.mp4");
        FakeWindow window = windows.get(media);
        assertSame(window.showing, clip);

        clip.setOnset(1_000);

        assertNull(window.showing);
    }

    @Test
    public void theWindowClosesWithItsLastClip() throws Exception {
        Column media = dataStore.createMediaColumn("media_1");
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
        Column media = dataStore.createMediaColumn("media_1");
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
        Column media = dataStore.createMediaColumn("media_1");
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
        Column media = dataStore.createMediaColumn("media_1");
        Cell clip = addClip(media, 0, "a.mp4");

        media.setHidden(true);

        verify(players.getPlayer(clip)).setViewerVisible(false);
    }

    /** Added media joins the one selected media column at the current time; otherwise each file gets a column. */
    @Test
    public void addedMediaJoinsTheSelectedMediaColumn() throws Exception {
        Column media = dataStore.createMediaColumn("media_1");
        addClip(media, 0, "a.mp4");
        dataStore.deselectAll();
        media.setSelected(true);

        spreadsheet.media().addMedia(List.of(new File(file("b.mp4"))), 12_000, column -> false);

        assertEquals(dataStore.getAllColumns(), List.of(media));
        assertEquals(media.getCellsTemporally().get(1).getOnset(), 12_000L);
        assertEquals(windows.get(media).clips.size(), 2);
    }

    /** A locked selected column refuses added media; asked once, the files go in new columns or nowhere. */
    @Test
    public void addedMediaGoesToNewColumnsOnlyIfAskedWhenTheSelectedColumnIsLocked() throws Exception {
        Column media = dataStore.createMediaColumn("media_1");
        addClip(media, 0, "a.mp4");
        dataStore.deselectAll();
        media.setSelected(true);
        media.setLocked(true);
        List<Column> asked = new ArrayList<>();
        List<File> files = List.of(new File(file("b.mp4")), new File(file("c.mp4")));

        spreadsheet.media().addMedia(files, 12_000, column -> {
            asked.add(column);
            return false;
        });
        assertEquals(asked, List.of(media));
        assertEquals(dataStore.getAllColumns(), List.of(media));
        assertEquals(media.getCells().size(), 1);

        spreadsheet.media().addMedia(files, 12_000, column -> asked.add(column));
        assertEquals(asked, List.of(media, media));
        assertEquals(dataStore.getAllColumns().stream().map(Column::getName).toList(),
            List.of("media_1", "media_2", "media_3"));
        assertEquals(media.getCells().size(), 1);
    }

    @Test
    public void addedMediaWithoutASelectedMediaColumnGetsNewColumns() throws Exception {
        dataStore.createColumn("notes").setSelected(true);

        List<File> files = List.of(new File(file("a.mp4")), new File(file("b.mp4")));
        spreadsheet.media().addMedia(files, 12_000, column -> false);

        assertEquals(dataStore.getAllColumns().stream().map(Column::getName).toList(),
            List.of("notes", "media_1", "media_2"));
        assertEquals(dataStore.getColumn("media_2").getCells().get(0).getOnset(), 0L);
    }

    /** B14: each file added is an edit, so undo takes the clip out again and closes its player, and redo reopens it. */
    @Test
    public void addingAClipCanBeUndoneAndRedone() throws Exception {
        Column media = dataStore.createMediaColumn("media_1");
        dataStore.deselectAll();
        media.setSelected(true);

        spreadsheet.media().addMedia(List.of(new File(file("b.mp4"))), 12_000, column -> false);
        Cell clip = media.getCells().get(0);
        StreamViewer player = players.getPlayer(clip);
        assertEquals(spreadsheet.getUndoManager().getUndoPresentationName(), "Undo Add Media b.mp4");

        FakeWindow window = windows.get(media);
        spreadsheet.undo();
        assertTrue(media.getCells().isEmpty());
        verify(videoController).shutdown(player.getIdentifier(), false);
        assertTrue(window.closed);

        spreadsheet.redo();
        assertEquals(media.getCells(), List.of(clip));
        assertEquals(opened.size(), 2);
    }

    @Test
    public void addingMediaInNewColumnsIsAnEditForEachFile() throws Exception {
        spreadsheet.media().addMedia(List.of(new File(file("a.mp4")), new File(file("b.mp4"))), 0, column -> false);
        assertEquals(spreadsheet.getUndoManager().getUndoPresentationName(), "Undo Add Media b.mp4");
        Column first = dataStore.getColumn("media_1");

        spreadsheet.undo();
        assertEquals(dataStore.getAllColumns(), List.of(first));
        assertFalse(windows.get(first).closed);
        spreadsheet.undo();
        assertTrue(dataStore.getAllColumns().isEmpty());
        assertTrue(windows.values().stream().allMatch(window -> window.closed));

        spreadsheet.redo();
        assertSame(dataStore.getColumn("media_1"), first);
        assertEquals(first.getCells().size(), 1);
    }

    /**
     * A file that does not open stays, marked as such, and its edit is undone like any other (owner, 2026-11-03).
     */
    @Test
    public void mediaThatFailsToOpenStaysMarkedAndCanBeUndone() throws Exception {
        Column media = dataStore.createMediaColumn("media_1");
        dataStore.deselectAll();
        media.setSelected(true);
        pending = new ArrayList<>();

        spreadsheet.media().addMedia(List.of(new File(file("bad.mp4"))), 5_000, column -> false);
        Cell clip = media.getCells().get(0);
        assertEquals(clip.getClipFile(), ClipFile.FOUND);
        pending.forEach(opening -> opening.<Runnable>getArgument(4).run());

        assertEquals(media.getCells(), List.of(clip));
        assertEquals(clip.getClipFile(), ClipFile.FAILED);
        assertNull(players.getPlayer(clip));
        assertEquals(spreadsheet.getUndoManager().getUndoPresentationName(), "Undo Add Media bad.mp4");
        spreadsheet.undo();
        assertTrue(media.getCells().isEmpty());
    }

    /** Media that does not open in a new column of its own keeps the column, which undo takes out. */
    @Test
    public void mediaThatFailsToOpenInANewColumnCanBeUndone() throws Exception {
        failToOpen = true;

        spreadsheet.media().addMedia(List.of(new File(file("bad.mp4"))), 0, column -> false);
        Column column = dataStore.getColumn("media_1");
        assertEquals(column.getCells().get(0).getClipFile(), ClipFile.FAILED);
        assertEquals(spreadsheet.getUndoManager().getUndoPresentationName(), "Undo Add Media bad.mp4");

        spreadsheet.undo();
        assertTrue(dataStore.getAllColumns().isEmpty());
    }

    /** Media added in a new column leaves the selection alone, and undo and redo do too. */
    @Test
    public void addingMediaKeepsTheSelectionThroughUndoAndRedo() throws Exception {
        Column notes = dataStore.createColumn("notes");
        dataStore.deselectAll();
        notes.setSelected(true);

        spreadsheet.media().addMedia(List.of(new File(file("a.mp4"))), 0, column -> false);
        Column media = dataStore.getColumn("media_1");
        assertEquals(dataStore.getSelectedColumns(), List.of(notes));

        spreadsheet.undo();
        assertEquals(dataStore.getSelectedColumns(), List.of(notes));
        spreadsheet.redo();
        assertEquals(dataStore.getSelectedColumns(), List.of(notes));
        assertFalse(media.isSelected());
    }

    /** A missing file is looked for again when its clip comes back, not each time the clip is drawn. */
    @Test
    public void aMissingFileIsLookedForWhenItsClipComesBack() throws Exception {
        Column media = dataStore.createMediaColumn("media_1");
        dataStore.deselectAll();
        media.setSelected(true);
        spreadsheet.media().addMedia(List.of(folder.resolve("late.mp4").toFile()), 0, column -> false);
        Cell clip = media.getCells().get(0);
        assertEquals(clip.getClipFile(), ClipFile.MISSING);

        file("late.mp4");
        assertEquals(clip.getClipFile(), ClipFile.MISSING, "not looked for until the clip is opened again");
        spreadsheet.undo();
        spreadsheet.redo();

        assertEquals(clip.getClipFile(), ClipFile.FOUND);
        assertNotNull(players.getPlayer(clip));
    }

    /** A file that could not be opened is tried again when its clip comes back, and marked by how that goes. */
    @Test
    public void aClipThatComesBackIsTriedAgain() throws Exception {
        Column media = dataStore.createMediaColumn("media_1");
        dataStore.deselectAll();
        media.setSelected(true);
        failToOpen = true;
        spreadsheet.media().addMedia(List.of(new File(file("b.mp4"))), 2_000, column -> false);
        Cell clip = media.getCells().get(0);
        assertEquals(clip.getClipFile(), ClipFile.FAILED);

        spreadsheet.undo();
        failToOpen = false;
        spreadsheet.redo();

        assertEquals(clip.getClipFile(), ClipFile.FOUND);
        assertTrue(players.getPlayer(clip) != null);
    }

    @Test
    public void aClipThatDoesNotOpenStaysInItsColumn() throws Exception {
        Column media = dataStore.createMediaColumn("media_1");
        Cell kept = addClip(media, 0, "a.mp4");
        failToOpen = true;

        Cell broken = players.addClip(media, new File(file("broken.mp4")), 5_000);

        assertEquals(media.getCells(), List.of(kept, broken));
        assertEquals(broken.getClipFile(), ClipFile.FAILED);
        assertEquals(kept.getClipFile(), ClipFile.FOUND);
        assertEquals(dataStore.getAllColumns(), List.of(media));
    }

    /** Audio is a clip like any other; it has no picture, so it records no size or frame rate. */
    @Test
    public void anAudioClipRecordsOnlyItsLength() throws Exception {
        doAnswer(call -> {
            StreamViewer player = player(call.getArgument(0), call.getArgument(1));
            when(player.getVideoSize()).thenReturn(new Dimension(0, 0));
            call.<Consumer<StreamViewer>>getArgument(2).accept(player);
            call.<Consumer<StreamViewer>>getArgument(3).accept(player);
            opened.add(player);
            return null;
        }).when(videoController).openStream(any(), any(), any(), any(), any());

        Column media = players.addMedia(new File(file("voice.m4a")));

        assertEquals(media.getCells().get(0).getClip(),
            new MediaClip(folder.resolve("voice.m4a").toString(), 10_000, 0, 0, 0, MediaClip.DEFAULT_VOLUME));
    }

    /** Playback stops at the end of the last clip of known length, unless that is turned off. */
    @Test
    public void playbackStopsAtTheEndOfTheLastClip() throws Exception {
        assertEquals(players.stopTime(true), -1L);

        Column room = dataStore.createMediaColumn("room");
        addClip(room, 0, "a.mp4");
        addClip(room, 20_000, "b.mp4");
        Column mic = dataStore.createMediaColumn("mic");
        mic.createClip(40_000, MediaClip.of(folder.resolve("missing.mp4").toString()));
        assertEquals(players.stopTime(true), 30_000L);

        room.getCellsTemporally().get(1).setOnset(25_000);
        assertEquals(players.stopTime(true), 35_000L);
        assertEquals(players.stopTime(false), -1L);
    }

    /** The window shows the volume of the clip in play, and its volume control sets that clip's volume. */
    @Test
    public void theWindowsVolumeControlSetsTheVolumeOfTheClipInPlay() throws Exception {
        Column media = dataStore.createMediaColumn("media_1");
        Cell first = addClip(media, 0, "a.mp4");
        Cell second = addClip(media, 20_000, "b.mp4");
        players.setVolume(second, 0.25);
        FakeWindow window = windows.get(media);

        players.clockForceSync(25_000);
        assertEquals(window.volume, 0.25);

        window.volumeListener.accept(0.6);
        assertEquals(second.getClip().volume(), 0.6);
        assertEquals(first.getClip().volume(), MediaClip.DEFAULT_VOLUME);
        verify(players.getPlayer(second)).setVolumeLevel(0.6f);

        players.clockForceSync(15_000);
        assertNull(window.volume);
        window.volumeListener.accept(0.1);
        assertEquals(first.getClip().volume(), MediaClip.DEFAULT_VOLUME);
    }

    /** A hidden column's clips are out of play, so they stay silent whatever else shows their players. */
    @Test
    public void aHiddenColumnsClipsAreOutOfPlay() throws Exception {
        Column media = dataStore.createMediaColumn("media_1");
        Cell clip = addClip(media, 0, "a.mp4");
        StreamViewer player = players.getPlayer(clip);

        media.setHidden(true);
        players.clockPeriodicSync(1_000);
        verify(player, org.mockito.Mockito.atLeastOnce()).setInPlay(false);

        media.setHidden(false);
        org.mockito.Mockito.clearInvocations(player);
        players.clockPeriodicSync(1_000);
        verify(player).setInPlay(true);
    }

    @Test
    public void closingAMediaWindowHidesItsColumn() throws Exception {
        Column media = dataStore.createMediaColumn("media_1");
        addClip(media, 0, "a.mp4");

        windows.get(media).closeListener.run();

        assertTrue(media.isHidden());
    }

    /** A clip deleted and restored with undo while its player opens ends up with one player, the later one. */
    @Test
    public void aClipRestoredWhileOpeningGetsOnePlayer() throws Exception {
        List<Runnable> pending = new ArrayList<>();
        doAnswer(call -> {
            StreamViewer player = player(call.getArgument(0), call.getArgument(1));
            Consumer<StreamViewer> before = call.getArgument(2);
            Consumer<StreamViewer> after = call.getArgument(3);
            pending.add(() -> {
                before.accept(player);
                after.accept(player);
                opened.add(player);
            });
            return null;
        }).when(videoController).openStream(any(), any(), any(), any(), any());
        Column media = dataStore.createMediaColumn("media_1");
        Cell clip = addClip(media, 0, "a.mp4");

        media.removeCell(clip);
        media.addCell(clip);
        pending.forEach(Runnable::run);

        assertEquals(opened.size(), 2);
        verify(videoController).shutdown(opened.get(0).getIdentifier(), false);
        assertSame(players.getPlayer(clip), opened.get(1));
    }
}
