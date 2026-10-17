package org.datavyu.views.discrete;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.views.discrete.datavalues.FixedText;
import org.testng.annotations.Test;

import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.JTextArea;

public class EditorTrackerTest {

    /** An editable value covering the whole text area. */
    private static final class Value extends EditorComponent {

        Value(final JTextArea textArea) {
            super(textArea);
            setEditable(true);
            resetText(textArea.getText());
        }

        @Override
        public void keyPressed(final KeyEvent e) {
        }

        @Override
        public void keyTyped(final KeyEvent e) {
        }

        @Override
        public void keyReleased(final KeyEvent e) {
        }

        @Override
        public void focusGained(final FocusEvent fe) {
        }
    }

    /** B6: a backslash without ⌘ is a character to type, not a Backspace. */
    @Test
    public void aBackslashAtTheStartOfAValueIsTyped() {
        JTextArea textArea = new JTextArea("value");
        Value value = new Value(textArea);
        EditorTracker tracker = new EditorTracker(textArea, List.of(value));
        tracker.setEditor(value, 0, 0);
        KeyEvent backslash = new KeyEvent(textArea, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_BACK_SLASH, '\\');

        tracker.keyPressed(backslash);

        assertFalse(backslash.isConsumed());
    }

    /**
     * B9: a cell with nothing to edit, such as a media clip or a cell of a column with no codes, refuses typing, so no
     * text appears that the cell would not keep.
     */
    @Test
    public void aCellWithNothingToEditRefusesTyping() {
        JTextArea textArea = new JTextArea("()");
        EditorTracker tracker = new EditorTracker(textArea,
            List.of(new FixedText(textArea, "("), new FixedText(textArea, ")")));
        tracker.focusGained(null);
        KeyEvent typed = new KeyEvent(textArea, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'x');
        KeyEvent backspace = new KeyEvent(textArea, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_BACK_SPACE, '\b');
        KeyEvent delete = new KeyEvent(textArea, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_DELETE, '\u007F');

        tracker.keyTyped(typed);
        tracker.keyPressed(backspace);
        tracker.keyPressed(delete);

        assertTrue(typed.isConsumed());
        assertTrue(backspace.isConsumed());
        assertTrue(delete.isConsumed());
        assertEquals(textArea.getText(), "()");
    }

    @Test
    public void anEditableValueTakesTyping() {
        JTextArea textArea = new JTextArea("value");
        Value value = new Value(textArea);
        EditorTracker tracker = new EditorTracker(textArea, List.of(value));
        tracker.setEditor(value, 0, 0);
        KeyEvent typed = new KeyEvent(textArea, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'x');

        tracker.keyTyped(typed);

        assertFalse(typed.isConsumed());
    }
}
