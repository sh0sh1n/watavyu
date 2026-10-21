package org.datavyu.views;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class VideoControllerTimeTest {

    @Test
    public void currentTimeLabelColoursEachField() {
        assertEquals(VideoController.formatTimeHtml(3_723_004L).replaceAll("<font color=\"#[0-9a-f]{6}\">", "<f>"),
            "<html><f>01</font>:<f>02</font>:<f>03</font>:<f>004</font></html>");
    }

    @Test
    public void currentTimeLabelDoesNotWrapAfterADay() {
        assertEquals(VideoController.formatTime(90_000_000L), "25:00:00:000");
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
}
