package org.datavyu.controllers.project;

import static org.testng.Assert.assertEquals;

import org.datavyu.controllers.project.ProjectController.OlderVideo;
import org.datavyu.models.project.ViewerSetting;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/** An older Datavyu project file's video settings become a clip's onset, volume and hidden column. */
public class OlderVideoTest {

    private static OlderVideo read(final String properties) {
        ViewerSetting setting = new ViewerSetting();
        setting.setFilePath("/study/cam.mp4");
        setting.setSettingsId("1");
        setting.copySettings(new ByteArrayInputStream(properties.getBytes(StandardCharsets.ISO_8859_1)));
        return OlderVideo.of(setting);
    }

    @Test
    public void settingsBecomeTheClipsOnsetVolumeAndVisibility() {
        assertEquals(read("offset=1500\nvolume=0.25\nvisible=false\n"), new OlderVideo(1_500, 0.25, true));
    }

    /** One unreadable value does not stop the project's media from opening. */
    @Test
    public void unreadableValuesFallBackToTheDefaults() {
        assertEquals(read("offset=1234.6\nvolume=loud\n"), new OlderVideo(1_235, 1.0, false));
        assertEquals(read("offset=\nvolume=7\n"), new OlderVideo(0, 1.0, false));
    }

    @Test
    public void theOldestFilesGiveOnlyAnOffset() {
        ViewerSetting setting = new ViewerSetting();
        setting.setFilePath("/study/cam.mp4");
        setting.setOffset(2_000);

        assertEquals(OlderVideo.of(setting), new OlderVideo(2_000, 1.0, false));
    }
}
