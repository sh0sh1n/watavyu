package com.sh0sh1n.watavyu.views;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.TestSpreadsheets;
import com.sh0sh1n.watavyu.controllers.MediaPlayers;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.util.ClockTimer;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class VideoControllerTimeTest {

    /** The playhead shows the rate as a decimal while playing, and says when playback is paused or stopped. */
    @Test
    public void thePlayheadShowsTheRate() {
        assertEquals(VideoController.rateText(1F, false), "1×");
        assertEquals(VideoController.rateText(1.25F, false), "1.25×");
        assertEquals(VideoController.rateText(0.75F, true), "paused 0.75×");
        assertEquals(VideoController.rateText(0F, true), "stopped");
    }

    /** Moving the clock moves the playhead, as soon as the clock's thread tells it the new time. */
    @Test
    public void movingTheClockMovesThePlayhead() throws InterruptedException {
        VideoController controller = TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore()).getVideoController();
        List<String> shown = new CopyOnWriteArrayList<>();
        CountDownLatch moved = new CountDownLatch(1);
        controller.setPlayhead((time, rate, playing) -> {
            String reading = time + " " + rate + (playing ? " playing" : "");
            shown.add(reading);
            if (reading.equals("5000 paused 1×")) {
                moved.countDown();
            }
        });
        assertEquals(shown.get(0), "0 paused 1×", "a new clock is paused at the normal rate");

        controller.setCurrentTime(5_000);

        assertTrue(moved.await(5, TimeUnit.SECONDS), "shown: " + shown);
    }

    /**
     * Playing forward pauses at the end of the media its media columns give, which follows the stop-at-end-of-media
     * setting (on by default), rather than running on to the end of the timeline.
     */
    @Test
    public void playbackPausesAtTheEndOfTheMedia() throws InterruptedException {
        VideoController controller = TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore()).getVideoController();
        MediaPlayers players = mock(MediaPlayers.class);
        when(players.stopTime(anyBoolean())).thenAnswer(asked -> (boolean) asked.getArgument(0) ? 2_150L : -1L);
        controller.setMediaPlayers(players);
        ClockTimer clock = controller.getClockTimer();
        try {
            clock.setForceTime(2_000);
            clock.setRate(2f);

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!clock.isPaused() && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertTrue(clock.isPaused(), "paused, at " + clock.getClockTime());
            assertEquals(clock.getClockTime(), 2_150.0);
        } finally {
            controller.stopAction();
        }
    }

    /** Shuttling forward at time zero plays faster, as it does anywhere else (B7). */
    @Test
    public void shuttlingAtTimeZeroPlays() {
        VideoController controller = TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore()).getVideoController();
        try {
            controller.shuttleForwardAction();
            assertEquals(controller.getClockTimer().getRate(), 1.25F);
            assertEquals(controller.getClockTimer().isPaused(), false);
        } finally {
            controller.stopAction();
        }
    }

    /** Jogging steps by the highest frame rate of the open video; with only audio open, by 30 frames a second. */
    @Test
    public void jogStepsByTheVideosFrameRateOrThirty() {
        assertEquals(VideoController.jogFrameRate(29.97), 29.97);
        assertEquals(VideoController.jogFrameRate(0), 30.0);
    }

    /** Only a clip that plays at the clock's time steps by frame when jogging, so the clock stays with it. */
    @Test
    public void onlyAClipPlayingNowStepsByFrame() {
        com.sh0sh1n.watavyu.playback.StreamViewer clip = org.mockito.Mockito.mock(com.sh0sh1n.watavyu.playback.StreamViewer.class);
        org.mockito.Mockito.when(clip.getOffset()).thenReturn(60_000L);
        org.mockito.Mockito.when(clip.getDuration()).thenReturn(60_000L);

        assertEquals(VideoController.holds(clip, 90_000), true);
        assertEquals(VideoController.holds(clip, 60_000), true);
        assertEquals(VideoController.holds(clip, 59_999), false);
        assertEquals(VideoController.holds(clip, 120_000), false);
    }
}
