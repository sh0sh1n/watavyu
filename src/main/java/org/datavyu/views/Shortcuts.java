package org.datavyu.views;

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

/**
 * The application's keyboard shortcuts, in one table, and the dispatcher that runs them. Menu items take their
 * accelerators from this table, and the Keyboard Shortcuts dialog lists it.
 */
public final class Shortcuts implements KeyEventDispatcher {

    /** Command key, the menu shortcut modifier on macOS. */
    public static final int COMMAND = InputEvent.META_DOWN_MASK;

    private static final int MODIFIERS = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
            | InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK;

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
        /** Handled by a component, such as the spreadsheet or the code editor. Listed for reference only. */
        REFERENCE
    }

    /** One shortcut. */
    public static final class Shortcut {
        private final String group;
        private final String name;
        private final KeyStroke key;
        private final String keyText;
        private final Scope scope;
        private final Action action;

        private Shortcut(final String group, final String name, final KeyStroke key, final String keyText,
                         final Scope scope, final Action action) {
            this.group = group;
            this.name = name;
            this.key = key;
            this.keyText = keyText;
            this.scope = scope;
            this.action = action;
        }

        public String getGroup() {
            return group;
        }

        public String getName() {
            return name;
        }

        /** @return The key, or null for a {@link Scope#REFERENCE} shortcut. */
        public KeyStroke getKey() {
            return key;
        }

        public Scope getScope() {
            return scope;
        }

        /** @return The command the key runs, or null for a {@link Scope#REFERENCE} shortcut. */
        public Action getAction() {
            return action;
        }

        /** @return The key as shown to the user, such as ⇧⌘S or Keypad 7. */
        public String getKeyText() {
            return keyText;
        }
    }

    /** Collects shortcuts in the order they are listed to the user. */
    public static final class Builder {
        private final List<Shortcut> shortcuts = new ArrayList<>();

        public Builder menu(final String group, final String name, final KeyStroke key, final Action action) {
            return add(new Shortcut(group, name, key, describe(key, false), Scope.MENU, action));
        }

        public Builder global(final String group, final String name, final KeyStroke key, final Action action) {
            return add(new Shortcut(group, name, key, describe(key, false), Scope.GLOBAL, action));
        }

        public Builder keypad(final String name, final int keyCode, final int modifiers, final Action action) {
            KeyStroke key = KeyStroke.getKeyStroke(keyCode, modifiers);
            return add(new Shortcut("Video controller", name, key, describe(key, true), Scope.KEYPAD, action));
        }

        public Builder reference(final String group, final String name, final String keyText) {
            return add(new Shortcut(group, name, null, keyText, Scope.REFERENCE, null));
        }

        private Builder add(final Shortcut shortcut) {
            shortcuts.add(shortcut);
            return this;
        }

        /**
         * @param mainWindowFocused Whether the main window has keyboard focus, so its menu bar handles accelerators.
         * @param shiftHeld         Told whenever the Shift key goes down or up.
         * @return The shortcuts.
         * @throws IllegalStateException If two shortcuts use the same key.
         */
        public Shortcuts build(final BooleanSupplier mainWindowFocused, final Consumer<Boolean> shiftHeld) {
            Set<String> keys = new HashSet<>();
            for (Shortcut shortcut : shortcuts) {
                if (shortcut.key != null && !keys.add((shortcut.scope == Scope.KEYPAD ? "keypad " : "") + shortcut.key)) {
                    throw new IllegalStateException(shortcut.keyText + " is used by more than one shortcut");
                }
            }
            return new Shortcuts(new ArrayList<>(shortcuts), mainWindowFocused, shiftHeld);
        }
    }

    private final List<Shortcut> shortcuts;
    private final BooleanSupplier mainWindowFocused;
    private final Consumer<Boolean> shiftHeld;

    /** True from the press of any keypad key until its release; the characters it types are dropped. */
    private boolean keypadKeyDown;

    /** Key code of a key whose press this dispatcher handled, until it is released; its characters are dropped. */
    private int handledKeyCode = KeyEvent.VK_UNDEFINED;

    private Shortcuts(final List<Shortcut> shortcuts, final BooleanSupplier mainWindowFocused,
                      final Consumer<Boolean> shiftHeld) {
        this.shortcuts = shortcuts;
        this.mainWindowFocused = mainWindowFocused;
        this.shiftHeld = shiftHeld;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** @return All shortcuts, in the order they are listed to the user. */
    public List<Shortcut> all() {
        return Collections.unmodifiableList(shortcuts);
    }

    @Override
    public boolean dispatchKeyEvent(final KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT && e.getID() != KeyEvent.KEY_TYPED) {
            shiftHeld.accept(e.getID() == KeyEvent.KEY_PRESSED);
        }
        switch (e.getID()) {
            case KeyEvent.KEY_PRESSED:
                return keyPressed(e);
            case KeyEvent.KEY_RELEASED:
                if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD) {
                    keypadKeyDown = false;
                }
                if (e.getKeyCode() == handledKeyCode) {
                    handledKeyCode = KeyEvent.VK_UNDEFINED;
                }
                return false;
            case KeyEvent.KEY_TYPED:
                return keyTyped(e);
            default:
                return false;
        }
    }

    private boolean keyPressed(final KeyEvent e) {
        boolean keypad = e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD;
        if (keypad) {
            keypadKeyDown = true;
        }
        Shortcut shortcut = find(e.getKeyCode(), e.getModifiersEx() & MODIFIERS, keypad);
        if (shortcut == null || shortcut.scope == Scope.REFERENCE) {
            return false;
        }
        if (shortcut.scope == Scope.MENU) {
            if (mainWindowFocused.getAsBoolean() || !shortcut.action.isEnabled()) {
                return false;
            }
            Object selected = shortcut.action.getValue(Action.SELECTED_KEY);
            if (selected instanceof Boolean) {
                shortcut.action.putValue(Action.SELECTED_KEY, !(Boolean) selected);
            }
        }
        shortcut.action.actionPerformed(new ActionEvent(e.getSource(), ActionEvent.ACTION_PERFORMED, shortcut.name));
        handledKeyCode = e.getKeyCode();
        e.consume();
        return true;
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
            if (shortcut.key == null || (shortcut.scope == Scope.KEYPAD) != keypad) {
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
     * @param key    A key with modifiers.
     * @param keypad Whether the key is on the numeric keypad.
     * @return The key as macOS menus show it, such as ⇧⌘S, or Keypad 7.
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
        switch (keyCode) {
            case KeyEvent.VK_DIVIDE:
                return "/";
            case KeyEvent.VK_MULTIPLY:
                return "*";
            case KeyEvent.VK_SUBTRACT:
            case KeyEvent.VK_MINUS:
                return "-";
            case KeyEvent.VK_ADD:
                return "+";
            case KeyEvent.VK_DECIMAL:
                return ".";
            case KeyEvent.VK_ENTER:
                return "Enter";
            case KeyEvent.VK_EQUALS:
                return "=";
            case KeyEvent.VK_BACK_SLASH:
                return "\\";
            case KeyEvent.VK_LEFT:
                return "←";
            case KeyEvent.VK_RIGHT:
                return "→";
            default:
                return KeyEvent.getKeyText(keyCode);
        }
    }
}
