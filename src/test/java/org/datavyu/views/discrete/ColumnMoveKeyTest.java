package org.datavyu.views.discrete;

import static org.testng.Assert.assertEquals;

import org.datavyu.TestSpreadsheets;
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.db.Variable;
import org.datavyu.views.Command;
import org.datavyu.views.Shortcuts;
import org.testng.annotations.Test;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * B5: plain arrow keys belong to the cell being edited; only ⌘← and ⌘→ select another column, the shown one beside
 * the selected one, coming round from the end.
 */
public class ColumnMoveKeyTest {

    private static final int COMMAND = InputEvent.META_DOWN_MASK;

    /** Returns the command each key runs in the main window, or null for a key that runs none. */
    private static List<Command> commandsOf(final List<KeyStroke> keys) throws Exception {
        List<Command> commands = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame();
            try {
                Shortcuts shortcuts = TestSpreadsheets.mainWindow(frame).getShortcuts();
                for (KeyStroke key : keys) {
                    commands.add(shortcuts.all().stream().filter(shortcut -> key.equals(shortcut.getKey()))
                        .map(Shortcuts.Shortcut::getCommand).findFirst().orElse(null));
                }
            } finally {
                frame.dispose();
            }
        });
        return commands;
    }

    private static void layOut(final Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container inner) {
                layOut(inner);
            }
        }
    }

    @Test
    public void onlyCommandArrowsSelectColumns() throws Exception {
        List<Command> commands = commandsOf(List.of(
            KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, COMMAND),
            KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, COMMAND),
            KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0),
            KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
            KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, COMMAND | InputEvent.SHIFT_DOWN_MASK),
            KeyStroke.getKeyStroke(KeyEvent.VK_UP, COMMAND)));

        assertEquals(commands, Arrays.asList(Command.SELECT_COLUMN_LEFT, Command.SELECT_COLUMN_RIGHT,
            null, null, null, null));
    }

    @Test
    public void selectingTheNextColumnComesRoundAndSkipsHiddenColumns() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        AtomicReference<List<String>> selected = new AtomicReference<>(new ArrayList<>());
        SwingUtilities.invokeAndWait(() -> {
            ProjectController spreadsheet = TestSpreadsheets.withPlayback(dataStore);
            SpreadSheetPanel panel = new SpreadSheetPanel(spreadsheet, null);
            spreadsheet.setSpreadSheetPanel(panel);
            panel.registerListeners();
            try {
                // With no column shown, there is none to select.
                panel.selectNextColumn(1);
                selected.get().add(String.valueOf(panel.getSelectedColumn()));

                spreadsheet.columns().newColumn("gaze");
                Variable speech = spreadsheet.columns().newColumn("speech");
                spreadsheet.columns().newColumn("gesture");
                spreadsheet.columns().hide(List.of(speech));
                dataStore.deselectAll();
                // Selecting a column scrolls to it, in a sheet laid out as it is once shown.
                panel.setSize(800, 600);
                layOut(panel);

                panel.selectNextColumn(1);
                selected.get().add(panel.getSelectedColumn().getVariable().getName());
                panel.selectNextColumn(1);
                selected.get().add(panel.getSelectedColumn().getVariable().getName());
                panel.selectNextColumn(1);
                selected.get().add(panel.getSelectedColumn().getVariable().getName());
                dataStore.deselectAll();
                panel.selectNextColumn(-1);
                selected.get().add(panel.getSelectedColumn().getVariable().getName());
            } catch (UserWarningException e) {
                throw new IllegalStateException(e);
            } finally {
                panel.close();
            }
        });

        assertEquals(selected.get(), List.of("null", "gaze", "gesture", "gaze", "gesture"));
    }
}
