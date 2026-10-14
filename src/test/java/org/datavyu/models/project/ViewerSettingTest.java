package org.datavyu.models.project;

import static org.testng.Assert.assertEquals;

import java.util.UUID;

import org.datavyu.plugins.javafx.JavaFxVideoPlugin;
import org.testng.annotations.Test;

public class ViewerSettingTest {

    @Test
    public void theOpenJfxPlayersUuidNamesItsViewer() {
        ViewerSetting setting = new ViewerSetting();
        setting.setPluginNameAndUUID(JavaFxVideoPlugin.PLUGIN_UUID);
        assertEquals(setting.getPluginName(), JavaFxVideoPlugin.VIEWER_CLASS_NAME);
        assertEquals(setting.playerMatch(), ViewerSetting.PlayerMatch.SAME_PLAYER);
    }

    @Test
    public void aTrackFromAnotherVideoPlayerOpensWithTheOpenJfxPlayer() {
        ViewerSetting setting = new ViewerSetting();
        setting.setPluginNameAndUUID(UUID.randomUUID());
        setting.setPluginClassifier(JavaFxVideoPlugin.NAMESPACE);
        assertEquals(setting.playerMatch(), ViewerSetting.PlayerMatch.OTHER_VIDEO_PLAYER);
    }

    @Test
    public void aTrackFromAViewerOfAnotherKindCannotOpen() {
        ViewerSetting setting = new ViewerSetting();
        setting.setPluginName("org.datavyu.plugins.audio.AudioViewer");
        setting.setPluginClassifier("datavyu.audio");
        assertEquals(setting.playerMatch(), ViewerSetting.PlayerMatch.NONE);
    }

    @Test
    public void aTrackThatDoesNotNameItsKindCannotOpen() {
        ViewerSetting setting = new ViewerSetting();
        setting.setPluginName("org.datavyu.plugins.quicktime.QTDataViewer");
        assertEquals(setting.playerMatch(), ViewerSetting.PlayerMatch.NONE);
    }
}
