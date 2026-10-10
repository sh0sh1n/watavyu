package org.datavyu.plugins.javafx;

import javafx.scene.media.MediaException;
import org.testng.annotations.Test;

import java.io.File;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class MediaOpenExceptionTest {

    @Test
    public void unsupportedMovSuggestsALosslessRemux() {
        String message = MediaOpenException.forMediaError("session.mov", MediaException.Type.MEDIA_UNSUPPORTED,
                "media type not supported", null).getMessage();

        assertTrue(message.startsWith("Cannot open session.mov: this file type is not supported."));
        assertTrue(message.contains("ffmpeg -i input.mov -c copy -movflags +faststart output.mp4"));
    }

    @Test
    public void unsupportedMp4BlamesTheCodecWithoutARemuxHint() {
        String message = MediaOpenException.forMediaError("clip.MP4", MediaException.Type.MEDIA_UNSUPPORTED,
                "unsupported codec", null).getMessage();

        assertTrue(message.contains("the video or audio codec is not supported"));
        assertTrue(message.contains("H.264 video and AAC audio"));
        assertFalse(message.contains("ffmpeg"));
    }

    @Test
    public void unreadableFilesIncludeThePlayerDetail() {
        String message = MediaOpenException.forMediaError("clip.mp4", MediaException.Type.MEDIA_CORRUPTED,
                "bad header", null).getMessage();

        assertEquals(message, "Cannot open clip.mp4: the file could not be read (bad header).");
    }

    @Test
    public void otherErrorsKeepTheirMessage() {
        String message = MediaOpenException.from(new File("clip.mp4"), new IllegalStateException("boom")).getMessage();

        assertEquals(message, "Cannot open clip.mp4: boom");
    }

    @Test
    public void timeoutsNameTheFileAndLimit() {
        assertEquals(MediaOpenException.timedOut(new File("clip.mp4"), 30).getMessage(),
                "Cannot open clip.mp4: the player did not become ready within 30 seconds.");
    }
}
