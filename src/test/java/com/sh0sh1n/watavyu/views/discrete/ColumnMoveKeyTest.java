package com.sh0sh1n.watavyu.views.discrete;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.TestSpreadsheets;
import com.sh0sh1n.watavyu.controllers.project.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.UserWarningException;
import com.sh0sh1n.watavyu.models.Column;
import org.testng.annotations.Test;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 * Select Column to Left and Right (⌘←, ⌘→) select the shown column beside the selected one, coming round from the end;
 * with none selected, the last or the first.
 */
public class ColumnMoveKeyTest {

    private static void layOut(final Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container inner) {
                layOut(inner);
            }
        }
    }

    /** Something done to a spreadsheet's view, laid out as it is once shown, that may throw. */
    private interface SheetStep {
        void run(ProjectController spreadsheet, SpreadSheetPanel panel) throws UserWarningException;
    }

    private static void onSheet(final SheetStep step) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ProjectController spreadsheet = TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore());
            SpreadSheetPanel panel = new SpreadSheetPanel(spreadsheet, null);
            spreadsheet.setSpreadSheetPanel(panel);
            panel.registerListeners();
            try {
                // Selecting a column scrolls to it, in a sheet laid out as it is once shown.
                panel.setSize(800, 600);
                layOut(panel);
                step.run(spreadsheet, panel);
            } catch (UserWarningException e) {
                throw new IllegalStateException(e);
            } finally {
                panel.close();
            }
        });
    }

    @Test
    public void selectingTheNextColumnComesRoundAndSkipsHiddenColumns() throws Exception {
        List<String> selected = new ArrayList<>();
        onSheet((spreadsheet, panel) -> {
            // With no column shown, there is none to select.
            panel.selectNextColumn(1);
            selected.add(String.valueOf(panel.getSelectedColumn()));

            spreadsheet.columns().newColumn("gaze");
            Column speech = spreadsheet.columns().newColumn("speech");
            spreadsheet.columns().newColumn("gesture");
            spreadsheet.columns().hide(List.of(speech));
            spreadsheet.getDataStore().deselectAll();
            layOut(panel);

            for (int direction : new int[] {1, 1, 1}) {
                panel.selectNextColumn(direction);
                selected.add(panel.getSelectedColumn().getColumn().getName());
            }
            spreadsheet.getDataStore().deselectAll();
            panel.selectNextColumn(-1);
            selected.add(panel.getSelectedColumn().getColumn().getName());
        });

        assertEquals(selected, List.of("null", "gaze", "gesture", "gaze", "gesture"));
    }

    /** With no column selected, the first or last is selected, and the cells selected stay so. */
    @Test
    public void selectingAColumnWithNoneSelectedLeavesTheCells() throws Exception {
        AtomicReference<Cell> cell = new AtomicReference<>();
        onSheet((spreadsheet, panel) -> {
            spreadsheet.columns().newColumn("gaze");
            Column gesture = spreadsheet.columns().newColumn("gesture");
            cell.set(gesture.createPointCell(1_000));
            spreadsheet.getDataStore().deselectAll();
            cell.get().setSelected(true);
            layOut(panel);

            panel.selectNextColumn(1);

            assertEquals(panel.getSelectedColumn().getColumn().getName(), "gaze");
        });

        assertTrue(cell.get().isSelected());
    }
}
