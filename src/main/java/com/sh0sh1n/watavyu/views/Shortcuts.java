package com.sh0sh1n.watavyu.views;

import javax.swing.Action;
import javax.swing.KeyStroke;
import java.awt.KeyEventDispatcher;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The application's keyboard shortcuts, in one table, and the dispatcher that runs them: the fixed ⌘ keys, the keypad,
 * and the key map's two layers ({@link KeyMap}). Menu items take their accelerators from this table, and the Keyboard
 * Shortcuts dialog lists it.
 */
public final class Shortcuts implements KeyEventDispatcher {

    /** Command key, the menu shortcut modifier on macOS. */
    public static final int COMMAND = InputEvent.META_DOWN_MASK;

    /** The modifiers a key is told apart by. */
    public static final int MODIFIERS = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
            | InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK;

    /**
     * Keys macOS text fields delete with (⌃H, ⌃D, ⌃W, ⌥⌫ and ⌥⌦), which in a cell got past the column lock. Unless a
     * shortcut uses one, as ⌃H goes to the column to the left, this dispatcher drops it in every window, with the
     * character it types.
     */
    public static final Set<KeyStroke> RESERVED = Set.of(
            KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK),
            KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK),
            KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK),
            KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, InputEvent.ALT_DOWN_MASK),
            KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, InputEvent.ALT_DOWN_MASK));

    /** Where a shortcut is handled. */
    public enum Scope {
        /**
         * A menu item's accelerator. In the main window the menu bar runs it; in other windows, such as the video
         * controller, this dispatcher does, if the menu item is enabled. For a check box item, whose action has an
         * {@link Action#SELECTED_KEY}, the dispatcher flips that state first, as clicking the item would.
         */
        MENU,
        /** Run by this dispatcher in every window, before the focused component sees the key. */
        GLOBAL,
        /** A numeric keypad key, run by this dispatcher in every window. The character the key types is dropped. */
        KEYPAD,
        /**
         * A plain character from the key map's command layer, run by this dispatcher while plain keys are commands
         * (command mode), whether or not its command can run now; otherwise the character types. The key is matched by
         * the character it types, so it follows the keyboard's layout.
         */
        COMMAND,
        /**
         * A chord from the key map (a key held with ⌃, or Esc), run by this dispatcher in every window and mode. A
         * chord whose command cannot run now goes on to the focused component, so Esc still closes a dialog.
         */
        CHORD
    }

    /** One shortcut. */
    public static final class Shortcut {
        private final String group;
        private final Command command;
        private final KeyStroke key;
        private final String keyText;
        private final Scope scope;
        private final Action action;

        private Shortcut(final String group, final Command command, final KeyStroke key, final String keyText,
                         final Scope scope, final Action action) {
            this.group = group;
            this.command = command;
            this.key = key;
            this.keyText = keyText;
            this.scope = scope;
            this.action = action;
        }

        /** Returns the command the key runs, by its name. */
        public Command getCommand() {
            return command;
        }

        public String getGroup() {
            return group;
        }

        public String getName() {
            return command.label();
        }

        /** Returns the key. */
        public KeyStroke getKey() {
            return key;
        }

        public Scope getScope() {
            return scope;
        }

        /** Returns the key as shown to the user, such as \u21e7\u2318S or Keypad 7. */
        public String getKeyText() {
            return keyText;
        }
    }

    /** Collects shortcuts in the order they are listed to the user. */
    public static final class Builder {
        private final List<Shortcut> shortcuts = new ArrayList<>();
        private final Function<Command, Action> actions;
        private Predicate<Character> commandKeys = c -> false;

        private Builder(final Function<Command, Action> actions) {
            this.actions = actions;
        }

        public Builder menu(final String group, final Command command, final KeyStroke key) {
            return add(new Shortcut(group, command, key, describe(key, false), Scope.MENU,
                actions.apply(command)));
        }

        public Builder global(final String group, final Command command, final KeyStroke key) {
            return add(new Shortcut(group, command, key, describe(key, false), Scope.GLOBAL,
                actions.apply(command)));
        }

        public Builder keypad(final Command command, final int keyCode, final int modifiers) {
            KeyStroke key = KeyStroke.getKeyStroke(keyCode, modifiers);
            return add(new Shortcut("Video controller", command, key, describe(key, true),
                Scope.KEYPAD, actions.apply(command)));
        }

        /**
         * Adds the key map's keys, after the shortcuts added so far.
         *
         * @param keyMap the keys
         * @return this builder
         */
        public Builder keyMap(final KeyMap keyMap) {
            for (KeyMap.Binding binding : keyMap.bindings()) {
                boolean command = binding.layer() == KeyMap.Layer.COMMAND;
                add(new Shortcut(command ? "Command mode" : "Every mode", binding.command(), binding.stroke(),
                    KeyMap.describe(binding), command ? Scope.COMMAND : Scope.CHORD,
                    actions.apply(binding.command())));
            }
            return this;
        }

        /**
         * Says when the command layer's keys are commands.
         *
         * @param commandKeys whether a plain character is a command now, as in command mode; by default never
         * @return this builder
         */
        public Builder commandKeysWhen(final Predicate<Character> commandKeys) {
            this.commandKeys = commandKeys;
            return this;
        }

        private Builder add(final Shortcut shortcut) {
            shortcuts.add(shortcut);
            return this;
        }

        /**
         * Returns the shortcuts, with what they need to know of the app.
         *
         * @param mainWindowFocused Whether the main window has keyboard focus, so its menu bar handles accelerators.
         * @param shiftHeld         Told whenever the Shift key goes down or up.
         * @param beforeShortcut    Run when a key is a shortcut, before it runs, as the menu bar too sees whether its
         *                          command can run now
         * @return The shortcuts.
         * @throws IllegalStateException If two shortcuts use the same key.
         */
        public Shortcuts build(final BooleanSupplier mainWindowFocused, final Consumer<Boolean> shiftHeld,
                               final Runnable beforeShortcut) {
            Set<String> keys = new HashSet<>();
            for (Shortcut shortcut : shortcuts) {
                if (!keys.add((shortcut.scope == Scope.KEYPAD ? "keypad " : "") + shortcut.key)) {
                    throw new IllegalStateException(shortcut.keyText + " is used by more than one shortcut");
                }
            }
            return new Shortcuts(new ArrayList<>(shortcuts), mainWindowFocused, shiftHeld, beforeShortcut,
                commandKeys);
        }
    }

    private final List<Shortcut> shortcuts;
    private final BooleanSupplier mainWindowFocused;
    private final Consumer<Boolean> shiftHeld;
    private final Runnable beforeShortcut;
    private final Predicate<Character> commandKeys;

    /** True from the press of any keypad key until its release; the characters it types are dropped. */
    private boolean keypadKeyDown;

    /**
     * Key code of a key whose press this dispatcher handled, until it is released or another key is pressed; its
     * characters are dropped. A held key presses again as it repeats, so a held {@code i} goes into a cell once and
     * then types, as in vim: the repeats are not commands in insert mode.
     */
    private int handledKeyCode = KeyEvent.VK_UNDEFINED;

    private Shortcuts(final List<Shortcut> shortcuts, final BooleanSupplier mainWindowFocused,
                      final Consumer<Boolean> shiftHeld, final Runnable beforeShortcut,
                      final Predicate<Character> commandKeys) {
        this.shortcuts = shortcuts;
        this.commandKeys = commandKeys;
        this.mainWindowFocused = mainWindowFocused;
        this.shiftHeld = shiftHeld;
        this.beforeShortcut = beforeShortcut;
    }

    /**
     * Starts a table of shortcuts.
     *
     * @param actions each command's action, which its shortcut runs
     * @return the builder
     */
    public static Builder builder(final Function<Command, Action> actions) {
        return new Builder(actions);
    }

    /** Returns all shortcuts, in the order they are listed to the user. */
    public List<Shortcut> all() {
        return Collections.unmodifiableList(shortcuts);
    }

    @Override
    public boolean dispatchKeyEvent(final KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT && e.getID() != KeyEvent.KEY_TYPED) {
            shiftHeld.accept(e.getID() == KeyEvent.KEY_PRESSED);
        }
        return switch (e.getID()) {
            case KeyEvent.KEY_PRESSED -> keyPressed(e);
            case KeyEvent.KEY_RELEASED -> keyReleased(e);
            case KeyEvent.KEY_TYPED -> keyTyped(e);
            default -> false;
        };
    }

    private boolean keyReleased(final KeyEvent e) {
        if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD) {
            keypadKeyDown = false;
        }
        if (e.getKeyCode() == handledKeyCode) {
            handledKeyCode = KeyEvent.VK_UNDEFINED;
        }
        return false;
    }

    private boolean keyPressed(final KeyEvent e) {
        boolean handled = handlePress(e);
        handledKeyCode = handled ? e.getKeyCode() : KeyEvent.VK_UNDEFINED;
        if (handled) {
            e.consume();
        }
        return handled;
    }

    /** Runs the shortcut a key press is, if any; returns whether the press is taken. */
    private boolean handlePress(final KeyEvent e) {
        boolean keypad = e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD;
        if (keypad) {
            keypadKeyDown = true;
        }
        int modifiers = e.getModifiersEx() & MODIFIERS;
        // The commands' state is worked out once a press, though a chord that cannot run goes on to another shortcut.
        boolean refreshed = false;
        if (!keypad) {
            Shortcut chord = find(Scope.CHORD, KeyStroke.getKeyStroke(e.getKeyCode(), modifiers));
            if (chord != null) {
                beforeShortcut.run();
                refreshed = true;
                if (chord.action.isEnabled()) {
                    run(chord, e);
                    return true;
                }
            }
            Shortcut command = commandKey(e, modifiers);
            if (command != null) {
                if (!refreshed) {
                    beforeShortcut.run();
                }
                if (command.action.isEnabled()) {
                    run(command, e);
                }
                return true;
            }
        }
        Shortcut shortcut = find(e.getKeyCode(), modifiers, keypad);
        if (shortcut == null) {
            return !keypad && RESERVED.contains(KeyStroke.getKeyStroke(e.getKeyCode(), modifiers));
        }
        if (!refreshed) {
            beforeShortcut.run();
        }
        boolean menu = shortcut.scope == Scope.MENU;
        if (menu && mainWindowFocused.getAsBoolean()) {
            return false;
        }
        if (!shortcut.action.isEnabled()) {
            // A menu key whose item is greyed goes on to the focused component, as in the main window; a global or
            // keypad key does nothing.
            return !menu;
        }
        if (menu) {
            Object selected = shortcut.action.getValue(Action.SELECTED_KEY);
            if (selected instanceof Boolean b) {
                shortcut.action.putValue(Action.SELECTED_KEY, !b);
            }
        }
        run(shortcut, e);
        return true;
    }

    private static void run(final Shortcut shortcut, final KeyEvent e) {
        shortcut.action.actionPerformed(new ActionEvent(e.getSource(), ActionEvent.ACTION_PERFORMED, shortcut.getName()));
    }

    /**
     * Returns the command-layer key a press types, if plain keys are commands now: a printable character, typed with
     * no modifier but Shift.
     */
    private Shortcut commandKey(final KeyEvent e, final int modifiers) {
        char c = e.getKeyChar();
        if ((modifiers & ~InputEvent.SHIFT_DOWN_MASK) != 0 || c == KeyEvent.CHAR_UNDEFINED || c < ' '
                || c == KeyEvent.VK_DELETE) {
            return null;
        }
        Shortcut shortcut = find(Scope.COMMAND, KeyStroke.getKeyStroke(c));
        return shortcut != null && commandKeys.test(c) ? shortcut : null;
    }

    /** Returns the shortcut of a scope on a key, or null. */
    private Shortcut find(final Scope scope, final KeyStroke key) {
        for (Shortcut shortcut : shortcuts) {
            if (shortcut.scope == scope && shortcut.key.equals(key)) {
                return shortcut;
            }
        }
        return null;
    }

    private boolean keyTyped(final KeyEvent e) {
        if (keypadKeyDown || handledKeyCode != KeyEvent.VK_UNDEFINED) {
            e.consume();
            return true;
        }
        // A Command-key character must not reach the focused component: the menu bar acts on the key press.
        if ((e.getModifiersEx() & MODIFIERS) == COMMAND && mainWindowFocused.getAsBoolean()) {
            e.consume();
            return true;
        }
        return false;
    }

    /**
     * Finds the shortcut for a key press. A keypad key held with modifiers it has no shortcut for runs its
     * unmodified shortcut.
     */
    private Shortcut find(final int keyCode, final int modifiers, final boolean keypad) {
        KeyStroke pressed = KeyStroke.getKeyStroke(keyCode, modifiers);
        Shortcut fallback = null;
        KeyStroke unmodified = KeyStroke.getKeyStroke(keyCode, 0);
        for (Shortcut shortcut : shortcuts) {
            boolean wanted = keypad ? shortcut.scope == Scope.KEYPAD
                    : shortcut.scope == Scope.MENU || shortcut.scope == Scope.GLOBAL;
            if (!wanted) {
                continue;
            }
            if (shortcut.key.equals(pressed)) {
                return shortcut;
            }
            if (keypad && shortcut.key.equals(unmodified)) {
                fallback = shortcut;
            }
        }
        return fallback;
    }

    /**
     * Returns the key as macOS menus show it, such as \u21e7\u2318S, or Keypad 7.
     * @param key    A key with modifiers.
     * @param keypad Whether the key is on the numeric keypad.
     */
    static String describe(final KeyStroke key, final boolean keypad) {
        StringBuilder text = new StringBuilder();
        int modifiers = key.getModifiers();
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
            text.append('⌃');
        }
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
            text.append('⌥');
        }
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
            text.append('⇧');
        }
        if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
            text.append('⌘');
        }
        if (keypad) {
            text.append(text.length() > 0 ? " " : "").append("Keypad ");
        }
        return text.append(keyName(key.getKeyCode())).toString();
    }

    private static String keyName(final int keyCode) {
        if (keyCode >= KeyEvent.VK_NUMPAD0 && keyCode <= KeyEvent.VK_NUMPAD9) {
            return Integer.toString(keyCode - KeyEvent.VK_NUMPAD0);
        }
        return switch (keyCode) {
            case KeyEvent.VK_DIVIDE -> "/";
            case KeyEvent.VK_MULTIPLY -> "*";
            case KeyEvent.VK_SUBTRACT, KeyEvent.VK_MINUS -> "-";
            case KeyEvent.VK_ADD -> "+";
            case KeyEvent.VK_DECIMAL -> ".";
            case KeyEvent.VK_ENTER -> "Enter";
            case KeyEvent.VK_EQUALS -> "=";
            case KeyEvent.VK_BACK_SLASH -> "\\";
            case KeyEvent.VK_LEFT -> "←";
            case KeyEvent.VK_RIGHT -> "→";
            case KeyEvent.VK_ESCAPE -> "Esc";
            case KeyEvent.VK_OPEN_BRACKET -> "[";
            case KeyEvent.VK_CLOSE_BRACKET -> "]";
            case KeyEvent.VK_SPACE -> "Space";
            default -> KeyEvent.getKeyText(keyCode);
        };
    }
}
