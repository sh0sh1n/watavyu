package org.datavyu.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.testng.annotations.Test;

import javax.swing.JOptionPane;

public class DialogsTest {

    private static final String[] OPTIONS = {"Cancel", "Overwrite"};

    @Test
    public void theAnswerIsTheLabelOfTheButtonChosen() {
        assertEquals(Dialogs.chosen(0, OPTIONS), "Cancel");
        assertEquals(Dialogs.chosen(1, OPTIONS), "Overwrite");
    }

    @Test
    public void aClosedDialogChoseNothing() {
        assertNull(Dialogs.chosen(JOptionPane.CLOSED_OPTION, OPTIONS));
        assertNull(Dialogs.chosen(2, OPTIONS));
    }
}
