package com.sh0sh1n.watavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ShortcutsTest {

    private final Component source = new JPanel();
    private List<String> ran;
    private List<Boolean> shift;
    private boolean mainWindowFocused;
    private Action save;
    private Shortcuts shortcuts;
    private int refreshed;

    private Action action(final String name) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                ran.add(name);
            }
        };
    }

    /** Each command's action, as a command table gives them. */
    private final Map<Command, Action> actions = new EnumMap<>(Command.class);

    private Function<Command, Action> actions() {
        return actions::get;
    }

    @BeforeMethod
    public void setUp() {
        ran = new ArrayList<>();
        shift = new ArrayList<>();
        refreshed = 0;
        mainWindowFocused = true;
        save = action("save");
        actions.clear();
        actions.put(Command.SAVE, save);
        actions.put(Command.NEW_CELL_LEFT, action("left"));
        actions.put(Command.PLAY_PAUSE, action("play"));
        actions.put(Command.JUMP_BACK, action("back"));
        actions.put(Command.JUMP_FORWARD, action("forward"));
        actions.put(Command.TEMPORAL_LAYOUT, action("ordering"));
        shortcuts = Shortcuts.builder(actions())
            .menu("File", Command.SAVE, KeyStroke.getKeyStroke(KeyEvent.VK_S, Shortcuts.COMMAND))
            .global("Spreadsheet", Command.NEW_CELL_LEFT, KeyStroke.getKeyStroke(KeyEvent.VK_L, Shortcuts.COMMAND))
            .keypad(Command.PLAY_PAUSE, KeyEvent.VK_NUMPAD8, 0)
            .keypad(Command.JUMP_BACK, KeyEvent.VK_SUBTRACT, 0)
            .keypad(Command.JUMP_FORWARD, KeyEvent.VK_SUBTRACT, InputEvent.SHIFT_DOWN_MASK)
            .build(() -> mainWindowFocused, shift::add, () -> refreshed++);
    }

    private KeyEvent press(final int keyCode, final int modifiers, final int location) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED, location);
    }

    private KeyEvent type(final char c, final int modifiers) {
        return new KeyEvent(source, KeyEvent.KEY_TYPED, 0, modifiers, KeyEvent.VK_UNDEFINED, c,
            KeyEvent.KEY_LOCATION_UNKNOWN);
    }

    private KeyEvent release(final int keyCode, final int location) {
        return new KeyEvent(source, KeyEvent.KEY_RELEASED, 0, 0, keyCode, KeyEvent.CHAR_UNDEFINED, location);
    }

    private static final int STANDARD = KeyEvent.KEY_LOCATION_STANDARD;
    private static final int KEYPAD = KeyEvent.KEY_LOCATION_NUMPAD;

    @Test
    public void inTheMainWindowTheMenuBarRunsMenuShortcuts() {
        assertFalse(shortcuts.dispatchKeyEvent(press(KeyEvent.VK_S, Shortcuts.COMMAND, STANDARD)));
        assertTrue(ran.isEmpty());
    }

    @Test
    public void inOtherWindowsTheDispatcherRunsMenuShortcuts() {
        mainWindowFocused = false;
        assertTrue(shortcuts.dispatchKeyEvent(press(KeyEvent.VK_S, Shortcuts.COMMAND, STANDARD)));
        assertEquals(ran, List.of("save"));
    }

    @Test
    public void aCheckBoxMenuShortcutFlipsItsStateFromOtherWindows() {
        Action ordering = actions.get(Command.TEMPORAL_LAYOUT);
        ordering.putValue(Action.SELECTED_KEY, false);
        mainWindowFocused = false;
        Shortcuts withCheckBox = Shortcuts.builder(actions())
            .menu("Spreadsheet", Command.TEMPORAL_LAYOUT, KeyStroke.getKeyStroke(KeyEvent.VK_T, Shortcuts.COMMAND))
            .build(() -> mainWindowFocused, shift::add, () -> { });

        assertTrue(withCheckBox.dispatchKeyEvent(press(KeyEvent.VK_T, Shortcuts.COMMAND, STANDARD)));
        assertEquals(ordering.getValue(Action.SELECTED_KEY), true);
        assertTrue(withCheckBox.dispatchKeyEvent(press(KeyEvent.VK_T, Shortcuts.COMMAND, STANDARD)));
        assertEquals(ordering.getValue(Action.SELECTED_KEY), false);
        assertEquals(ran, List.of("ordering", "ordering"));
    }

    @Test
    public void aCheckBoxMenuShortcutInTheMainWindowIsLeftToTheMenuBar() {
        Action ordering = actions.get(Command.TEMPORAL_LAYOUT);
        ordering.putValue(Action.SELECTED_KEY, false);
        Shortcuts withCheckBox = Shortcuts.builder(actions())
            .menu("Spreadsheet", Command.TEMPORAL_LAYOUT, KeyStroke.getKeyStroke(KeyEvent.VK_T, Shortcuts.COMMAND))
            .build(() -> mainWindowFocused, shift::add, () -> { });

        assertFalse(withCheckBox.dispatchKeyEvent(press(KeyEvent.VK_T, Shortcuts.COMMAND, STANDARD)));
        assertEquals(ordering.getValue(Action.SELECTED_KEY), false);
    }

    @Test
    public void aDisabledMenuCommandDoesNotRunFromOtherWindows() {
        mainWindowFocused = false;
        save.setEnabled(false);
        assertFalse(shortcuts.dispatchKeyEvent(press(KeyEvent.VK_S, Shortcuts.COMMAND, STANDARD)));
        assertTrue(ran.isEmpty());
    }

    /** The commands' state is worked out before a shortcut runs, so the menu bar too sees whether it can run now. */
    @Test
    public void commandsAreRefreshedBeforeAShortcutButNotForOtherKeys() {
        shortcuts.dispatchKeyEvent(press(KeyEvent.VK_S, Shortcuts.COMMAND, STANDARD));
        shortcuts.dispatchKeyEvent(press(KeyEvent.VK_NUMPAD8, 0, KEYPAD));
        shortcuts.dispatchKeyEvent(press(KeyEvent.VK_A, 0, STANDARD));
        shortcuts.dispatchKeyEvent(press(KeyEvent.VK_LEFT, Shortcuts.COMMAND, STANDARD));

        assertEquals(refreshed, 2);
    }

    /** A global or keypad key whose command cannot run now does nothing, and its key goes no further. */
    @Test
    public void aDisabledGlobalOrKeypadShortcutDoesNothing() {
        actions.get(Command.NEW_CELL_LEFT).setEnabled(false);
        actions.get(Command.PLAY_PAUSE).setEnabled(false);
        mainWindowFocused = false;

        KeyEvent left = press(KeyEvent.VK_L, Shortcuts.COMMAND, STANDARD);
        KeyEvent play = press(KeyEvent.VK_NUMPAD8, 0, KEYPAD);
        assertTrue(shortcuts.dispatchKeyEvent(left));
        assertTrue(shortcuts.dispatchKeyEvent(play));

        assertTrue(left.isConsumed());
        assertTrue(play.isConsumed());
        assertTrue(ran.isEmpty(), ran.toString());
    }

    /** A shortcut names its command, as a key mapping will, and is listed by the command's label. */
    @Test
    public void aShortcutNamesItsCommand() {
        Shortcuts.Shortcut play = shortcuts.all().stream()
            .filter(shortcut -> shortcut.getCommand() == Command.PLAY_PAUSE).findFirst().orElseThrow();

        assertEquals(play.getName(), "Play or pause");
        assertEquals(play.getKeyText(), "Keypad 8");
    }

    @Test
    public void globalShortcutsRunInEveryWindow() {
        assertTrue(shortcuts.dispatchKeyEvent(press(KeyEvent.VK_L, Shortcuts.COMMAND, STANDARD)));
        mainWindowFocused = false;
        assertTrue(shortcuts.dispatchKeyEvent(press(KeyEvent.VK_L, Shortcuts.COMMAND, STANDARD)));
        assertEquals(ran, List.of("left", "left"));
    }

    @Test
    public void commandCharactersNeverReachTheMainWindow() {
        assertTrue(shortcuts.dispatchKeyEvent(type('s', Shortcuts.COMMAND)));
        assertFalse(shortcuts.dispatchKeyEvent(type('s', 0)));
    }

    @Test
    public void keypadKeysRunTheirShortcutAndTypeNothing() {
        assertTrue(shortcuts.dispatchKeyEvent(press(KeyEvent.VK_NUMPAD8, 0, KEYPAD)));
        assertTrue(shortcuts.dispatchKeyEvent(type('8', 0)));
        assertFalse(shortcuts.dispatchKeyEvent(release(KeyEvent.VK_NUMPAD8, KEYPAD)));
        assertFalse(shortcuts.dispatchKeyEvent(type('8', 0)), "typing works again after the key is released");
        assertEquals(ran, List.of("play"));
    }

    @Test
    public void anUnassignedKeypadKeyStillTypesNothing() {
        assertFalse(shortcuts.dispatchKeyEvent(press(KeyEvent.VK_EQUALS, 0, KEYPAD)));
        assertTrue(shortcuts.dispatchKeyEvent(type('=', 0)));
        assertTrue(ran.isEmpty());
    }

    @Test
    public void shiftChoosesTheShiftedKeypadShortcut() {
        shortcuts.dispatchKeyEvent(press(KeyEvent.VK_SUBTRACT, InputEvent.SHIFT_DOWN_MASK, KEYPAD));
        shortcuts.dispatchKeyEvent(press(KeyEvent.VK_SUBTRACT, 0, KEYPAD));
        assertEquals(ran, List.of("forward", "back"));
    }

    @Test
    public void aKeypadKeyWithOtherModifiersRunsItsPlainShortcut() {
        assertTrue(shortcuts.dispatchKeyEvent(press(KeyEvent.VK_NUMPAD8, InputEvent.ALT_DOWN_MASK, KEYPAD)));
        assertEquals(ran, List.of("play"));
    }

    @Test
    public void keypadShortcutsIgnoreTheMainKeyboard() {
        assertFalse(shortcuts.dispatchKeyEvent(press(KeyEvent.VK_SUBTRACT, 0, STANDARD)));
        assertTrue(ran.isEmpty());
    }

    @Test
    public void shiftIsReported() {
        shortcuts.dispatchKeyEvent(press(KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK, KeyEvent.KEY_LOCATION_LEFT));
        shortcuts.dispatchKeyEvent(release(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_LEFT));
        assertEquals(shift, List.of(true, false));
    }

    /** macOS text fields delete with these keys; they are kept free for the key mapping (F7). */
    @Test
    public void reservedKeysReachNoComponentAndTypeNothing() {
        int[][] keys = {
            {KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK, '\b'},
            {KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK, 0x04},
            {KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK, 0x17},
            {KeyEvent.VK_BACK_SPACE, InputEvent.ALT_DOWN_MASK, '\b'},
            {KeyEvent.VK_DELETE, InputEvent.ALT_DOWN_MASK, 0x7F},
        };
        for (int[] key : keys) {
            for (boolean focused : new boolean[] {true, false}) {
                mainWindowFocused = focused;
                KeyEvent pressed = press(key[0], key[1], STANDARD);
                assertTrue(shortcuts.dispatchKeyEvent(pressed), KeyEvent.getKeyText(key[0]));
                assertTrue(pressed.isConsumed(), KeyEvent.getKeyText(key[0]));
                assertTrue(shortcuts.dispatchKeyEvent(type((char) key[2], key[1])), KeyEvent.getKeyText(key[0]));
                assertFalse(shortcuts.dispatchKeyEvent(release(key[0], STANDARD)));
            }
        }
        assertEquals(Shortcuts.RESERVED.size(), keys.length);
        assertTrue(ran.isEmpty());
    }

    @Test
    public void theSameKeysWithoutTheirModifierAreLeftToTheComponent() {
        assertFalse(shortcuts.dispatchKeyEvent(press(KeyEvent.VK_H, 0, STANDARD)));
        assertFalse(shortcuts.dispatchKeyEvent(type('h', 0)));
        assertFalse(shortcuts.dispatchKeyEvent(press(KeyEvent.VK_BACK_SPACE, 0, STANDARD)));
        assertFalse(shortcuts.dispatchKeyEvent(press(KeyEvent.VK_DELETE, 0, STANDARD)));
        assertFalse(shortcuts.dispatchKeyEvent(press(KeyEvent.VK_BACK_SPACE, InputEvent.SHIFT_DOWN_MASK, STANDARD)));
    }

    @Test
    public void aShortcutOnAReservedKeyRuns() {
        Shortcuts mapped = Shortcuts.builder(actions())
            .global("Video controller", Command.PLAY_PAUSE, KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK))
            .build(() -> mainWindowFocused, shift::add, () -> { });

        assertTrue(mapped.dispatchKeyEvent(press(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK, STANDARD)));
        assertEquals(ran, List.of("play"));
    }

    @Test
    public void twoShortcutsCannotShareAKey() {
        Shortcuts.Builder builder = Shortcuts.builder(actions())
            .menu("File", Command.SAVE, KeyStroke.getKeyStroke(KeyEvent.VK_S, Shortcuts.COMMAND))
            .global("File", Command.SAVE_AS, KeyStroke.getKeyStroke(KeyEvent.VK_S, Shortcuts.COMMAND));
        expectThrows(IllegalStateException.class, () -> builder.build(() -> true, b -> { }, () -> { }));
    }

    @Test
    public void keysAreDescribedTheWayMacMenusShowThem() {
        assertEquals(Shortcuts.describe(KeyStroke.getKeyStroke(KeyEvent.VK_S,
            Shortcuts.COMMAND | InputEvent.SHIFT_DOWN_MASK), false), "⇧⌘S");
        assertEquals(Shortcuts.describe(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD7, 0), true), "Keypad 7");
        assertEquals(Shortcuts.describe(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.SHIFT_DOWN_MASK), true),
            "⇧ Keypad +");
        assertEquals(Shortcuts.describe(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, Shortcuts.COMMAND), false),
            "⌘\\");
    }

    // The key map's layers (AGENTS.md › Decisions, 2026-11-13).

    private boolean commandMode;

    /** A table with the key map's two layers: x and Space in command mode; ⌃J and Esc in every mode. */
    private Shortcuts mapped() {
        actions.put(Command.DELETE_CELLS, action("delete"));
        actions.put(Command.NEXT_CELL, action("next"));
        actions.put(Command.COMMAND_MODE, action("command mode"));
        actions.put(Command.INSERT_MODE, action("insert"));
        actions.put(Command.NEW_CELL_HERE_CLOSING_PREVIOUS, action("O"));
        commandMode = true;
        return Shortcuts.builder(actions())
            .keyMap(KeyMap.of(java.util.Map.of("x", Command.DELETE_CELLS, "space", Command.PLAY_PAUSE,
                "i", Command.INSERT_MODE, "O", Command.NEW_CELL_HERE_CLOSING_PREVIOUS,
                "ctrl+j", Command.NEXT_CELL, "escape", Command.COMMAND_MODE)))
            .commandKeysWhen(c -> commandMode)
            .build(() -> mainWindowFocused, shift::add, () -> refreshed++);
    }

    private KeyEvent pressTyping(final int keyCode, final char c, final int modifiers) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, c, STANDARD);
    }

    @Test
    public void inCommandModeAPlainKeyRunsItsCommandAndTypesNothing() {
        Shortcuts keys = mapped();
        KeyEvent x = pressTyping(KeyEvent.VK_X, 'x', 0);
        assertTrue(keys.dispatchKeyEvent(x));
        assertTrue(x.isConsumed());
        assertTrue(keys.dispatchKeyEvent(type('x', 0)), "its character is dropped");
        assertTrue(keys.dispatchKeyEvent(pressTyping(KeyEvent.VK_SPACE, ' ', 0)));
        assertEquals(ran, List.of("delete", "play"));
        assertEquals(refreshed, 2, "the commands' state is worked out first");
    }

    /** Command keys are matched by the character typed, so Shift is part of the key, and they follow the layout. */
    @Test
    public void aCommandKeyIsTheCharacterTyped() {
        Shortcuts keys = mapped();
        assertTrue(keys.dispatchKeyEvent(pressTyping(KeyEvent.VK_O, 'O', InputEvent.SHIFT_DOWN_MASK)));
        assertFalse(keys.dispatchKeyEvent(pressTyping(KeyEvent.VK_O, 'o', 0)), "o is not mapped here");
        assertFalse(keys.dispatchKeyEvent(type('o', 0)));
        assertEquals(ran, List.of("O"));
    }

    @Test
    public void outsideCommandModePlainKeysType() {
        Shortcuts keys = mapped();
        commandMode = false;
        assertFalse(keys.dispatchKeyEvent(pressTyping(KeyEvent.VK_X, 'x', 0)));
        assertFalse(keys.dispatchKeyEvent(type('x', 0)));
        assertTrue(ran.isEmpty());
    }

    /** A command key held with ⌃, ⌥ or ⌘ is another key, and the keypad's keys are the keypad's. */
    @Test
    public void commandKeysAreTypedWithNoModifierButShift() {
        Shortcuts keys = mapped();
        assertFalse(keys.dispatchKeyEvent(pressTyping(KeyEvent.VK_X, 'x', InputEvent.ALT_DOWN_MASK)));
        assertFalse(keys.dispatchKeyEvent(pressTyping(KeyEvent.VK_X, 'x', Shortcuts.COMMAND)));
        assertFalse(keys.dispatchKeyEvent(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_SPACE, ' ',
            KEYPAD)));
        assertTrue(ran.isEmpty());
    }

    /** In command mode a command key is a command, though it cannot run now: it does not type. */
    @Test
    public void aDisabledCommandKeyDoesNothing() {
        Shortcuts keys = mapped();
        actions.get(Command.DELETE_CELLS).setEnabled(false);
        assertTrue(keys.dispatchKeyEvent(pressTyping(KeyEvent.VK_X, 'x', 0)));
        assertTrue(keys.dispatchKeyEvent(type('x', 0)));
        assertTrue(ran.isEmpty());
    }

    /**
     * A held i goes into a cell once and then types, as in vim: its repeats press again in insert mode, where they are
     * not commands, and their characters type.
     */
    @Test
    public void aHeldCommandKeyRepeatsOnlyWhileItIsACommand() {
        Shortcuts keys = mapped();
        assertTrue(keys.dispatchKeyEvent(pressTyping(KeyEvent.VK_I, 'i', 0)));
        assertTrue(keys.dispatchKeyEvent(type('i', 0)));
        commandMode = false;
        assertFalse(keys.dispatchKeyEvent(pressTyping(KeyEvent.VK_I, 'i', 0)), "a repeat in insert mode");
        assertFalse(keys.dispatchKeyEvent(type('i', 0)), "types");
        assertEquals(ran, List.of("insert"));
    }

    @Test
    public void chordsRunInEveryModeAndWindow() {
        Shortcuts keys = mapped();
        commandMode = false;
        assertTrue(keys.dispatchKeyEvent(pressTyping(KeyEvent.VK_J, '\n', InputEvent.CTRL_DOWN_MASK)));
        assertTrue(keys.dispatchKeyEvent(type('\n', InputEvent.CTRL_DOWN_MASK)), "its character is dropped");
        mainWindowFocused = false;
        assertTrue(keys.dispatchKeyEvent(pressTyping(KeyEvent.VK_ESCAPE, '\u001b', 0)));
        assertEquals(ran, List.of("next", "command mode"));
    }

    /** A chord that cannot run now goes on to the component in focus, so Esc still closes a dialog. */
    @Test
    public void aDisabledChordGoesOn() {
        Shortcuts keys = mapped();
        actions.get(Command.COMMAND_MODE).setEnabled(false);
        assertFalse(keys.dispatchKeyEvent(pressTyping(KeyEvent.VK_ESCAPE, '\u001b', 0)));
        assertFalse(keys.dispatchKeyEvent(type('\u001b', 0)));
        assertTrue(ran.isEmpty());
    }

    /** A chord is told apart by its modifiers: Esc with Shift is not Esc. */
    @Test
    public void aChordWithOtherModifiersIsAnotherKey() {
        Shortcuts keys = mapped();
        assertFalse(keys.dispatchKeyEvent(pressTyping(KeyEvent.VK_ESCAPE, '\u001b', InputEvent.SHIFT_DOWN_MASK)));
        assertFalse(keys.dispatchKeyEvent(pressTyping(KeyEvent.VK_J, 'j', 0)), "j alone types");
        assertTrue(ran.isEmpty());
    }

    /** A reserved key whose chord cannot run now is still dropped. */
    @Test
    public void aDisabledChordOnAReservedKeyIsDropped() {
        actions.put(Command.COLUMN_LEFT, action("left column"));
        actions.get(Command.COLUMN_LEFT).setEnabled(false);
        Shortcuts keys = Shortcuts.builder(actions())
            .keyMap(KeyMap.of(java.util.Map.of("ctrl+h", Command.COLUMN_LEFT)))
            .build(() -> mainWindowFocused, shift::add, () -> { });
        assertTrue(keys.dispatchKeyEvent(pressTyping(KeyEvent.VK_H, '\b', InputEvent.CTRL_DOWN_MASK)));
        assertTrue(ran.isEmpty());
    }

    @Test
    public void aChordCannotShareAKeyWithAFixedShortcut() {
        Shortcuts.Builder builder = Shortcuts.builder(actions())
            .global("Spreadsheet", Command.NEW_CELL_LEFT, KeyStroke.getKeyStroke(KeyEvent.VK_J,
                InputEvent.CTRL_DOWN_MASK))
            .keyMap(KeyMap.of(java.util.Map.of("ctrl+j", Command.NEXT_CELL)));
        expectThrows(IllegalStateException.class, () -> builder.build(() -> true, b -> { }, () -> { }));
    }

    @Test
    public void theKeyMapIsListedByLayer() {
        Shortcuts.Shortcut x = mapped().all().stream()
            .filter(shortcut -> shortcut.getCommand() == Command.DELETE_CELLS).findFirst().orElseThrow();
        Shortcuts.Shortcut esc = mapped().all().stream()
            .filter(shortcut -> shortcut.getCommand() == Command.COMMAND_MODE).findFirst().orElseThrow();
        assertEquals(x.getGroup(), "Command mode");
        assertEquals(x.getKeyText(), "x");
        assertEquals(x.getScope(), Shortcuts.Scope.COMMAND);
        assertEquals(esc.getGroup(), "Every mode");
        assertEquals(esc.getKeyText(), "Esc");
        assertEquals(esc.getScope(), Shortcuts.Scope.CHORD);
    }
}
