package org.datavyu.views.discrete;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.TestSpreadsheets;
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.Variable;
import org.testng.annotations.Test;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Every open spreadsheet listens for keys, but only the one on screen, in the selected tab, takes them; closing a
 * spreadsheet stops its clock.
 */
public class SpreadSheetPanelTest {

    @Test
    public void aSpreadsheetNotOnScreenTakesNoKeys() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Variable notes = dataStore.createVariable("notes");
        AtomicReference<SpreadSheetPanel> panel = new AtomicReference<>();
        AtomicBoolean passedOn = new AtomicBoolean();
        KeyboardFocusManager focus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        KeyEventDispatcher probe = event -> {
            passedOn.set(true);
            return false;
        };
        SwingUtilities.invokeAndWait(() -> {
            panel.set(new SpreadSheetPanel(TestSpreadsheets.withPlayback(dataStore), null));
            panel.get().registerListeners();
            focus.addKeyEventDispatcher(probe);
            // ⌘→ selects the next column in the spreadsheet on screen.
            focus.dispatchEvent(new KeyEvent(new JTextField(), KeyEvent.KEY_PRESSED, 0,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx(), KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED));
        });
        try {
            assertTrue(passedOn.get(), "the key reached the dispatchers");
            assertFalse(notes.isSelected());
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                focus.removeKeyEventDispatcher(probe);
                panel.get().close();
            });
        }
    }

    @Test
    public void closingASpreadsheetStopsItsClock() throws Exception {
        AtomicReference<ProjectController> spreadsheet = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            spreadsheet.set(TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore()));
            new SpreadSheetPanel(spreadsheet.get(), null).close();
        });

        assertTrue(spreadsheet.get().getVideoController().getClockTimer().isClosed());
    }
}
