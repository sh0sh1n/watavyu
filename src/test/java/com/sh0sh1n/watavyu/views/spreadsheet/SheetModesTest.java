package com.sh0sh1n.watavyu.views.spreadsheet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.TestSpreadsheets;
import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.Project;
import com.sh0sh1n.watavyu.util.ClockTimer;
import com.sh0sh1n.watavyu.views.VideoController;
import org.testng.annotations.Test;

import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * The keyboard's modes (AGENTS.md › Decisions, 2026-11-13): in command mode the sheet has the focus and keys are
 * commands; in insert mode a part of the editor has it and keys type. {@code i} goes into the selected cell, and Esc
 * or ⌃] back out, with the cell still selected.
 */
public class SheetModesTest {

    private Column notes;
    private Cell first;
    private Cell second;
    private VideoController playback;
    private ClockTimer clock;
    private SpreadsheetView sheet;
    private SpreadsheetColumn column;

    /** The component the test says has the focus. */
    private final AtomicReference<Component> owner = new AtomicReference<>();

    /** The components the sheet gave the focus, in turn. */
    private final List<Component> focused = new ArrayList<>();

    /** Builds a column of two unselected cells and runs the test with it on the EDT. */
    private void withSheet(final Consumer<SpreadsheetColumn> test) throws Exception {
        owner.set(null);
        focused.clear();
        DataStore dataStore = DataStoreFactory.newDataStore();
        notes = dataStore.createColumn("notes");
        first = notes.createPointCell(1_000);
        second = notes.createPointCell(2_000);
        for (Cell cell : notes.getCells()) {
            cell.setSelected(false);
        }
        playback = mock(VideoController.class);
        clock = mock(ClockTimer.class);
        when(playback.getClockTimer()).thenReturn(clock);
        ProjectController spreadsheet = new ProjectController(new Project(), dataStore);
        spreadsheet.setVideoController(playback);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                sheet = new SpreadsheetView(spreadsheet);
                sheet.setFocus(owner::get, component -> {
                    focused.add(component);
                    owner.set(component instanceof SpreadsheetCell cell ? cell.getDataView() : component);
                });
                column = new SpreadsheetColumn(spreadsheet, notes, sheet, mock(CellSelectionListener.class),
                    mock(ColumnSelectionListener.class), mock(ColumnVisibilityListener.class));
                sheet.addColumn(column);
                try {
                    test.accept(column);
                } finally {
                    sheet.close();
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private KeyEvent press(final int keyCode, final int modifiers) {
        return new KeyEvent(sheet, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    private KeyEvent typed(final char character) {
        return new KeyEvent(sheet, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, character);
    }

    /** Puts the test in insert mode in a selected cell, with its values in focus. */
    private SpreadsheetCell insertIn(final Cell cell) {
        cell.setSelected(true);
        notes.setSelected(true);
        SpreadsheetCell editor = sheet.edit(cell);
        owner.set(editor.getDataView());
        return editor;
    }

    @Test
    public void escapeInInsertModeGoesToCommandModeAndDropsItsCharacter() throws Exception {
        withSheet(column -> {
            insertIn(second);
            assertTrue(sheet.inInsertMode());

            KeyEvent escape = press(KeyEvent.VK_ESCAPE, 0);
            assertTrue(sheet.modeKey(escape));
            assertTrue(escape.isConsumed());
            assertEquals(focused, List.of(sheet));
            assertTrue(sheet.inCommandMode());

            KeyEvent character = typed('\u001b');
            assertTrue(sheet.modeKey(character), "its character does not reach the cell");
            assertTrue(character.isConsumed());
            assertFalse(sheet.modeKey(press(KeyEvent.VK_X, 0)));
            assertFalse(sheet.modeKey(typed('x')), "the next key types");
        });
    }

    @Test
    public void controlCloseBracketGoesToCommandModeToo() throws Exception {
        withSheet(column -> {
            insertIn(second);
            assertTrue(sheet.modeKey(press(KeyEvent.VK_CLOSE_BRACKET, InputEvent.CTRL_DOWN_MASK)));
            assertEquals(focused, List.of(sheet));
        });
    }

    @Test
    public void otherBracketsAndEscapesWithModifiersType() throws Exception {
        withSheet(column -> {
            insertIn(second);
            assertFalse(sheet.modeKey(press(KeyEvent.VK_CLOSE_BRACKET, 0)));
            assertFalse(sheet.modeKey(press(KeyEvent.VK_CLOSE_BRACKET, InputEvent.META_DOWN_MASK)));
            assertFalse(sheet.modeKey(press(KeyEvent.VK_ESCAPE, InputEvent.SHIFT_DOWN_MASK)));
            assertEquals(focused, List.of());
        });
    }

    /** Esc in command mode, or with the focus in another window or field, is not the sheet's. */
    @Test
    public void escapeOutsideInsertModeIsLeftAlone() throws Exception {
        withSheet(column -> {
            owner.set(sheet);
            assertFalse(sheet.modeKey(press(KeyEvent.VK_ESCAPE, 0)));
            owner.set(new JTextField());
            assertFalse(sheet.modeKey(press(KeyEvent.VK_ESCAPE, 0)));
            owner.set(null);
            assertFalse(sheet.modeKey(press(KeyEvent.VK_ESCAPE, 0)));
            assertEquals(focused, List.of());
        });
    }

    @Test
    public void iInCommandModeGoesIntoTheSelectedCell() throws Exception {
        withSheet(column -> {
            second.setSelected(true);
            owner.set(sheet);

            assertTrue(sheet.modeKey(press(KeyEvent.VK_I, 0)));
            assertEquals(focused.size(), 1);
            assertEquals(((SpreadsheetCell) focused.get(0)).getCell(), second);
            assertTrue(sheet.inInsertMode());
            assertTrue(sheet.modeKey(typed('i')), "the i does not reach the cell");
        });
    }

    /** With a column selected and no cell, there is nothing to type into, and the key is left alone. */
    @Test
    public void iWithNoCellSelectedDoesNothing() throws Exception {
        withSheet(column -> {
            notes.setSelected(true);
            owner.set(sheet);

            assertFalse(sheet.modeKey(press(KeyEvent.VK_I, 0)));
            assertFalse(sheet.modeKey(typed('i')));
            assertEquals(focused, List.of());
        });
    }

    @Test
    public void iInInsertModeTypes() throws Exception {
        withSheet(column -> {
            insertIn(second);
            assertFalse(sheet.modeKey(press(KeyEvent.VK_I, 0)));
            assertFalse(sheet.modeKey(typed('i')));
        });
    }

    /** Focus leaving the editor for the sheet keeps the cell and its column selected; leaving elsewhere does not. */
    @Test
    public void goingToCommandModeKeepsTheSelection() throws Exception {
        withSheet(column -> {
            SpreadsheetCell editor = insertIn(second);
            editor.focusLost(new FocusEvent(editor.getOnset(), FocusEvent.FOCUS_LOST, false, sheet));
            editor.getDataView().focusLost(new FocusEvent(editor.getDataView(), FocusEvent.FOCUS_LOST, false, sheet));
            assertTrue(second.isSelected());
            assertTrue(notes.isSelected());

            editor.getDataView().focusLost(new FocusEvent(editor.getDataView(), FocusEvent.FOCUS_LOST, false, null));
            assertFalse(second.isSelected(), "focus leaving for another window deselects, as before");
            second.setSelected(true);
            editor.focusLost(new FocusEvent(editor.getOnset(), FocusEvent.FOCUS_LOST, false, new JTextField()));
            assertFalse(second.isSelected());
            assertFalse(notes.isSelected());
        });
    }

    /** A press on a cell outside its values and times, as on its ordinal, selects it and its column in command mode. */
    @Test
    public void aPressOutsideTheValuesSelectsInCommandMode() throws Exception {
        withSheet(column -> {
            SpreadsheetCell editor = sheet.edit(first);
            editor.mousePressed(new MouseEvent(editor, MouseEvent.MOUSE_PRESSED, 0, 0, 2, 2, 1, false,
                MouseEvent.BUTTON1));

            assertTrue(first.isSelected());
            assertTrue(first.isHighlighted());
            assertTrue(notes.isSelected());
            assertEquals(focused, List.of(sheet));
        });
    }

    /**
     * In highlight-and-focus mode, a tick in command mode selects the selected column's cell in play and leaves the
     * keys as commands, where in insert mode it moves the caret into the cell.
     */
    @Test
    public void followingTheNeedleKeepsCommandMode() throws Exception {
        withSheet(column -> {
            when(playback.getCellHighlightAndFocus()).thenReturn(true);
            when(playback.getCurrentTime()).thenReturn(2_000L);
            when(clock.isPaused()).thenReturn(false);
            notes.setSelected(true);
            owner.set(sheet);

            column.followNeedle();

            assertTrue(second.isSelected());
            assertTrue(second.isHighlighted());
            assertFalse(first.isSelected(), "the cell left behind is deselected");
            assertTrue(sheet.inCommandMode());
            assertEquals(focused, List.of(), "the focus stays with the sheet");
            assertEquals(sheet.getEditor().orElseThrow().getCell(), second, "the editor waits over the cell");
        });
    }

    /** With the keyboard in another window, a tick selects the cell in play and leaves the focus where it is. */
    @Test
    public void followingTheNeedleLeavesTheFocusInAnotherWindow() throws Exception {
        withSheet(column -> {
            when(playback.getCellHighlightAndFocus()).thenReturn(true);
            when(playback.getCurrentTime()).thenReturn(2_000L);
            when(clock.isPaused()).thenReturn(false);
            notes.setSelected(true);
            owner.set(new JTextField());

            column.followNeedle();

            assertTrue(second.isSelected());
            assertEquals(focused, List.of());
        });
    }

    /** With several cells selected in the column, including the cell in play, a tick leaves it selected alone. */
    @Test
    public void followingTheNeedleSelectsTheCellInPlayAlone() throws Exception {
        withSheet(column -> {
            when(playback.getCellHighlightAndFocus()).thenReturn(true);
            when(playback.getCurrentTime()).thenReturn(2_000L);
            when(clock.isPaused()).thenReturn(false);
            notes.setSelected(true);
            first.setSelected(true);
            second.setSelected(true);
            owner.set(sheet);

            column.followNeedle();

            assertTrue(second.isSelected());
            assertFalse(first.isSelected());
            assertEquals(sheet.getEditor().orElseThrow().getCell(), second);
        });
    }

    /** ↑ and ↓ in command mode move the selection and stay in command mode; in insert mode they move the caret. */
    @Test
    public void arrowsInCommandModeStayInCommandMode() throws Exception {
        withSheet(column -> {
            column.selectInCommandMode(first);
            focused.clear();

            KeyEvent down = press(KeyEvent.VK_DOWN, 0);
            assertTrue(column.dispatchKeyEvent(down));
            assertTrue(down.isConsumed());
            assertTrue(second.isSelected());
            assertTrue(second.isHighlighted());
            assertTrue(sheet.inCommandMode());
            assertEquals(focused, List.of(sheet));

            focused.clear();
            second.setSelected(false);
            insertIn(first);
            assertTrue(column.dispatchKeyEvent(press(KeyEvent.VK_DOWN, 0)));
            assertTrue(second.isSelected());
            assertTrue(sheet.inInsertMode(), "insert mode stays too");
        });
    }

    /** Only a part of a cell's editor is insert mode; anything else in the sheet is not. */
    @Test
    public void onlyTheEditorIsInsertMode() throws Exception {
        withSheet(column -> {
            JTextField stray = new JTextField();
            sheet.add(stray);
            owner.set(stray);
            assertFalse(sheet.inInsertMode());
            assertFalse(sheet.modeKey(press(KeyEvent.VK_ESCAPE, 0)));
            sheet.remove(stray);
        });
    }

    /**
     * An editor taken off its cell while it has the focus, as when the cell is deleted, keeps the focus until it goes
     * elsewhere; Esc still leaves it.
     */
    @Test
    public void escapeLeavesAnEditorTakenOffItsCell() throws Exception {
        withSheet(column -> {
            SpreadsheetCell editor = insertIn(second);
            sheet.unwatch(second);
            assertTrue(sheet.getEditor().isEmpty());
            assertEquals(editor.getParent(), sheet, "the editor waits, out of sight, for the focus to leave");
            assertTrue(sheet.inInsertMode());
            assertTrue(sheet.modeKey(press(KeyEvent.VK_ESCAPE, 0)));
            assertEquals(focused, List.of(sheet));
        });
    }

    /** Esc with a menu open closes the menu, and stays in insert mode. */
    @Test
    public void escapeWithAMenuOpenIsTheMenus() throws Exception {
        withSheet(column -> {
            insertIn(second);
            javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
            javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(new javax.swing.MenuElement[] {menu});
            try {
                assertFalse(sheet.modeKey(press(KeyEvent.VK_ESCAPE, 0)));
                assertEquals(focused, List.of());
            } finally {
                javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
            }
        });
    }

    /** ↑ and ↓ with the focus outside the sheet, in another window or tab, are not its keys. */
    @Test
    public void arrowsOutsideTheSheetAreLeftAlone() throws Exception {
        withSheet(column -> {
            column.selectInCommandMode(first);
            focused.clear();
            owner.set(new JTextField());

            KeyEvent down = press(KeyEvent.VK_DOWN, 0);
            assertFalse(column.dispatchKeyEvent(down));
            assertFalse(down.isConsumed());
            assertFalse(second.isSelected());
            assertEquals(focused, List.of());
        });
    }

    /** Focus going from the editor to another tab's sheet is focus leaving this one, which deselects. */
    @Test
    public void focusGoingToAnotherSheetDeselects() throws Exception {
        withSheet(column -> {
            SpreadsheetCell editor = insertIn(second);
            SpreadsheetView other = new SpreadsheetView(
                TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore()));
            try {
                editor.getDataView().focusLost(new FocusEvent(editor.getDataView(), FocusEvent.FOCUS_LOST, false,
                    other));
                assertFalse(second.isSelected());
            } finally {
                other.close();
            }
        });
    }

    /**
     * A held key repeats, pressing and typing again. A held i goes into the cell once, its own character dropped, and
     * then types, as in vim.
     */
    @Test
    public void aHeldIGoesInOnceAndThenTypes() throws Exception {
        withSheet(column -> {
            second.setSelected(true);
            owner.set(sheet);
            assertTrue(sheet.modeKey(press(KeyEvent.VK_I, 0)));
            assertTrue(sheet.modeKey(typed('i')));
            assertFalse(sheet.modeKey(press(KeyEvent.VK_I, 0)), "a repeat types");
            assertFalse(sheet.modeKey(typed('i')));
            assertEquals(focused.size(), 1, "into the cell once");
        });
    }

    /** A held Esc's characters are all dropped until it is released, whatever else is released meanwhile. */
    @Test
    public void aHeldEscapeTypesNothing() throws Exception {
        withSheet(column -> {
            insertIn(second);
            assertTrue(sheet.modeKey(press(KeyEvent.VK_ESCAPE, 0)));
            assertTrue(sheet.modeKey(typed('\u001b')));
            assertTrue(sheet.modeKey(typed('\u001b')), "a repeat is dropped too");
            assertFalse(sheet.modeKey(new KeyEvent(sheet, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_X, 'x')),
                "releasing another key leaves the held one");
            assertTrue(sheet.modeKey(typed('\u001b')));
            sheet.modeKey(new KeyEvent(sheet, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_ESCAPE, '\u001b'));
            assertFalse(sheet.modeKey(typed('x')));
        });
    }

    /** Tab does not move the focus out of the sheet, and so into the editor, in command mode. */
    @Test
    public void tabStaysInTheSheet() throws Exception {
        withSheet(column -> assertFalse(sheet.getFocusTraversalKeysEnabled()));
    }
}
