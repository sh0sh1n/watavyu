package com.sh0sh1n.watavyu.views;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The keys a user can remap (AGENTS.md › Decisions, 2026-11-13, the key mapping), in two layers. The command layer is
 * plain characters, such as {@code o} or {@code <}, which are commands in command mode and type in insert mode. The
 * chord layer is keys held with ⌃, and Esc, which run in every mode and window. A key is written as it is in Settings:
 * a character, {@code space}, {@code escape}, or a chord such as {@code ctrl+j} or {@code ctrl+]}. The ⌘ menu keys
 * are not here: they stay fixed, as every Mac app shares them.
 */
public final class KeyMap {

    /** Where a mapped key acts. */
    public enum Layer {
        /** A plain character, a command in command mode. */
        COMMAND,
        /** A key held with ⌃, or Esc, run in every mode. */
        CHORD
    }

    /**
     * One mapped key.
     *
     * @param key     the key as written, such as {@code ctrl+j}
     * @param layer   where it acts
     * @param stroke  for the command layer the character typed ({@link KeyStroke#getKeyChar()}); for the chord layer
     *                the key and its modifiers
     * @param command what it runs
     */
    public record Binding(String key, Layer layer, KeyStroke stroke, Command command) {
    }

    /**
     * The keys a chord can name: those a press reports, letters, digits and the unshifted punctuation of a US layout.
     * A shifted character, such as an opening brace, is a key press of another key with Shift.
     */
    private static final Set<Integer> PRESSABLE = pressableKeys();

    private static Set<Integer> pressableKeys() {
        Set<Integer> keys = new HashSet<>(List.of(KeyEvent.VK_ESCAPE, KeyEvent.VK_SPACE, KeyEvent.VK_OPEN_BRACKET,
            KeyEvent.VK_CLOSE_BRACKET, KeyEvent.VK_COMMA, KeyEvent.VK_PERIOD, KeyEvent.VK_SLASH,
            KeyEvent.VK_SEMICOLON, KeyEvent.VK_QUOTE, KeyEvent.VK_BACK_SLASH, KeyEvent.VK_MINUS, KeyEvent.VK_EQUALS,
            KeyEvent.VK_BACK_QUOTE));
        for (int key = KeyEvent.VK_A; key <= KeyEvent.VK_Z; key++) {
            keys.add(key);
        }
        for (int key = KeyEvent.VK_0; key <= KeyEvent.VK_9; key++) {
            keys.add(key);
        }
        return Set.copyOf(keys);
    }

    /** The default map, in the order the Keyboard Shortcuts dialog lists it. */
    private static final Map<String, Command> DEFAULTS = defaultKeys();

    private static Map<String, Command> defaultKeys() {
        Map<String, Command> keys = new LinkedHashMap<>();
        keys.put("space", Command.PLAY_PAUSE);
        keys.put("=", Command.RESET_RATE);
        keys.put(",", Command.JOG_BACK);
        keys.put(".", Command.JOG_FORWARD);
        keys.put("<", Command.SHUTTLE_BACK);
        keys.put(">", Command.SHUTTLE_FORWARD);
        keys.put("b", Command.JUMP_BACK);
        keys.put("w", Command.JUMP_FORWARD);
        keys.put("[", Command.SET_ONSET);
        keys.put("]", Command.SET_OFFSET);
        keys.put("{", Command.GO_TO_ONSET);
        keys.put("}", Command.GO_TO_OFFSET);
        keys.put("o", Command.NEW_CELL_HERE);
        keys.put("O", Command.NEW_CELL_HERE_CLOSING_PREVIOUS);
        keys.put("x", Command.DELETE_CELLS);
        keys.put("u", Command.UNDO);
        keys.put("i", Command.INSERT_MODE);
        keys.put("q", Command.QUICK_MODE);
        keys.put("ctrl+j", Command.NEXT_CELL);
        keys.put("ctrl+k", Command.PREVIOUS_CELL);
        keys.put("ctrl+h", Command.COLUMN_LEFT);
        keys.put("ctrl+l", Command.COLUMN_RIGHT);
        keys.put("ctrl+p", Command.PLAY_PAUSE);
        keys.put("ctrl+r", Command.REDO);
        keys.put("escape", Command.COMMAND_MODE);
        keys.put("ctrl+]", Command.COMMAND_MODE);
        return Collections.unmodifiableMap(keys);
    }

    private final List<Binding> mapped;

    private KeyMap(final List<Binding> mapped) {
        this.mapped = List.copyOf(mapped);
    }

    /** Returns the default map. */
    public static KeyMap defaultMap() {
        return of(DEFAULTS);
    }

    /**
     * Makes a map from keys as written.
     *
     * @param keys each key and the command it runs, in the order they are listed
     * @return the map
     * @throws IllegalArgumentException if a key cannot be read, or two are the same key
     */
    public static KeyMap of(final Map<String, Command> keys) {
        Map<KeyStroke, Binding> bindings = new LinkedHashMap<>();
        for (Map.Entry<String, Command> entry : keys.entrySet()) {
            Binding binding = parse(entry.getKey(), entry.getValue());
            if (bindings.putIfAbsent(binding.stroke(), binding) != null) {
                throw new IllegalArgumentException("\"" + entry.getKey() + "\" is mapped more than once");
            }
        }
        return new KeyMap(new ArrayList<>(bindings.values()));
    }

    /**
     * A key map with the user's changes, and the changes it could not make.
     *
     * @param map      the map in effect
     * @param problems each change left out, and why
     */
    public record WithChanges(KeyMap map, List<String> problems) {
    }

