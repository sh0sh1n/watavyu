package org.datavyu.views;

import org.datavyu.util.Resources;

/**
 * The main window's commands, by stable names. Its menu bar and keyboard shortcuts, the keypad's included, run a
 * command through one action ({@link CommandTable}), so a command, its label and its state are defined once; a key
 * mapping can name commands too (F7). The label is the command's name in menus and the shortcut list. The controls
 * drawer's keys call the selected tab's playback themselves; the keypad runs them by name through
 * {@link VideoController#run}.
 */
public enum Command {
    NEW_PROJECT(menu("newMenuItem.text")),
    OPEN(menu("openMenuItem.text")),
    CLOSE_TAB(menu("closeTabMenuItem.text")),
    SAVE(menu("saveMenuItem.text")),
    SAVE_AS(menu("saveAsMenuItem.text")),

    NEW_MEDIA_COLUMN(menu("newMediaColumnMenuItem.text")),
    ADD_MEDIA(menu("addMediaMenuItem.text")),
    COLUMN_LIST(menu("columnListMenuItem.text")),
    CODE_EDITOR(menu("codeEditorMenuItem.text")),
    NEW_CELL(menu("newCellMenuItem.text")),
    NEW_CELL_LEFT(menu("newCellLeftMenuItem.text")),
    NEW_CELL_RIGHT(menu("newCellRightMenuItem.text")),
    DELETE_CELLS(menu("deleteCellMenuItem.text")),
    UNDO("Undo"),
    REDO("Redo"),
    EXPORT_JSON(menu("exportJSONMenuItem.text")),
    IMPORT_JSON(menu("importJSONMenuItem.text")),
    TEMPORAL_LAYOUT(menu("weakTemporalAlignmentMenuItem.text")),
    ZOOM_IN(menu("zoomInMenuItem.text")),
    ZOOM_OUT(menu("zoomOutMenuItem.text")),
    RESET_ZOOM(menu("resetZoomMenuItem.text")),
    MONOSPACED_VALUES(menu("monospacedValuesMenuItem.text")),
    QUICK_KEYS("Quick Key Mode"),
    HIGHLIGHT_AND_FOCUS("Highlight and Focus Mode"),

    SHOW_CONTROLS(menu("showControlsMenuItem.text")),
    PLAYHEAD_ACROSS_COLUMNS(menu("playheadAcrossColumnsMenuItem.text")),
    SET_ONSET("Set cell onset"),
    PLAY("Play"),
    SET_OFFSET("Set cell offset"),
    SHUTTLE_BACK("Shuttle back"),
    STOP("Stop"),
    SHUTTLE_FORWARD("Shuttle forward"),
    JOG_BACK("Jog back one frame"),
    PAUSE("Pause"),
    JOG_FORWARD("Jog forward one frame"),
    NEW_CELL_CLOSING_PREVIOUS("New cell, setting the previous cell's offset"),
    JUMP_BACK("Jump back by the jump time"),
    JUMP_FORWARD("Jump forward by the jump time"),
    FIND_ONSET("Go to the onset field's time"),
    FIND_OFFSET("Go to the offset field's time"),
    NEW_CELL_IN_SELECTED_COLUMN("New cell"),

    SHORTCUTS(menu("hotkeysMenuItem.text")),
    CITATION(menu("citationMenuItem.text"));

    private final String label;

    Command(final String label) {
        this.label = label;
    }

    private static String menu(final String key) {
        return Labels.MENU.getString(key);
    }

    /** The menu labels, loaded once, when the first command is made. */
    private static final class Labels {
        static final Resources MENU = Resources.of(DatavyuView.class);
    }

    /** Returns the command's name in menus and the shortcut list. */
    public String label() {
        return label;
    }
}
