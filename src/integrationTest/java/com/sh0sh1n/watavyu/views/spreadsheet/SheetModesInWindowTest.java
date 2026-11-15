package com.sh0sh1n.watavyu.views.spreadsheet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.TestSpreadsheets;
import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.views.MainWindow;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/**
 * The key map in a shown main window, with real focus, which the unit tests stand in for: {@code i} goes into the
 * selected cell without typing itself there, though its character comes after the focus has moved, typing goes into
 * the cell, and Esc and ⌃] come back to command mode with the cell still selected; in command mode {@code o}, {@code x}
 * and {@code u} make, delete and bring back cells. Keys are posted to the event queue as the system would post them
 * (press, character, release), since macOS drops a robot's keys from a process without accessibility access; the focus
 * manager delivers them to the component in focus. Needs an awake display and an unlocked screen.
 */
public class SheetModesInWindowTest {

    private JFrame frame;
    private MainWindow window;
    private ProjectController spreadsheet;
    private SpreadSheetPanel panel;
    private SpreadsheetView sheet;
    private Column notes;
    private Cell cell;

    /** Every key event the window saw, for a failure to report. */
    private final java.util.List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final java.awt.KeyEventDispatcher recorder = e -> {
        seen.add(e.paramString() + " to " + e.getComponent().getClass().getSimpleName());
        return false;
    };

