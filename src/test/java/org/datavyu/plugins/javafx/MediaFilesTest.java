package org.datavyu.plugins.javafx;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import java.io.File;

/** The player opens MP4 video and the audio formats OpenJFX plays on macOS; other files open as projects. */
public class MediaFilesTest {

    @Test
    public void videoAndAudioAreMedia() {
        for (String name : new String[]{"cam.mp4", "cam.MP4", "cam.m4v", "voice.m4a", "voice.mp3", "voice.wav",
            "voice.aif", "voice.AIFF"}) {
            assertTrue(JavaFxVideoPlugin.isMediaFile(new File("/study/" + name)), name);
            assertTrue(JavaFxVideoPlugin.VIDEO_FILE_FILTER.accept(new File("/study/" + name)), name);
        }
    }

    @Test
    public void projectsAndOtherFilesAreNot() {
        for (String name : new String[]{"study.watavyu.json", "old.opf", "data.csv", "cam.mov", "mp4", "notes"}) {
            assertFalse(JavaFxVideoPlugin.isMediaFile(new File("/study/" + name)), name);
        }
    }
}
