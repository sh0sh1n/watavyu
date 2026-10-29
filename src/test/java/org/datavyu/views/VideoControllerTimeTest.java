package org.datavyu.views;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class VideoControllerTimeTest {

    /** The playhead shows the rate as a decimal while playing, and says when playback is paused or stopped. */
    @Test
    public void thePlayheadShowsTheRate() {
        assertEquals(VideoController.rateText(1F, false), "1×");
        assertEquals(VideoController.rateText(1.25F, false), "1.25×");
        assertEquals(VideoController.rateText(0.75F, true), "paused 0.75×");
        assertEquals(VideoController.rateText(0F, true), "stopped");
    }

    /** Moving the clock moves the playhead. */
    @Test
    public void movingTheClockMovesThePlayhead() {
        VideoController controller = new VideoController();
        List<String> shown = new ArrayList<>();
        controller.setPlayhead((time, rate) -> shown.add(time + " " + rate));
        assertEquals(shown, List.of("0 paused 1×"), "a new clock is paused at the normal rate");

        controller.setCurrentTime(5_000);

        assertEquals(shown.get(shown.size() - 1), "5000 paused 1×");
    }

    /** Shuttling forward at time zero plays faster, as it does anywhere else (B7). */
    @Test
    public void shuttlingAtTimeZeroPlays() {
        VideoController controller = new VideoController();
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
        org.datavyu.plugins.StreamViewer clip = org.mockito.Mockito.mock(org.datavyu.plugins.StreamViewer.class);
        org.mockito.Mockito.when(clip.getOffset()).thenReturn(60_000L);
        org.mockito.Mockito.when(clip.getDuration()).thenReturn(60_000L);

        assertEquals(VideoController.holds(clip, 90_000), true);
        assertEquals(VideoController.holds(clip, 60_000), true);
        assertEquals(VideoController.holds(clip, 59_999), false);
        assertEquals(VideoController.holds(clip, 120_000), false);
    }

    /** Playback starting at time zero or at the end runs on when it heads away from that edge (B7). */
    @Test
    public void playbackPausesOnlyAtTheEdgeItIsHeadingFor() {
        assertEquals(VideoController.atTimelineEdge(0, 1F, 60_000), false, "playing forward from zero");
        assertEquals(VideoController.atTimelineEdge(0, -1F, 60_000), true, "playing backward into zero");
        assertEquals(VideoController.atTimelineEdge(60_000, 1F, 60_000), true, "playing forward into the end");
        assertEquals(VideoController.atTimelineEdge(60_000, -1F, 60_000), false, "playing backward from the end");
        assertEquals(VideoController.atTimelineEdge(30_000, 2F, 60_000), false);
        assertEquals(VideoController.atTimelineEdge(0, 0F, 60_000), false, "stopped");
    }
}
