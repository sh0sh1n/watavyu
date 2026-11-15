package com.sh0sh1n.watavyu.views;

import com.sh0sh1n.watavyu.util.Resources;

/**
 * The main window's commands, by stable names. Its menu bar and keyboard shortcuts, the key map's included, run a
 * command through one action ({@link CommandTable}), so a command, its label and its state are defined once. The key
 * map ({@link KeyMap}) and its changes in Settings name commands by these names. The label is the command's name in
 * menus and the shortcut list. The controls drawer's keys call the selected tab's playback themselves; the key map
 * runs the same commands by name through {@link VideoController#run}.
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
    SELECT_COLUMN_LEFT("Select column to left"),
    SELECT_COLUMN_RIGHT("Select column to right"),
    NEXT_CELL("Next cell in the column"),
    PREVIOUS_CELL("Previous cell in the column"),
    COLUMN_LEFT("Column to the left"),
    COLUMN_RIGHT("Column to the right"),
    NEW_CELL_HERE("New cell at the playhead in the selected column"),
    NEW_CELL_HERE_CLOSING_PREVIOUS("New cell at the playhead, setting the previous cell's offset"),
    GO_TO_ONSET("Go to the selected cell's onset"),
    GO_TO_OFFSET("Go to the selected cell's offset"),
    INSERT_MODE("Type in the selected cell (insert mode)"),
    QUICK_MODE("Make cells with letters and digits (quick mode)"),
    COMMAND_MODE("Back to command mode"),
    UNDO("Undo"),
    REDO("Redo"),
    TEMPORAL_LAYOUT(menu("weakTemporalAlignmentMenuItem.text")),
    ZOOM_IN(menu("zoomInMenuItem.text")),
    ZOOM_OUT(menu("zoomOutMenuItem.text")),
    RESET_ZOOM(menu("resetZoomMenuItem.text")),
    MONOSPACED_VALUES(menu("monospacedValuesMenuItem.text")),
    HIGHLIGHT_AND_FOCUS("Highlight and Focus Mode"),

    SHOW_CONTROLS(menu("showControlsMenuItem.text")),
    PLAYHEAD_ACROSS_COLUMNS(menu("playheadAcrossColumnsMenuItem.text")),
    SET_ONSET("Set cell onset"),
    PLAY_PAUSE("Play or pause"),
    SET_OFFSET("Set cell offset"),
    SHUTTLE_BACK("Shuttle back"),
    RESET_RATE("Reset the rate to 1×"),
    SHUTTLE_FORWARD("Shuttle forward"),
    JOG_BACK("Jog back one frame"),
    JOG_FORWARD("Jog forward one frame"),
    NEW_CELL_CLOSING_PREVIOUS("New cell, setting the previous cell's offset"),
    JUMP_BACK("Jump back by the jump time"),
    JUMP_FORWARD("Jump forward by the jump time"),
    FIND_ONSET("Go to the onset field's time"),
    FIND_OFFSET("Go to the offset field's time"),
    NEW_CELL_IN_SELECTED_COLUMN("New cell"),

    SHORTCUTS(menu("hotkeysMenuItem.text")),
    CITATION(menu("citationMenuItem.text"));

    private final String text;

    Command(final String text) {
        this.text = text;
    }

    private static String menu(final String key) {
        return Labels.MENU.getString(key);
    }

    /** The menu labels, loaded once, when the first command is made. */
    private static final class Labels {
        static final Resources MENU = Resources.of(MainWindow.class);
    }

    /** Returns the command's name in menus and the shortcut list. */
    public String label() {
        return text;
    }
}
