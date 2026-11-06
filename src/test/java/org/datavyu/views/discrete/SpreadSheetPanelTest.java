package org.datavyu.views.discrete;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.TestSpreadsheets;
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.db.Variable;
import org.testng.annotations.Test;

import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
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

    /** The panel shows the columns in the data store's order: after a move, and after undo puts a column back. */
    @Test
    public void theColumnsFollowTheSpreadsheetOrder() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        AtomicReference<List<String>> afterMove = new AtomicReference<>();
        AtomicReference<List<String>> afterUndo = new AtomicReference<>();
        AtomicReference<List<String>> headers = new AtomicReference<>();
        AtomicReference<List<String>> dataPanels = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            ProjectController spreadsheet = TestSpreadsheets.withPlayback(dataStore);
            SpreadSheetPanel panel = new SpreadSheetPanel(spreadsheet, null);
            spreadsheet.setSpreadSheetPanel(panel);
            panel.registerListeners();
            try {
                Variable gaze = spreadsheet.columns().newColumn("gaze");
                spreadsheet.columns().newColumn("speech");
                Variable gesture = spreadsheet.columns().newColumn("gesture");

                spreadsheet.columns().move(gesture, gaze);
                afterMove.set(names(panel.getColumns()));

                spreadsheet.columns().delete(List.of(gaze));
                spreadsheet.undo();
                afterUndo.set(names(panel.getColumns()));
                headers.set(Arrays.stream(panel.getColumns().get(0).getParent().getComponents())
                    .filter(SpreadsheetColumn.class::isInstance)
                    .map(header -> ((SpreadsheetColumn) header).getVariable().getName()).toList());
                // Each column's cells sit under its header: the sheet's data panels are in the same order.
                List<Component> sheet = Arrays.asList(panel.getColumns().get(0).getDataPanel().getParent().getComponents());
                dataPanels.set(panel.getColumns().stream()
                    .sorted(Comparator.comparingInt(column -> sheet.indexOf(column.getDataPanel())))
                    .map(column -> column.getVariable().getName()).toList());
            } catch (UserWarningException e) {
                throw new AssertionError(e);
            } finally {
                panel.close();
            }
        });

        assertEquals(afterMove.get(), List.of("gesture", "gaze", "speech"));
        assertEquals(afterUndo.get(), List.of("gesture", "gaze", "speech"));
        assertEquals(headers.get(), List.of("gesture", "gaze", "speech"));
        assertEquals(dataPanels.get(), List.of("gesture", "gaze", "speech"));
    }

    private static List<String> names(final List<SpreadsheetColumn> columns) {
        return columns.stream().map(column -> column.getVariable().getName()).toList();
    }

    /** A column's cells redraw when its codes change, undo included, as the Code Editor no longer redraws them. */
    @Test
    public void cellsShowTheirColumnsCodesAfterEachChange() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Variable gaze = dataStore.createVariable("gaze");
        gaze.addArgument();
        Cell cell = gaze.createPointCell(0);
        cell.setValue(0, "left");
        AtomicReference<SpreadSheetPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
            () -> panel.set(new SpreadSheetPanel(TestSpreadsheets.withPlayback(dataStore), null)));
        ProjectController spreadsheet = panel.get().getProjectController();
        AtomicReference<List<String>> shown = new AtomicReference<>(new ArrayList<>());
        Runnable look = () -> shown.get().add(
            panel.get().getColumns().get(0).getCells().get(0).getDataView().getText());
        try {
            SwingUtilities.invokeAndWait(() -> {
                look.run();
                spreadsheet.codes().rename(gaze, 1, "side", false);
                look.run();
                spreadsheet.codes().delete(gaze, 0);
                look.run();
                spreadsheet.undo();
                look.run();
            });
            assertEquals(shown.get(), List.of("(left, <code02>)", "(left, <side>)", "(<side>)", "(left, <side>)"));
        } finally {
            SwingUtilities.invokeAndWait(() -> panel.get().close());
        }
    }
}
