package org.datavyu.views.discrete;

import static org.testng.Assert.assertFalse;

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
}
