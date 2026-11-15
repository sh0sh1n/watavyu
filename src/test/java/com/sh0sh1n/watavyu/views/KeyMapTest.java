package com.sh0sh1n.watavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

import org.testng.annotations.Test;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.KeyStroke;

/** The key map (AGENTS.md › Decisions, 2026-11-13): its default keys, and how a key is written. */
public class KeyMapTest {

    private static Map<String, Command> defaults() {
        Map<String, Command> keys = new LinkedHashMap<>();
        for (KeyMap.Binding binding : KeyMap.defaultMap().bindings()) {
            keys.put(binding.key(), binding.command());
        }
        return keys;
    }

    @Test
    public void theDefaultMapIsTheDecidedOne() {
        Map<String, Command> expected = new LinkedHashMap<>();
        expected.put("space", Command.PLAY_PAUSE);
        expected.put("=", Command.RESET_RATE);
        expected.put(",", Command.JOG_BACK);
        expected.put(".", Command.JOG_FORWARD);
        expected.put("<", Command.SHUTTLE_BACK);
        expected.put(">", Command.SHUTTLE_FORWARD);
        expected.put("b", Command.JUMP_BACK);
        expected.put("w", Command.JUMP_FORWARD);
        expected.put("[", Command.SET_ONSET);
        expected.put("]", Command.SET_OFFSET);
        expected.put("{", Command.GO_TO_ONSET);
        expected.put("}", Command.GO_TO_OFFSET);
        expected.put("o", Command.NEW_CELL_HERE);
        expected.put("O", Command.NEW_CELL_HERE_CLOSING_PREVIOUS);
        expected.put("x", Command.DELETE_CELLS);
        expected.put("u", Command.UNDO);
        expected.put("i", Command.INSERT_MODE);
        expected.put("ctrl+j", Command.NEXT_CELL);
        expected.put("ctrl+k", Command.PREVIOUS_CELL);
        expected.put("ctrl+h", Command.COLUMN_LEFT);
        expected.put("ctrl+l", Command.COLUMN_RIGHT);
        expected.put("ctrl+p", Command.PLAY_PAUSE);
        expected.put("ctrl+r", Command.REDO);
        expected.put("escape", Command.COMMAND_MODE);
        expected.put("ctrl+]", Command.COMMAND_MODE);
        assertEquals(List.copyOf(defaults().entrySet()), List.copyOf(expected.entrySet()));
    }

    /** Plain keys are letters, digits and punctuation, so playback and timing keys still work in quick mode. */
    @Test
    public void plainKeysAreCharactersAndChordsHoldControlOrAreEscape() {
        for (KeyMap.Binding binding : KeyMap.defaultMap().bindings()) {
            if (binding.layer() == KeyMap.Layer.COMMAND) {
                assertEquals(binding.stroke().getKeyEventType(), KeyEvent.KEY_TYPED, binding.key());
            } else {
                int modifiers = binding.stroke().getModifiers();
                boolean chord = (modifiers & InputEvent.CTRL_DOWN_MASK) != 0
                    || binding.stroke().getKeyCode() == KeyEvent.VK_ESCAPE;
                assertEquals(chord, true, binding.key());
            }
        }
    }

    @Test
    public void aCharacterIsACommandKey() {
        KeyMap.Binding o = KeyMap.parse("O", Command.UNDO);
        assertEquals(o.layer(), KeyMap.Layer.COMMAND);
        assertEquals(o.stroke(), KeyStroke.getKeyStroke('O'));
        assertEquals(KeyMap.parse("space", Command.UNDO).stroke(), KeyStroke.getKeyStroke(' '));
        assertEquals(KeyMap.parse("{", Command.UNDO).stroke(), KeyStroke.getKeyStroke('{'));
    }

    @Test
    public void aChordIsAKeyHeldWithControl() {
        assertEquals(KeyMap.parse("ctrl+]", Command.UNDO).stroke(),
            KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, InputEvent.CTRL_DOWN_MASK));
        assertEquals(KeyMap.parse("Ctrl+J", Command.UNDO).stroke(),
            KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK), "in any case");
        assertEquals(KeyMap.parse("ctrl+shift+j", Command.UNDO).stroke(),
            KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertEquals(KeyMap.parse("escape", Command.UNDO).stroke(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        assertEquals(KeyMap.parse("esc", Command.UNDO).layer(), KeyMap.Layer.CHORD);
    }

    /** ⌘ keys stay fixed, so a chord holds ⌃; ⌥ or ⇧ alone type characters. */
    @Test
    public void keysThatCannotBeMappedAreRefused() {
        for (String key : new String[] {"", "ab", "cmd+j", "alt+j", "shift+j", "ctrl+", "ctrl+jj", "\t", " "}) {
            expectThrows(IllegalArgumentException.class, () -> KeyMap.parse(key, Command.UNDO));
        }
    }

    @Test
    public void oneKeyCannotBeMappedTwice() {
        Map<String, Command> keys = new LinkedHashMap<>();
        keys.put("escape", Command.COMMAND_MODE);
        keys.put("esc", Command.UNDO);
        expectThrows(IllegalArgumentException.class, () -> KeyMap.of(keys));
    }

    @Test
    public void keysAreShownAsTheyAreTypedOrAsMacMenusShowChords() {
        KeyMap map = KeyMap.defaultMap();
        assertEquals(map.keyText(Command.PLAY_PAUSE), Optional.of("Space"));
        assertEquals(map.keyText(Command.NEW_CELL_HERE_CLOSING_PREVIOUS), Optional.of("O"));
        assertEquals(map.keyText(Command.NEXT_CELL), Optional.of("⌃J"));
        assertEquals(map.keyText(Command.COMMAND_MODE), Optional.of("Esc"));
        assertEquals(map.keyText(Command.SAVE), Optional.empty());
    }
}
