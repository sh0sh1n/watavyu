package com.sh0sh1n.watavyu.views;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.TestSpreadsheets;
import com.sh0sh1n.watavyu.controllers.project.MediaPlayers;
import com.sh0sh1n.watavyu.models.db.DataStoreFactory;
import com.sh0sh1n.watavyu.plugins.StreamViewer;
import org.testng.annotations.Test;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 * Closing a tab's playback stops its clock and closes its media players, and a player that finishes opening afterwards
 * is closed rather than added (B19).
 */
public class VideoControllerCloseTest {

    private static VideoController closedPlayback(final MediaPlayers mediaPlayers) throws Exception {
        AtomicReference<VideoController> playback = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            playback.set(TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore()).getVideoController());
            playback.get().setMediaPlayers(mediaPlayers);
            playback.get().close();
        });
        return playback.get();
    }

    @Test
    public void closingStopsTheClockAndClosesTheMediaPlayers() throws Exception {
        MediaPlayers mediaPlayers = mock(MediaPlayers.class);

        VideoController playback = closedPlayback(mediaPlayers);

        verify(mediaPlayers).close();
        assertTrue(playback.getClockTimer().isClosed());
    }

    @Test
    public void aPlayerThatOpensAfterClosingIsClosed() throws Exception {
        VideoController playback = closedPlayback(mock(MediaPlayers.class));
        StreamViewer late = mock(StreamViewer.class);
        AtomicBoolean told = new AtomicBoolean();

        SwingUtilities.invokeAndWait(() -> playback.streamOpened(late, viewer -> told.set(true), viewer -> told.set(true)));

        verify(late).close();
        assertFalse(told.get());
    }

    /** No error dialog appears for a tab that has gone; one would block this test until its time out. */
    @Test(timeOut = 10_000)
    public void aPlayerThatFailsAfterClosingSaysNothing() throws Exception {
        VideoController playback = closedPlayback(mock(MediaPlayers.class));
        AtomicBoolean failed = new AtomicBoolean();

        SwingUtilities.invokeAndWait(() -> playback.streamFailed(new IllegalStateException("unreadable"),
            new File("cam.mp4"), () -> failed.set(true)));

        assertFalse(failed.get());
    }
}
