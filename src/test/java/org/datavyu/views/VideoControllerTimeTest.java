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
}