    @BeforeMethod
    public void showASheet() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(recorder);
            frame = new JFrame("modes");
            window = TestSpreadsheets.mainWindow(frame);
            spreadsheet = window.selectedController();
            panel = (SpreadSheetPanel) window.getTabbedPane().getSelectedComponent();
            try {
                notes = spreadsheet.getDataStore().createColumn("notes");
            } catch (com.sh0sh1n.watavyu.models.UserWarningException e) {
                throw new IllegalStateException(e);
            }
            if (notes.getCodes().isEmpty()) {
                notes.addCode();
            }
            cell = notes.createPointCell(1_000);
            cell.setSelected(false);
            frame.setSize(900, 500);
            frame.setLocation(100, 100);
            frame.setVisible(true);
            // Another app in front would keep the window from the focus.
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.APP_REQUEST_FOREGROUND)) {
                java.awt.Desktop.getDesktop().requestForeground(true);
            }
            frame.toFront();
        });
        await(() -> frame.isFocused(), "the window has the focus");
        SwingUtilities.invokeAndWait(() -> {
            SpreadsheetColumn column = panel.getColumns().stream()
                .filter(view -> view.getColumn().equals(notes)).findFirst().orElseThrow();
            sheet = column.getSheet();
            column.selectInCommandMode(cell);
        });
        await(() -> sheet.inCommandMode(), "command mode");
    }

    @AfterMethod(alwaysRun = true)
    public void close() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(recorder);
            if (window != null) {
                JTabbedPane tabs = window.getTabbedPane();
                while (tabs.getTabCount() > 0) {
                    SpreadSheetPanel tab = (SpreadSheetPanel) tabs.getComponentAt(0);
                    tabs.remove(tab);
                    tab.close();
                }
            }
            if (frame != null) {
                frame.dispose();
            }
        });
    }

    @Test
    public void iGoesIntoTheCellAndEscapeComesBack() throws Exception {
        // Posted together, so the i's character and the z come while the focus is still moving into the cell.
        type(KeyEvent.VK_I, 'i', 0);
        type(KeyEvent.VK_Z, 'z', 0);
        await(() -> sheet.inInsertMode(), "insert mode");
        await(() -> "z".equals(cell.getValues().get(0)), "z typed into the cell, without the i");

        type(KeyEvent.VK_ESCAPE, '\u001b', 0);
        await(() -> sheet.inCommandMode(), "command mode");
        assertTrue(cell.isSelected(), "the cell stays selected");
        assertTrue(notes.isSelected(), "its column stays selected");

        type(KeyEvent.VK_Z, 'z', 0);
        settle();
        assertEquals(cell.getValues().get(0), "z", "in command mode, keys do not type");
    }

    @Test
    public void controlCloseBracketComesBackToo() throws Exception {
        type(KeyEvent.VK_I, 'i', 0);
        await(() -> sheet.inInsertMode(), "insert mode");

        type(KeyEvent.VK_CLOSE_BRACKET, '\u001d', InputEvent.CTRL_DOWN_MASK);
        await(() -> sheet.inCommandMode(), "command mode");
        assertTrue(cell.isSelected());
        assertEquals(cell.getValues().get(0), "", "nothing typed");
    }

    /** In insert mode the command keys type, as x does here, rather than deleting the cell. */
    @Test
    public void inInsertModeCommandKeysType() throws Exception {
        type(KeyEvent.VK_I, 'i', 0);
        type(KeyEvent.VK_X, 'x', 0);
        type(KeyEvent.VK_O, 'o', 0);
        await(() -> "xo".equals(cell.getValues().get(0)), "x and o typed");
        assertEquals(notes.getCells().size(), 1);
    }

    /** o makes a cell at the playhead and goes into it; Esc, x deletes it, and u brings it back. */
    @Test
    public void oMakesACellXDeletesItAndUBringsItBack() throws Exception {
        SwingUtilities.invokeAndWait(() -> spreadsheet.getVideoController().setCurrentTime(4_000));
        type(KeyEvent.VK_O, 'o', 0);
        type(KeyEvent.VK_A, 'a', 0);
        await(() -> notes.getCells().size() == 2, "a new cell");
        Cell made = spreadsheet.getLastCreatedCell();
        assertEquals(made.getOnset(), 4_000);
        await(() -> "a".equals(made.getValues().get(0)), "typing in the new cell");

        type(KeyEvent.VK_ESCAPE, '\u001b', 0);
        await(() -> sheet.inCommandMode(), "command mode");
        type(KeyEvent.VK_X, 'x', 0);
        await(() -> notes.getCells().size() == 1, "the cell deleted");
        assertTrue(notes.isSelected(), "its column stays selected");
        assertTrue(sheet.inCommandMode());

        type(KeyEvent.VK_U, 'u', 0);
        await(() -> notes.getCells().size() == 2, "the cell back");
    }

    /** q goes to quick mode, where letters make cells and Space still plays; Esc comes back to command mode. */
    @Test
    public void qGoesToQuickModeAndEscapeComesBack() throws Exception {
        type(KeyEvent.VK_Q, 'q', 0);
        await(() -> panel.mode() == SpreadSheetPanel.Mode.QUICK, "quick mode");
        assertEquals(panel.getPlayhead().getMode(), "QUICK");
        type(KeyEvent.VK_X, 'x', 0);
        await(() -> notes.getCells().size() == 2, "x makes a cell rather than deleting one");
        assertTrue(notes.getCells().stream().anyMatch(made -> "x".equals(made.getValues().get(0))));
        assertTrue(sheet.inCommandMode(), "the keyboard stays on the sheet");

        type(KeyEvent.VK_ESCAPE, '\u001b', 0);
        await(() -> panel.mode() == SpreadSheetPanel.Mode.COMMAND, "command mode");
        type(KeyEvent.VK_Z, 'z', 0);
        settle();
        assertEquals(notes.getCells().size(), 2, "letters are commands again");
    }

    /** Tab in command mode leaves the focus on the sheet; it does not slip into the editor, and so into insert mode. */
    @Test
    public void tabStaysInCommandMode() throws Exception {
        type(KeyEvent.VK_TAB, '\t', 0);
        type(KeyEvent.VK_Z, 'z', 0);
        settle();
        await(() -> sheet.inCommandMode(), "command mode");
        assertEquals(cell.getValues().get(0), "", "nothing typed");
    }

    /** Space plays and pauses in command mode. */
    @Test
    public void spacePlaysAndPauses() throws Exception {
        type(KeyEvent.VK_SPACE, ' ', 0);
        await(() -> !spreadsheet.getVideoController().getClockTimer().isPaused(), "playing");
        type(KeyEvent.VK_SPACE, ' ', 0);
        await(() -> spreadsheet.getVideoController().getClockTimer().isPaused(), "paused");
        assertFalse(sheet.inInsertMode());
    }

    /** When the last key event posted happened; each comes later, as typing does. */
    private long lastWhen = System.currentTimeMillis();

    /**
     * Posts a key's press, character and release, as the system does, for the focus manager to deliver. Each event
     * is stamped later than the last, as the focus manager holds back keys stamped after a focus change it is making.
     */
    private void type(final int key, final char character, final int modifiers) {
        java.awt.EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        lastWhen = Math.max(lastWhen + 1, System.currentTimeMillis());
        queue.postEvent(new KeyEvent(frame, KeyEvent.KEY_PRESSED, lastWhen, modifiers, key, character));
        queue.postEvent(new KeyEvent(frame, KeyEvent.KEY_TYPED, ++lastWhen, modifiers, KeyEvent.VK_UNDEFINED,
            character));
        queue.postEvent(new KeyEvent(frame, KeyEvent.KEY_RELEASED, ++lastWhen, modifiers, key, character));
    }

    /** Lets the keys posted so far be delivered. */
    private static void settle() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
    }

    private void await(final Callable<Boolean> condition, final String what) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        AtomicReference<Boolean> met = new AtomicReference<>(false);
        do {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    met.set(condition.call());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            if (met.get()) {
                return;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Timed out waiting for " + what + "; keys seen: " + seen);
    }
}
