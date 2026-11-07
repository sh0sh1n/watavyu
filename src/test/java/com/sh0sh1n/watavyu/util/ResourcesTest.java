package com.sh0sh1n.watavyu.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.sh0sh1n.watavyu.Watavyu;
import com.sh0sh1n.watavyu.views.VideoController;
import org.testng.annotations.Test;

public class ResourcesTest {

    @Test
    public void readsTheClassResourceFile() {
        assertEquals(Resources.of(VideoController.class).getString("onsetTextField.toolTipText"),
            "Use \"+\" to find this cellValue");
    }

    @Test
    public void fallsBackToTheApplicationResourceFile() {
        assertEquals(Resources.of(VideoController.class).getString("ClosePluginDialog.title"), "Remove Track");
        assertEquals(Resources.of(Settings.class).getString("Application.title"), "Watavyu");
    }

    @Test
    public void formatsArguments() {
        assertEquals(Resources.of(Watavyu.class).getString("UnsavedDialog.tabmessage", "Study 1"),
            "Spreadsheet 'Study 1' has unsaved changes.\nDo you want to save before closing this tab?");
    }

    @Test
    public void trimsSurroundingSpaces() {
        assertEquals(Resources.of(Watavyu.class).getString("WarningDialog.title"), "Warning:");
    }

    @Test
    public void missingKeysGiveNull() {
        assertNull(Resources.of(VideoController.class).getString("no.such.key"));
    }
}
