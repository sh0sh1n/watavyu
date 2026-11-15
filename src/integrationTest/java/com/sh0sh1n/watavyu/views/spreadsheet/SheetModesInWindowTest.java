package com.sh0sh1n.watavyu.views.spreadsheet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.Project;
import com.sh0sh1n.watavyu.views.VideoController;
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
import javax.swing.SwingUtilities;

/**
 * The keyboard's modes in a shown window, with real focus, which the unit tests stand in for: {@code i} goes into the
 * selected cell without typing itself there, though its character comes after the focus has moved, typing goes into
 * the cell, and Esc and ⌃] come back to command mode with the cell still selected. Keys are posted to the event queue
 * as the system would post them (press, character, release), since macOS drops a robot's keys from a process without
 * accessibility access; the focus manager delivers them to the component in focus. Needs an awake display.
 */
public class SheetModesInWindowTest {

    private JFrame frame;
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
        DataStore dataStore = DataStoreFactory.newDataStore();
        notes = dataStore.createColumn("notes");
        if (notes.getCodes().isEmpty()) {
            notes.addCode();
        }
        cell = notes.createPointCell(1_000);
        cell.setSelected(false);
        SwingUtilities.invokeAndWait(() -> {
            ProjectController spreadsheet = new ProjectController(new Project(), dataStore);
            spreadsheet.setVideoController(new VideoController(spreadsheet, null));
            panel = new SpreadSheetPanel(spreadsheet, null);
            spreadsheet.setSpreadSheetPanel(panel);
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(recorder);
            panel.registerListeners();
            frame = new JFrame("modes");
            frame.setContentPane(panel);
            frame.setSize(700, 400);
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
            SpreadsheetColumn column = panel.getColumns().get(0);
            sheet = column.getSheet();
            column.selectInCommandMode(cell);
        });
        await(() -> sheet.inCommandMode(), "command mode");
    }

    @AfterMethod(alwaysRun = true)
    public void close() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(recorder);
            if (panel != null) {
                panel.close();
            }
            if (frame != null) {
                frame.dispose();
            }
        });
    }

    @Test
    public void iGoesIntoTheCellAndEscapeComesBack() throws Exception {
        // Posted together, so the i's character and the x come while the focus is still moving into the cell.
        type(KeyEvent.VK_I, 'i', 0);
        type(KeyEvent.VK_X, 'x', 0);
        await(() -> sheet.inInsertMode(), "insert mode");
        await(() -> "x".equals(cell.getValues().get(0)), "x typed into the cell, without the i");

        type(KeyEvent.VK_ESCAPE, '\u001b', 0);
        await(() -> sheet.inCommandMode(), "command mode");
        assertTrue(cell.isSelected(), "the cell stays selected");
        assertTrue(notes.isSelected(), "its column stays selected");

        type(KeyEvent.VK_X, 'x', 0);
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(cell.getValues().get(0), "x", "in command mode, keys do not type");
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

    /** Tab in command mode leaves the focus on the sheet; it does not slip into the editor, and so into insert mode. */
    @Test
    public void tabStaysInCommandMode() throws Exception {
        type(KeyEvent.VK_TAB, '\t', 0);
        type(KeyEvent.VK_X, 'x', 0);
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        await(() -> sheet.inCommandMode(), "command mode");
        assertEquals(cell.getValues().get(0), "", "nothing typed");
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