    /**
     * Returns this map with the user's changes (Settings' {@code keys}): each key runs the command named, in any case,
     * in place of what it ran, or with an empty name runs nothing. A change that cannot be read, such as an unknown key
     * or command, is left out, so a mistake in the file never stops the application, and is reported. Some key always
     * leaves insert mode: if the changes leave none, Esc does, as by default.
     *
     * @param changes each key as written and the name of the command it runs, or empty for none
     * @return the map in effect, and the changes left out
     */
    public WithChanges withChanges(final Map<String, String> changes) {
        Map<KeyStroke, Binding> bindings = new LinkedHashMap<>();
        mapped.forEach(binding -> bindings.put(binding.stroke(), binding));
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> change : changes.entrySet()) {
            String name = change.getValue().trim();
            Command command = null;
            if (!name.isEmpty()) {
                try {
                    command = Command.valueOf(name.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    problems.add("\"" + change.getKey() + "\": no command is named " + name);
                    continue;
                }
            }
            try {
                Binding binding = parse(change.getKey(), command);
                if (command == null) {
                    bindings.remove(binding.stroke());
                } else {
                    // A key mapped already keeps its place in the list; a new one goes at the end.
                    bindings.put(binding.stroke(), binding);
                }
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
        if (bindings.values().stream().noneMatch(binding -> binding.command() == Command.COMMAND_MODE)) {
            Binding escape = parse("escape", Command.COMMAND_MODE);
            bindings.put(escape.stroke(), escape);
            problems.add("no key would leave insert mode, so Esc still does");
        }
        return new WithChanges(new KeyMap(new ArrayList<>(bindings.values())), List.copyOf(problems));
    }

    /** Returns the mapped keys, in the order they are listed. */
    public List<Binding> bindings() {
        return mapped;
    }

    /**
     * Returns the first key that runs a command, as shown to the user.
     *
     * @param command the command
     * @return the key, such as {@code o} or ⌃J, or empty if no key runs it
     */
    public Optional<String> keyText(final Command command) {
        return mapped.stream().filter(binding -> binding.command() == command).findFirst().map(KeyMap::describe);
    }

    /**
     * Reads a key as written.
     *
     * @param key     the key, such as {@code o}, {@code space}, {@code escape} or {@code ctrl+]}
     * @param command what it runs
     * @return the binding
     * @throws IllegalArgumentException if the key cannot be read
     */
    static Binding parse(final String key, final Command command) {
        int modifiers = 0;
        String rest = key;
        for (int found = modifier(rest); found != 0; found = modifier(rest)) {
            modifiers |= found;
            rest = rest.substring(rest.indexOf('+') + 1);
        }
        int keyCode = namedKey(rest);
        if (modifiers == 0) {
            if (keyCode == KeyEvent.VK_ESCAPE) {
                return new Binding(key, Layer.CHORD, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), command);
            }
            char c = keyCode == KeyEvent.VK_SPACE ? ' ' : rest.length() == 1 ? rest.charAt(0) : '\0';
            if (c < ' ' || c == KeyEvent.VK_DELETE || (c == ' ' && keyCode != KeyEvent.VK_SPACE)) {
                throw new IllegalArgumentException("\"" + key + "\" is not a key: write a character, space, escape, "
                    + "or a chord such as ctrl+j");
            }
            return new Binding(key, Layer.COMMAND, KeyStroke.getKeyStroke(c), command);
        }
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) == 0) {
            throw new IllegalArgumentException("\"" + key + "\" is not a chord: a chord holds ctrl");
        }
        if (keyCode == KeyEvent.VK_UNDEFINED && rest.length() == 1) {
            keyCode = KeyEvent.getExtendedKeyCodeForChar(Character.toLowerCase(rest.charAt(0)));
        }
        if (!PRESSABLE.contains(keyCode)) {
            throw new IllegalArgumentException("\"" + key + "\" is not a key: a chord names a key as it is unshifted, "
                + "such as ctrl+shift+[ rather than ctrl+{");
        }
        return new Binding(key, Layer.CHORD, KeyStroke.getKeyStroke(keyCode, modifiers), command);
    }

    /** Returns the modifier a key as written starts with, such as {@code ctrl+}, or 0 for none. */
    private static int modifier(final String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        int plus = lower.indexOf('+');
        if (plus < 0 || plus == lower.length() - 1) {
            return 0;
        }
        return switch (lower.substring(0, plus)) {
            case "ctrl" -> InputEvent.CTRL_DOWN_MASK;
            case "shift" -> InputEvent.SHIFT_DOWN_MASK;
            case "alt" -> InputEvent.ALT_DOWN_MASK;
            default -> 0;
        };
    }

    /** Returns the key a name writes, escape or space, or {@link KeyEvent#VK_UNDEFINED} for any other. */
    private static int namedKey(final String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "escape", "esc" -> KeyEvent.VK_ESCAPE;
            case "space" -> KeyEvent.VK_SPACE;
            default -> KeyEvent.VK_UNDEFINED;
        };
    }

    /** Returns a mapped key as shown to the user: the character, Space, or a chord as macOS menus show it. */
    static String describe(final Binding binding) {
        if (binding.layer() == Layer.CHORD) {
            return Shortcuts.describe(binding.stroke(), false);
        }
        char c = binding.stroke().getKeyChar();
        return c == ' ' ? "Space" : String.valueOf(c);
    }
}
