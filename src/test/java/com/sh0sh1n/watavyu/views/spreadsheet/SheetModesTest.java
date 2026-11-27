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
 * or ⌃[ back out, with the cell still selected. Which keys do this is the key map's ({@code KeyMapTest},
 * {@code ShortcutsTest}); here are what they do.
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

    /** Puts the test in insert mode in a selected cell, with its values in focus. */
    private SpreadsheetCell insertIn(final Cell cell) {
        cell.setSelected(true);
        notes.setSelected(true);
        SpreadsheetCell editor = sheet.edit(cell);
        owner.set(editor.getDataView());
        return editor;
    }

    @Test
    public void leavingInsertModeGoesToCommandModeWithTheCellSelected() throws Exception {
        withSheet(column -> {
            insertIn(second);
            assertTrue(sheet.inInsertMode());
            assertTrue(sheet.canLeaveInsertMode());

            assertTrue(sheet.leaveInsertMode());
            assertEquals(focused, List.of(sheet));
            assertTrue(sheet.inCommandMode());
            assertTrue(second.isSelected());
            assertTrue(notes.isSelected());
        });
    }

    /** Esc in command mode, or with the focus in another window or field, is not the sheet's. */
    @Test
    public void outsideInsertModeThereIsNothingToLeave() throws Exception {
        withSheet(column -> {
            owner.set(sheet);
            assertFalse(sheet.canLeaveInsertMode());
            assertFalse(sheet.leaveInsertMode());
            owner.set(new JTextField());
            assertFalse(sheet.leaveInsertMode());
            owner.set(null);
            assertFalse(sheet.leaveInsertMode());
            assertEquals(focused, List.of());
        });
    }

    @Test
    public void insertModeGoesIntoTheSelectedCell() throws Exception {
        withSheet(column -> {
            second.setSelected(true);
            owner.set(sheet);

            assertTrue(sheet.insertMode());
            assertEquals(focused.size(), 1);
            assertEquals(((SpreadsheetCell) focused.get(0)).getCell(), second);
            assertTrue(sheet.inInsertMode());
        });
    }

    /** With a column selected and no cell, there is nothing to type into. */
    @Test
    public void insertModeWithNoCellSelectedDoesNothing() throws Exception {
        withSheet(column -> {
            notes.setSelected(true);
            owner.set(sheet);

            assertFalse(sheet.insertMode());
            assertEquals(focused, List.of());
            assertTrue(sheet.inCommandMode());
        });
    }

    /**
     * Plain keys are commands with the keyboard on the sheet, or elsewhere in its window, but not in insert mode, in
     * another text field, or with a menu open.
     */
    @Test
    public void plainKeysAreCommandsOnlyInCommandMode() throws Exception {
        withSheet(column -> {
            owner.set(sheet);
            assertTrue(sheet.takesCommandKeys());
            owner.set(new javax.swing.JButton());
            assertTrue(sheet.takesCommandKeys(), "a button beside the sheet");
            owner.set(new JTextField());
            assertFalse(sheet.takesCommandKeys(), "a text field");
            owner.set(new javax.swing.JComboBox<String>());
            assertFalse(sheet.takesCommandKeys(), "a combo box, which picks an item by its first letter");
            owner.set(new javax.swing.JList<String>());
            assertFalse(sheet.takesCommandKeys(), "a list");
            owner.set(null);
            assertFalse(sheet.takesCommandKeys(), "another app");

            insertIn(second);
            assertFalse(sheet.takesCommandKeys(), "insert mode");
            sheet.leaveInsertMode();
            assertTrue(sheet.takesCommandKeys());
            javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(
                new javax.swing.MenuElement[] {new javax.swing.JPopupMenu()});
            try {
                assertFalse(sheet.takesCommandKeys(), "a menu open");
            } finally {
                javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
            }
        });
    }

    /**
     * The selection is the sheet's, and does not follow the focus: focus leaving the editor, for the sheet, another
     * window or another field, keeps the cell and its column selected, and focus coming to an editor selects nothing.
     */
    @Test
    public void theFocusLeavesTheSelectionAlone() throws Exception {
        withSheet(column -> {
            SpreadsheetCell editor = insertIn(second);
            editor.focusLost(new FocusEvent(editor.getOnset(), FocusEvent.FOCUS_LOST, false, sheet));
            editor.getDataView().focusLost(new FocusEvent(editor.getDataView(), FocusEvent.FOCUS_LOST, false, null));
            editor.focusLost(new FocusEvent(editor.getOnset(), FocusEvent.FOCUS_LOST, false, new JTextField()));
            assertTrue(second.isSelected());
            assertTrue(notes.isSelected());

            SpreadsheetCell other = sheet.edit(first);
            other.getDataView().focusGained(new FocusEvent(other.getDataView(), FocusEvent.FOCUS_GAINED));
            other.focusGained(new FocusEvent(other.getOnset(), FocusEvent.FOCUS_GAINED));
            assertFalse(first.isSelected());
            assertTrue(second.isSelected());
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

    /** In insert mode, a tick moves the editor and the keyboard to the cell in play, selected alone in its column. */
    @Test
    public void followingTheNeedleInInsertModeSelectsTheCellInPlay() throws Exception {
        withSheet(column -> {
            when(playback.getCellHighlightAndFocus()).thenReturn(true);
            when(playback.getCurrentTime()).thenReturn(2_000L);
            when(clock.isPaused()).thenReturn(false);
            insertIn(first);

            column.followNeedle();

            assertTrue(second.isSelected());
            assertFalse(first.isSelected());
            assertEquals(sheet.getEditor().orElseThrow().getCell(), second);
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
            assertFalse(sheet.leaveInsertMode());
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
            assertTrue(sheet.leaveInsertMode());
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
                assertFalse(sheet.leaveInsertMode());
                assertEquals(focused, List.of());
            } finally {
                javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
            }
        });
    }

    /** Tab does not move the focus out of the sheet, and so into the editor, in command mode. */
    @Test
    public void tabStaysInTheSheet() throws Exception {
        withSheet(column -> assertFalse(sheet.getFocusTraversalKeysEnabled()));
    }
}
