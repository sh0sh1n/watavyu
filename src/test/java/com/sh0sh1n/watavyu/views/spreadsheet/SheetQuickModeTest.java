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
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Quick mode (AGENTS.md › Decisions, 2026-11-13): command mode in which a letter or digit makes a point cell at the
 * playhead in the selected annotation column, holding the character. {@code q} enters it and Esc or ⌃[ leave it;
 * going into a cell leaves it too. The playhead strip shows the keyboard's mode. Which keys do this is the key map's.
 */
public class SheetQuickModeTest {

    private Column notes;
    private Cell cell;
    private ProjectController spreadsheet;
    private SpreadsheetView sheet;

    private final AtomicReference<Component> owner = new AtomicReference<>();
    private final List<Component> focused = new ArrayList<>();

    /** Builds a column with one cell, the column selected and the keyboard on the sheet, in command mode. */
    private void withPanel(final Consumer<SpreadSheetPanel> test) throws Exception {
        owner.set(null);
        focused.clear();
        DataStore dataStore = DataStoreFactory.newDataStore();
        notes = dataStore.createColumn("notes");
        if (notes.getCodes().isEmpty()) {
            notes.addCode();
        }
        cell = notes.createPointCell(1_000);
        dataStore.deselectAll();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            spreadsheet = TestSpreadsheets.withPlayback(dataStore);
            SpreadSheetPanel panel = new SpreadSheetPanel(spreadsheet, null);
            spreadsheet.setSpreadSheetPanel(panel);
            try {
                sheet = panel.getColumns().get(0).getSheet();
                sheet.setFocus(owner::get, component -> {
                    focused.add(component);
                    owner.set(component instanceof SpreadsheetCell editor ? editor.getDataView() : component);
                });
                owner.set(sheet);
                notes.setSelected(true);
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

    private boolean key(final SpreadSheetPanel panel, final int code, final char c) {
        return panel.takeKey(new KeyEvent(sheet, KeyEvent.KEY_PRESSED, 0, 0, code, c));
    }

    @Test
    public void inQuickModeALetterMakesACellAndTheKeyboardStays() throws Exception {
        withPanel(panel -> {
            assertFalse(key(panel, KeyEvent.VK_A, 'a'), "in command mode a letter is not a quick key");
            panel.quickMode();
            assertEquals(panel.mode(), SpreadSheetPanel.Mode.QUICK);

            assertTrue(key(panel, KeyEvent.VK_A, 'a'));
            assertEquals(notes.getCells().size(), 2);
            assertTrue(notes.getCells().stream().anyMatch(made -> "a".equals(made.getValues().get(0))));
            assertEquals(focused, List.of(), "the keyboard stays");
            assertEquals(panel.mode(), SpreadSheetPanel.Mode.QUICK);
        });
    }

    @Test
    public void commandModeLeavesQuickMode() throws Exception {
        withPanel(panel -> {
            assertFalse(panel.canGoToCommandMode(), "in command mode, Esc is left alone");
            panel.quickMode();
            assertTrue(panel.canGoToCommandMode());

            panel.commandMode();

            assertEquals(panel.mode(), SpreadSheetPanel.Mode.COMMAND);
            assertFalse(spreadsheet.isQuickKeyMode());
            assertFalse(key(panel, KeyEvent.VK_A, 'a'));
            assertEquals(notes.getCells().size(), 1);
        });
    }

    /** Going into a cell, as by a click, leaves quick mode, so Esc comes back to command mode. */
    @Test
    public void goingIntoACellLeavesQuickMode() throws Exception {
        withPanel(panel -> {
            panel.quickMode();
            cell.setSelected(true);
            owner.set(sheet.edit(cell).getDataView());
            panel.keyboardMoved();

            assertEquals(panel.mode(), SpreadSheetPanel.Mode.INSERT);
            assertFalse(spreadsheet.isQuickKeyMode());
            panel.commandMode();
            assertEquals(panel.mode(), SpreadSheetPanel.Mode.COMMAND);
        });
    }

    /** With the keyboard in a field or another window, letters type there, and the strip shows no mode. */
    @Test
    public void elsewhereQuickKeysType() throws Exception {
        withPanel(panel -> {
            panel.quickMode();
            owner.set(new JTextField());

            assertFalse(key(panel, KeyEvent.VK_A, 'a'));
            assertEquals(notes.getCells().size(), 1);
            assertEquals(panel.mode(), SpreadSheetPanel.Mode.NONE);
            assertFalse(panel.canGoToCommandMode(), "Esc is the field's");
        });
    }

    @Test
    public void thePlayheadStripShowsTheMode() throws Exception {
        withPanel(panel -> {
            assertEquals(panel.getModeMark().getMode(), "COMMAND");
            panel.quickMode();
            assertEquals(panel.getModeMark().getMode(), "QUICK");
            cell.setSelected(true);
            owner.set(sheet.edit(cell).getDataView());
            assertEquals(panel.getModeMark().getMode(), "INSERT");
            owner.set(null);
            assertEquals(panel.getModeMark().getMode(), "", "the keyboard in another app");
        });
    }

    /** Each spreadsheet has its own quick mode, as it has its own insert mode: another tab stays in command mode. */
    @Test
    public void quickModeIsTheSpreadsheets() throws Exception {
        withPanel(panel -> {
            ProjectController other = TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore());
            SpreadSheetPanel otherPanel = new SpreadSheetPanel(other, null);
            try {
                SpreadsheetView otherSheet = otherPanel.getSheet();
                otherSheet.setFocus(() -> otherSheet, component -> { });
                panel.quickMode();
                assertEquals(panel.mode(), SpreadSheetPanel.Mode.QUICK);
                assertFalse(other.isQuickKeyMode());
                assertEquals(otherPanel.mode(), SpreadSheetPanel.Mode.COMMAND);
            } finally {
                otherPanel.close();
            }
        });
    }

    /** With a menu open, keys are the menu's: no mode shows, and Esc closes the menu rather than leaving quick mode. */
    @Test
    public void aMenuOpenTakesTheKeys() throws Exception {
        withPanel(panel -> {
            panel.quickMode();
            javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(
                new javax.swing.MenuElement[] {new javax.swing.JPopupMenu()});
            try {
                assertEquals(panel.mode(), SpreadSheetPanel.Mode.NONE);
                assertFalse(panel.canGoToCommandMode());
                assertFalse(key(panel, KeyEvent.VK_A, 'a'));
            } finally {
                javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
            }
            assertEquals(panel.mode(), SpreadSheetPanel.Mode.QUICK);
        });
    }

    /** Option-letters are quick keys too, as they were. */
    @Test
    public void anOptionLetterMakesACell() throws Exception {
        withPanel(panel -> {
            panel.quickMode();
            assertTrue(panel.takeKey(new KeyEvent(sheet, KeyEvent.KEY_PRESSED, 0, java.awt.event.InputEvent.ALT_DOWN_MASK,
                KeyEvent.VK_A, 'å')));
            assertEquals(notes.getCells().size(), 2);
        });
    }
}
