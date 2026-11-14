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
import javax.swing.SwingUtilities;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class VideoControllerTimeTest {

    /** The playhead shows the rate as a decimal while playing, and says when playback is paused. */
    @Test
    public void thePlayheadShowsTheRate() {
        assertEquals(VideoController.rateText(1F, false), "1×");
        assertEquals(VideoController.rateText(1.25F, false), "1.25×");
        assertEquals(VideoController.rateText(0.75F, true), "paused 0.75×");
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
            clock.start();

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!clock.isPaused() && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertTrue(clock.isPaused(), "paused, at " + clock.getClockTime());
            assertEquals(clock.getClockTime(), 2_150.0);
        } finally {
            controller.pause();
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
            controller.pause();
        }
    }

    /** Play or pause pauses playback, and resumes it at the rate it paused at; a new spreadsheet plays at 1×. */
    @Test
    public void playOrPauseResumesAtThePausedRate() {
        VideoController controller = TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore()).getVideoController();
        ClockTimer clock = controller.getClockTimer();
        try {
            controller.playPauseAction();
            assertEquals(clock.isPaused(), false);
            assertEquals(clock.getRate(), 1F, "a new spreadsheet plays at 1×");

            controller.shuttleForwardAction();
            controller.playPauseAction();
            assertEquals(clock.isPaused(), true);
            assertEquals(clock.getRate(), 1.25F, "pausing keeps the rate");

            controller.playPauseAction();
            assertEquals(clock.isPaused(), false);
            assertEquals(clock.getRate(), 1.25F, "playing resumes at it");
        } finally {
            controller.pause();
        }
    }

    /** Reset sets the rate back to 1×, and neither starts nor pauses playback. */
    @Test
    public void resetSetsTheRateToOneWithoutStartingOrPausing() {
        VideoController controller = TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore()).getVideoController();
        ClockTimer clock = controller.getClockTimer();
        try {
            controller.shuttleForwardAction();
            controller.shuttleForwardAction();
            controller.resetRateAction();
            assertEquals(clock.getRate(), 1F);
            assertEquals(clock.isPaused(), false, "playing carries on");

            controller.shuttleForwardAction();
            controller.pause();
            controller.resetRateAction();
            assertEquals(clock.getRate(), 1F);
            assertEquals(clock.isPaused(), true, "paused stays paused");
        } finally {
            controller.pause();
        }
    }

    /** Shuttling while paused plays at the next rate. */
    @Test
    public void shuttlingWhilePausedPlays() {
        VideoController controller = TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore()).getVideoController();
        ClockTimer clock = controller.getClockTimer();
        try {
            controller.shuttleBackAction();
            assertEquals(clock.isPaused(), false);
            assertEquals(clock.getRate(), 0.75F);
        } finally {
            controller.pause();
        }
    }

    /**
     * A jog while playing pauses and steps a frame, as one while paused steps: with no video open, by a 30th of a
     * second, to a frame marker (a multiple of 34 ms).
     */
    @Test
    public void joggingWhilePlayingPausesAndSteps() {
        VideoController controller = TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore()).getVideoController();
        ClockTimer clock = controller.getClockTimer();
        try {
            controller.setCurrentTime(10_000);
            controller.playPauseAction();
            controller.jogForwardAction();
            assertEquals(clock.isPaused(), true);
            long forward = controller.getCurrentTime();
            assertEquals(forward % 34, 0L);
            assertTrue(forward > 10_000L, "stepped forward to " + forward);

            controller.playPauseAction();
            controller.jogBackAction();
            assertEquals(clock.isPaused(), true);
            long back = controller.getCurrentTime();
            assertEquals(back % 34, 0L);
            assertTrue(back < forward + 1_000L, "stepped back to " + back);

            controller.jogBackAction();
            assertEquals(controller.getCurrentTime(), back - 34, "paused, a jog steps");
        } finally {
            controller.pause();
        }
    }

    /**
     * A jog while playing ends with a forced time at the step, told after the pause's own, so a player told of the
     * pause after it stepped is taken to the step rather than left where playback paused.
     */
    @Test
    public void aJogWhilePlayingTellsThePlayersTheStepLast() throws InterruptedException {
        VideoController controller = TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore()).getVideoController();
        ClockTimer clock = controller.getClockTimer();
        List<Long> forced = new CopyOnWriteArrayList<>();
        CountDownLatch told = new CountDownLatch(1);
        try {
            controller.setCurrentTime(10_000);
            controller.playPauseAction();
            clock.registerListener(new ClockTimer.ClockListener() {
                @Override
                public void clockTick(final double clockTime) {
                    // Not followed.
                }

                @Override
                public void clockForceSync(final double clockTime) {
                    forced.add((long) clockTime);
                    if (forced.size() == 2) {
                        told.countDown();
                    }
                }

                @Override
                public void clockStart(final double clockTime) {
                    // Not followed.
                }

                @Override
                public void clockPause(final double clockTime) {
                    // Not followed.
                }

                @Override
                public void clockRate(final float rate) {
                    // Not followed.
                }
            });

            controller.jogForwardAction();

            assertTrue(told.await(5, TimeUnit.SECONDS), "forced: " + forced);
            // The pause's may be told before the step is made, with the time it paused at; the last is the step's.
            assertEquals(forced.get(forced.size() - 1), (Long) controller.getCurrentTime(), "forced: " + forced);
        } finally {
            controller.pause();
        }
    }

    /** The play key shows pause while playing, and play once paused, however the clock paused. */
    @Test
    public void thePlayKeyShowsWhatPressingItDoes() throws Exception {
        VideoController controller = TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore()).getVideoController();
        KeypadKey play = (KeypadKey) find(controller.getControls(), "playPauseButton");
        try {
            assertEquals(play.getGlyph(), Glyph.Shape.PLAY);

            controller.playPauseAction();
            awaitGlyph(play, Glyph.Shape.PAUSE);

            controller.playPauseAction();
            awaitGlyph(play, Glyph.Shape.PLAY);

            controller.setCurrentTime(VideoController.MINIMUM_TIMELINE_END - 100);
            controller.shuttleForwardAction();
            awaitGlyph(play, Glyph.Shape.PAUSE);
            awaitGlyph(play, Glyph.Shape.PLAY);
            assertTrue(controller.getClockTimer().isPaused(), "paused at the end of the timeline");
        } finally {
            controller.pause();
        }
    }

    private static void awaitGlyph(final KeypadKey key, final Glyph.Shape shape) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        String label = shape == Glyph.Shape.PLAY ? "play" : "pause";
        Glyph.Shape[] shown = new Glyph.Shape[1];
        String[] text = new String[1];
        do {
            SwingUtilities.invokeAndWait(() -> {
                shown[0] = key.getGlyph();
                text[0] = key.getText();
            });
            if (shown[0] == shape && label.equals(text[0])) {
                return;
            }
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        assertEquals(shown[0], shape);
        assertEquals(text[0], label);
    }

    private static java.awt.Component find(final java.awt.Container container, final String name) {
        for (java.awt.Component child : container.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof java.awt.Container inner) {
                java.awt.Component found = find(inner, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
