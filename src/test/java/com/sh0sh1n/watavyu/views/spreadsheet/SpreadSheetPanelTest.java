package com.sh0sh1n.watavyu.views.spreadsheet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.TestSpreadsheets;
import com.sh0sh1n.watavyu.controllers.project.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.UserWarningException;
import com.sh0sh1n.watavyu.models.Column;
import org.testng.annotations.Test;

import java.awt.Component;
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

    /** In quick-key mode a key typed makes a cell in the selected column, but only in the spreadsheet on screen. */
    @Test
    public void aSpreadsheetNotOnScreenTakesNoKeys() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Column notes = dataStore.createColumn("notes");
        AtomicBoolean taken = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            ProjectController spreadsheet = TestSpreadsheets.withPlayback(dataStore);
            spreadsheet.setQuickKeyMode(() -> true);
            SpreadSheetPanel panel = new SpreadSheetPanel(spreadsheet, null);
            panel.registerListeners();
            notes.setSelected(true);
            try {
                taken.set(panel.dispatchKeyEvent(new KeyEvent(new JTextField(), KeyEvent.KEY_PRESSED, 0, 0,
                    KeyEvent.VK_A, 'a')));
            } finally {
                panel.close();
            }
        });

        assertFalse(taken.get(), "the key was left to the spreadsheet on screen");
        assertTrue(notes.getCells().isEmpty());
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
                Column gaze = spreadsheet.columns().newColumn("gaze");
                spreadsheet.columns().newColumn("speech");
                Column gesture = spreadsheet.columns().newColumn("gesture");

                spreadsheet.columns().move(gesture, gaze);
                afterMove.set(names(panel.getColumns()));

                spreadsheet.columns().delete(List.of(gaze));
                spreadsheet.undo();
                afterUndo.set(names(panel.getColumns()));
                headers.set(Arrays.stream(panel.getColumns().get(0).getParent().getComponents())
                    .filter(SpreadsheetColumn.class::isInstance)
                    .map(header -> ((SpreadsheetColumn) header).getColumn().getName()).toList());
                // Each column's cells sit under its header: the sheet's data panels are in the same order.
                List<Component> sheet = Arrays.asList(panel.getColumns().get(0).getDataPanel().getParent().getComponents());
                dataPanels.set(panel.getColumns().stream()
                    .sorted(Comparator.comparingInt(column -> sheet.indexOf(column.getDataPanel())))
                    .map(column -> column.getColumn().getName()).toList());
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
        return columns.stream().map(column -> column.getColumn().getName()).toList();
    }

    /** A column's cells redraw when its codes change, undo included, as the Code Editor no longer redraws them. */
    @Test
    public void cellsShowTheirColumnsCodesAfterEachChange() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Column gaze = dataStore.createColumn("gaze");
        gaze.addCode();
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
