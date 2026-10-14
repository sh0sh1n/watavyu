package org.datavyu.views.discrete;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JPanel;

/** B5: plain arrow keys belong to the cell being edited; only ⌘← and ⌘→ select another column. */
public class ColumnMoveKeyTest {

    private static final int COMMAND = InputEvent.META_DOWN_MASK;

    private static KeyEvent press(final int key, final int modifiers) {
        return new KeyEvent(new JPanel(), KeyEvent.KEY_PRESSED, 0, modifiers, key, KeyEvent.CHAR_UNDEFINED);
    }

    @Test
    public void plainArrowsAreNotColumnMoves() {
        assertFalse(SpreadSheetPanel.isColumnMove(press(KeyEvent.VK_LEFT, 0), COMMAND));
        assertFalse(SpreadSheetPanel.isColumnMove(press(KeyEvent.VK_RIGHT, 0), COMMAND));
    }

    @Test
    public void commandArrowsAreColumnMoves() {
        assertTrue(SpreadSheetPanel.isColumnMove(press(KeyEvent.VK_LEFT, COMMAND), COMMAND));
        assertTrue(SpreadSheetPanel.isColumnMove(press(KeyEvent.VK_RIGHT, COMMAND), COMMAND));
    }

    @Test
    public void otherModifiersAreNotColumnMoves() {
        assertFalse(SpreadSheetPanel.isColumnMove(press(KeyEvent.VK_LEFT, COMMAND | InputEvent.SHIFT_DOWN_MASK),
            COMMAND));
        assertFalse(SpreadSheetPanel.isColumnMove(press(KeyEvent.VK_UP, COMMAND), COMMAND));
    }
}
