package com.sh0sh1n.watavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
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
        expected.put("q", Command.QUICK_MODE);
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
        assertEquals(KeyMap.parse("ctrl+shift+[", Command.UNDO).stroke(),
            KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertEquals(KeyMap.parse("ctrl+space", Command.UNDO).stroke(),
            KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK));
        assertEquals(KeyMap.parse("+", Command.UNDO).stroke(), KeyStroke.getKeyStroke('+'), "a plus alone is a character");
    }

    /**
     * ⌘ keys stay fixed, so a chord holds ⌃; ⌥ or ⇧ alone type characters. A chord names a key as it is unshifted, as
     * a press reports it: ⌃{ is ⌃⇧[.
     */
    @Test
    public void keysThatCannotBeMappedAreRefused() {
        for (String key : new String[] {"", "ab", "cmd+j", "alt+j", "shift+j", "ctrl+", "ctrl+jj", "\t", " ", "ctrl+{", "ctrl+<", "ctrl+é", "ctrl++", "shift++", "ctrl+shift+", "spacebar"}) {
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
    // The user's changes (Settings' keys).

    private static Map<String, String> changes(final String... keysAndCommands) {
        Map<String, String> changes = new LinkedHashMap<>();
        for (int i = 0; i < keysAndCommands.length; i += 2) {
            changes.put(keysAndCommands[i], keysAndCommands[i + 1]);
        }
        return changes;
    }

    private static Optional<Command> commandOn(final KeyMap map, final String key) {
        KeyStroke stroke = KeyMap.parse(key, Command.UNDO).stroke();
        return map.bindings().stream().filter(binding -> binding.stroke().equals(stroke)).map(KeyMap.Binding::command)
            .findFirst();
    }

    @Test
    public void aChangeMapsAKeyInPlaceOfWhatItRan() {
        KeyMap.WithChanges changed = KeyMap.defaultMap().withChanges(changes("x", "UNDO", "d", "DELETE_CELLS",
            "ctrl+n", "NEXT_CELL"));
        assertEquals(changed.problems(), List.of());
        assertEquals(commandOn(changed.map(), "x"), Optional.of(Command.UNDO));
        assertEquals(commandOn(changed.map(), "d"), Optional.of(Command.DELETE_CELLS));
        assertEquals(commandOn(changed.map(), "ctrl+n"), Optional.of(Command.NEXT_CELL));
        assertEquals(commandOn(changed.map(), "ctrl+j"), Optional.of(Command.NEXT_CELL), "the default key stays");
        assertEquals(changed.map().bindings().size(), KeyMap.defaultMap().bindings().size() + 2);
        int place = KeyMap.defaultMap().bindings().stream().map(KeyMap.Binding::key).toList().indexOf("x");
        assertEquals(changed.map().bindings().get(place).key(), "x", "a changed key keeps its place in the list");
    }

    @Test
    public void anEmptyCommandTurnsAKeyOff() {
        KeyMap.WithChanges changed = KeyMap.defaultMap().withChanges(changes("x", "", "escape", " "));
        assertEquals(changed.problems(), List.of());
        assertEquals(commandOn(changed.map(), "x"), Optional.empty());
        assertEquals(commandOn(changed.map(), "escape"), Optional.empty());
        assertEquals(commandOn(changed.map(), "ctrl+]"), Optional.of(Command.COMMAND_MODE));
    }

    /** A change that cannot be read is left out and reported; the others still apply, so the app always starts. */
    @Test
    public void aChangeThatCannotBeReadIsLeftOut() {
        KeyMap.WithChanges changed = KeyMap.defaultMap().withChanges(changes("cmd+s", "UNDO", "d", "DELETE",
            "u", "REDO"));
        assertEquals(changed.problems().size(), 2, changed.problems().toString());
        assertTrue(changed.problems().get(0).contains("cmd+s"), changed.problems().get(0));
        assertTrue(changed.problems().get(1).contains("DELETE"), changed.problems().get(1));
        assertEquals(commandOn(changed.map(), "d"), Optional.empty());
        assertEquals(commandOn(changed.map(), "u"), Optional.of(Command.REDO));
        assertEquals(changed.map().bindings().size(), KeyMap.defaultMap().bindings().size());
    }

    @Test
    public void commandsAreNamedInAnyCase() {
        KeyMap.WithChanges changed = KeyMap.defaultMap().withChanges(changes("Ctrl+N", "next_cell"));
        assertEquals(changed.problems(), List.of());
        assertEquals(commandOn(changed.map(), "ctrl+n"), Optional.of(Command.NEXT_CELL));
    }

    /** Some key always leaves insert mode: turning off Esc and ⌃] leaves Esc on, and says so. */
    @Test
    public void someKeyAlwaysLeavesInsertMode() {
        KeyMap.WithChanges changed = KeyMap.defaultMap().withChanges(changes("escape", "", "ctrl+]", ""));
        assertEquals(commandOn(changed.map(), "escape"), Optional.of(Command.COMMAND_MODE));
        assertEquals(commandOn(changed.map(), "ctrl+]"), Optional.empty());
        assertEquals(changed.problems().size(), 1, changed.problems().toString());

        KeyMap.WithChanges moved = KeyMap.defaultMap().withChanges(changes("escape", "", "ctrl+[", "COMMAND_MODE"));
        assertEquals(commandOn(moved.map(), "escape"), Optional.empty(), "another key leaves it");
        assertEquals(moved.problems(), List.of());
    }

    /** A key written two ways is one key, and the later change wins. */
    @Test
    public void theLaterOfTwoChangesToOneKeyWins() {
        KeyMap.WithChanges changed = KeyMap.defaultMap().withChanges(changes("esc", "UNDO", "escape", "REDO"));
        assertEquals(commandOn(changed.map(), "escape"), Optional.of(Command.REDO));
    }
}
