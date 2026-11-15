package com.sh0sh1n.watavyu.views.spreadsheet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.TestSpreadsheets;
import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import org.testng.annotations.Test;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * The navigation keys in a spreadsheet (AGENTS.md › Decisions, 2026-11-13): ⌃J and ⌃K, and ↓ and ↑ while the keyboard
 * is in the sheet, go down and up the selected column; ⌃H and ⌃L go to the annotation column beside, landing on the
 * cell whose onset is nearest. Each keeps the mode the keyboard is in. Where they go is {@code CellNavigationTest}'s.
 */
public class SheetNavigationTest {

    private Column gaze;
    private Column speech;
    private Column empty;
    private Cell first;
    private Cell second;
    private Cell third;
    private Cell spoken;
    private ProjectController spreadsheet;
    private SpreadsheetView sheet;

    /** The component the test says has the focus. */
    private final AtomicReference<Component> owner = new AtomicReference<>();

    /** The components the sheet gave the focus, in turn. */
    private final List<Component> focused = new ArrayList<>();

    /**
     * Builds gaze (cells at 1, 2 and 3 s), a media column, speech (a cell at 2.6 s) and an empty column, nothing
     * selected, and runs the test with them on the EDT.
     */
    private void withPanel(final Consumer<SpreadSheetPanel> test) throws Exception {
        owner.set(null);
        focused.clear();
        DataStore dataStore = DataStoreFactory.newDataStore();
        gaze = dataStore.createColumn("gaze");
        dataStore.createMediaColumn("media_1");
        speech = dataStore.createColumn("speech");
        empty = dataStore.createColumn("empty");
        third = gaze.createPointCell(3_000);
        first = gaze.createPointCell(1_000);
        second = gaze.createPointCell(2_000);
        spoken = speech.createPointCell(2_600);
        dataStore.deselectAll();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            spreadsheet = TestSpreadsheets.withPlayback(dataStore);
            SpreadSheetPanel panel = new SpreadSheetPanel(spreadsheet, null);
            spreadsheet.setSpreadSheetPanel(panel);
            panel.setSize(900, 400);
            layOut(panel);
            try {
                sheet = panel.getColumns().get(0).getSheet();
                sheet.setFocus(owner::get, component -> {
                    focused.add(component);
                    owner.set(component instanceof SpreadsheetCell cell ? cell.getDataView() : component);
                });
                test.accept(panel);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                panel.close();
            }
        });
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static void layOut(final Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layOut(child);
            }
        }
    }

    private SpreadsheetColumn column(final SpreadSheetPanel panel, final Column column) {
        return panel.getColumns().stream().filter(view -> view.getColumn().equals(column)).findFirst().orElseThrow();
    }

    private KeyEvent press(final int keyCode, final int modifiers) {
        return new KeyEvent(sheet, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    /** Puts the keyboard in insert mode in a cell, selected alone. */
    private void insertIn(final SpreadSheetPanel panel, final Cell cell) {
        column(panel, cell.getColumn()).select(cell);
        owner.set(sheet.edit(cell).getDataView());
        focused.clear();
    }

    @Test
    public void downAndUpInCommandModeMoveTheSelectionAndStay() throws Exception {
        withPanel(panel -> {
            column(panel, gaze).selectInCommandMode(first);
            focused.clear();

            KeyEvent down = press(KeyEvent.VK_DOWN, 0);
            assertTrue(panel.takeKey(down));
            assertTrue(down.isConsumed());
            assertTrue(second.isSelected());
            assertFalse(first.isSelected());
            assertTrue(sheet.inCommandMode());

            panel.moveVertically(false);
            assertTrue(first.isSelected());
            assertFalse(second.isSelected());
            assertTrue(sheet.inCommandMode());
        });
    }

    /** In insert mode the keys stay in insert mode, in the same value of the next cell. */
    @Test
    public void inInsertModeTheCaretStaysInTheSameValue() throws Exception {
        withPanel(panel -> {
            gaze.addCode();
            insertIn(panel, second);
            EditorTracker tracker = sheet.getEditor().orElseThrow().getDataView().getEdTracker();
            tracker.setEditor(tracker.getEditorAtIndex(1));

            assertTrue(panel.takeKey(press(KeyEvent.VK_DOWN, 0)));

            SpreadsheetCell editor = sheet.getEditor().orElseThrow();
            assertEquals(editor.getCell(), third);
            assertTrue(third.isSelected());
            assertFalse(second.isSelected());
            assertEquals(editor.getDataView().getEdTracker().indexOfCurrentEditor(), 1);
            assertTrue(sheet.inInsertMode());
            assertEquals(focused.size(), 1);
        });
    }

    /** At a column's ends the keys go no further, and ↑ and ↓ are still taken, as ⌃K and ⌃J are, so nothing scrolls. */
    @Test
    public void theEndsOfTheColumnGoNoFurther() throws Exception {
        withPanel(panel -> {
            column(panel, gaze).selectInCommandMode(first);
            KeyEvent up = press(KeyEvent.VK_UP, 0);
            assertTrue(panel.takeKey(up));
            assertTrue(up.isConsumed());
            assertTrue(first.isSelected());
            assertFalse(panel.moveVertically(false));
        });
    }

    /** Navigation goes from the cell being typed in, though other cells are selected too. */
    @Test
    public void itGoesFromTheCellTypedIn() throws Exception {
        withPanel(panel -> {
            insertIn(panel, third);
            first.setSelected(true);

            panel.moveVertically(false);

            assertTrue(second.isSelected());
            assertEquals(sheet.getEditor().orElseThrow().getCell(), second);
        });
    }

    /** From a cell's onset, the next cell's onset takes the keyboard, and so for the offset. */
    @Test
    public void fromATimeItGoesToTheSameTime() throws Exception {
        withPanel(panel -> {
            insertIn(panel, first);
            owner.set(sheet.getEditor().orElseThrow().getOnset());

            panel.moveVertically(true);

            assertEquals(focused, List.of(sheet.getEditor().orElseThrow().getOnset()));
            assertEquals(sheet.getEditor().orElseThrow().getCell(), second);

            focused.clear();
            owner.set(sheet.getEditor().orElseThrow().getOffset());
            panel.moveVertically(true);
            assertEquals(focused, List.of(sheet.getEditor().orElseThrow().getOffset()));
        });
    }

    /** With the column selected and no cell, down goes to the first cell at or after the playhead, up to the last. */
    @Test
    public void withNoCellTheyGoFromThePlayhead() throws Exception {
        withPanel(panel -> {
            column(panel, gaze).goToColumn();
            owner.set(sheet);
            spreadsheet.getVideoController().setCurrentTime(1_500);

            panel.moveVertically(true);
            assertTrue(second.isSelected());

            column(panel, gaze).goToColumn();
            panel.moveVertically(false);
            assertTrue(first.isSelected());
        });
    }

    /** ↑ and ↓ are the sheet's only while the keyboard is in it, and with no modifier. */
    @Test
    public void arrowsOutsideTheSheetOrWithModifiersAreLeftAlone() throws Exception {
        withPanel(panel -> {
            column(panel, gaze).selectInCommandMode(first);
            owner.set(new JTextField());
            assertFalse(panel.takeKey(press(KeyEvent.VK_DOWN, 0)));

            owner.set(sheet);
            assertFalse(panel.takeKey(press(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK)));
            assertFalse(panel.takeKey(
                new KeyEvent(sheet, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED)));
            assertTrue(first.isSelected());
            assertFalse(second.isSelected());
        });
    }

    /** A chord from another window moves the selection and leaves the keyboard there. */
    @Test
    public void fromAnotherWindowTheKeyboardStaysThere() throws Exception {
        withPanel(panel -> {
            column(panel, gaze).select(first);
            owner.set(new JTextField());
            focused.clear();

            panel.moveVertically(true);

            assertTrue(second.isSelected());
            assertEquals(focused, List.of());
        });
    }

    /** Right passes over the media column to speech, landing on the cell whose onset is nearest the selected cell's. */
    @Test
    public void rightLandsOnTheNearestCellPassingOverMediaColumns() throws Exception {
        withPanel(panel -> {
            column(panel, gaze).selectInCommandMode(third);

            panel.moveHorizontally(true);

            assertTrue(spoken.isSelected());
            assertTrue(speech.isSelected());
            assertFalse(third.isSelected());
            assertFalse(gaze.isSelected());
            assertTrue(sheet.inCommandMode());

            panel.moveHorizontally(false);
            assertTrue(third.isSelected(), "back to the nearest, 3 s, not the 1 s cell");
        });
    }

    /** A column with no cells is selected alone; from insert mode the keyboard goes to command mode. */
    @Test
    public void aColumnWithNoCellsIsSelectedInCommandMode() throws Exception {
        withPanel(panel -> {
            insertIn(panel, spoken);

            panel.moveHorizontally(true);

            assertTrue(empty.isSelected());
            assertFalse(speech.isSelected());
            assertFalse(spoken.isSelected());
            assertTrue(sheet.inCommandMode());
            assertEquals(focused, List.of(sheet));

            panel.moveHorizontally(true);
            assertTrue(empty.isSelected(), "the last column goes no further");
        });
    }

    /** In insert mode, the column beside is gone to in insert mode too. */
    @Test
    public void inInsertModeTheColumnBesideStaysInInsertMode() throws Exception {
        withPanel(panel -> {
            insertIn(panel, spoken);

            panel.moveHorizontally(false);

            assertTrue(third.isSelected());
            assertEquals(sheet.getEditor().orElseThrow().getCell(), third);
            assertTrue(sheet.inInsertMode());
        });
    }

    /** With no column selected, right goes to the first annotation column, from the playhead. */
    @Test
    public void withNoColumnRightGoesToTheFirst() throws Exception {
        withPanel(panel -> {
            owner.set(sheet);
            spreadsheet.getVideoController().setCurrentTime(1_900);

            panel.moveHorizontally(true);

            assertTrue(second.isSelected());
            assertTrue(gaze.isSelected());
        });
    }

    /** Going to a column with fewer codes keeps the caret in a value, the last it has, not on a separator. */
    @Test
    public void aColumnWithFewerValuesTakesTheCaretInItsLast() throws Exception {
        withPanel(panel -> {
            gaze.addCode();
            insertIn(panel, third);
            EditorTracker from = sheet.getEditor().orElseThrow().getDataView().getEdTracker();
            from.setEditor(from.getEditorAtArgIndex(1));

            panel.moveHorizontally(true);

            EditorTracker to = sheet.getEditor().orElseThrow().getDataView().getEdTracker();
            assertEquals(sheet.getEditor().orElseThrow().getCell(), spoken);
            assertEquals(to.editableIndexOfCurrentEditor(), 0, "speech's one value");
        });
    }

    /** With a menu open, ↑ and ↓ are the menu's. */
    @Test
    public void arrowsWithAMenuOpenAreTheMenus() throws Exception {
        withPanel(panel -> {
            column(panel, gaze).selectInCommandMode(first);
            javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(
                new javax.swing.MenuElement[] {new javax.swing.JPopupMenu()});
            try {
                assertFalse(panel.takeKey(press(KeyEvent.VK_DOWN, 0)));
                assertTrue(first.isSelected());
            } finally {
                javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
            }
        });
    }

    /** With several columns selected and no cell, navigation goes from the column selected last. */
    @Test
    public void withSeveralColumnsItGoesFromTheOneSelectedLast() throws Exception {
        withPanel(panel -> {
            owner.set(sheet);
            gaze.setSelected(true);
            speech.setSelected(true);

            panel.moveVertically(true);

            assertTrue(spoken.isSelected(), "down speech, selected last");
        });
    }
}
