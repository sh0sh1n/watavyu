package org.datavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
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

    private Action action(final String name) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                ran.add(name);
            }
        };
    }

    @BeforeMethod
    public void setUp() {
        ran = new ArrayList<>();
        shift = new ArrayList<>();
        mainWindowFocused = true;
        save = action("save");
        shortcuts = Shortcuts.builder()
            .menu("File", "Save", KeyStroke.getKeyStroke(KeyEvent.VK_S, Shortcuts.COMMAND), save)
            .global("Spreadsheet", "New cell to left", KeyStroke.getKeyStroke(KeyEvent.VK_L, Shortcuts.COMMAND),
                action("left"))
            .keypad("Play", KeyEvent.VK_NUMPAD8, 0, action("play"))
            .keypad("Jump back", KeyEvent.VK_SUBTRACT, 0, action("back"))
            .keypad("Jump forward", KeyEvent.VK_SUBTRACT, InputEvent.SHIFT_DOWN_MASK, action("forward"))
            .reference("Spreadsheet", "Select column to left", "⌘←")
            .build(() -> mainWindowFocused, shift::add);
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
        Action ordering = action("ordering");
        ordering.putValue(Action.SELECTED_KEY, false);
        mainWindowFocused = false;
        Shortcuts withCheckBox = Shortcuts.builder()
            .menu("Spreadsheet", "Weak temporal ordering", KeyStroke.getKeyStroke(KeyEvent.VK_T, Shortcuts.COMMAND),
                ordering)
            .build(() -> mainWindowFocused, shift::add);

        assertTrue(withCheckBox.dispatchKeyEvent(press(KeyEvent.VK_T, Shortcuts.COMMAND, STANDARD)));
        assertEquals(ordering.getValue(Action.SELECTED_KEY), true);
        assertTrue(withCheckBox.dispatchKeyEvent(press(KeyEvent.VK_T, Shortcuts.COMMAND, STANDARD)));
        assertEquals(ordering.getValue(Action.SELECTED_KEY), false);
        assertEquals(ran, List.of("ordering", "ordering"));
    }

    @Test
    public void aCheckBoxMenuShortcutInTheMainWindowIsLeftToTheMenuBar() {
        Action ordering = action("ordering");
        ordering.putValue(Action.SELECTED_KEY, false);
        Shortcuts withCheckBox = Shortcuts.builder()
            .menu("Spreadsheet", "Weak temporal ordering", KeyStroke.getKeyStroke(KeyEvent.VK_T, Shortcuts.COMMAND),
                ordering)
            .build(() -> mainWindowFocused, shift::add);

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
    public void referenceShortcutsAreLeftToTheirComponent() {
        assertFalse(shortcuts.dispatchKeyEvent(press(KeyEvent.VK_LEFT, Shortcuts.COMMAND, STANDARD)));
    }

    @Test
    public void shiftIsReported() {
        shortcuts.dispatchKeyEvent(press(KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK, KeyEvent.KEY_LOCATION_LEFT));
        shortcuts.dispatchKeyEvent(release(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_LEFT));
        assertEquals(shift, List.of(true, false));
    }

    @Test
    public void twoShortcutsCannotShareAKey() {
        Shortcuts.Builder builder = Shortcuts.builder()
            .menu("File", "Save", KeyStroke.getKeyStroke(KeyEvent.VK_S, Shortcuts.COMMAND), save)
            .global("File", "Save again", KeyStroke.getKeyStroke(KeyEvent.VK_S, Shortcuts.COMMAND), save);
        expectThrows(IllegalStateException.class, () -> builder.build(() -> true, b -> { }));
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

}
