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
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * The keyboard's modes with the spreadsheet's own selection listeners, where {@link SheetModesTest} has stand-ins:
 * command mode holds a cell, or a column with no cell, and leaving it by a click selects one column.
 */
public class SheetModesInPanelTest {

    private Column left;
    private Column right;
    private Cell leftCell;
    private Cell rightCell;
    private SpreadsheetView sheet;

    /** The component the test says has the focus. */
    private final AtomicReference<Component> owner = new AtomicReference<>();

    /** The components the sheet gave the focus, in turn. */
    private final List<Component> focused = new ArrayList<>();

    /** Builds a spreadsheet of two columns with a cell each, nothing selected, and runs the test with it on the EDT. */
    private void withPanel(final Consumer<SpreadSheetPanel> test) throws Exception {
        owner.set(null);
        focused.clear();
        DataStore dataStore = DataStoreFactory.newDataStore();
        left = dataStore.createColumn("left");
        right = dataStore.createColumn("right");
        leftCell = left.createPointCell(1_000);
        rightCell = right.createPointCell(1_000);
        for (Column column : List.of(left, right)) {
            column.setSelected(false);
            column.getCells().forEach(cell -> cell.setSelected(false));
        }
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            ProjectController spreadsheet = TestSpreadsheets.withPlayback(dataStore);
            SpreadSheetPanel panel = new SpreadSheetPanel(spreadsheet, null);
            spreadsheet.setSpreadSheetPanel(panel);
            panel.setSize(700, 400);
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

    /** Lays out a component tree that is never shown, as a window would. */
    private static void layOut(final Component component) {
        if (component instanceof java.awt.Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layOut(child);
            }
        }
    }

    /** From command mode in one column, a click on a value in another's cell selects that column alone. */
    @Test
    public void aClickOnAValueInAnotherColumnSelectsItAlone() throws Exception {
        withPanel(panel -> {
            panel.getColumns().get(0).selectInCommandMode(leftCell);
            assertTrue(left.isSelected());

            SpreadsheetCell editor = sheet.edit(rightCell);
            Component values = editor.getDataView();
            editor.mousePressed(new MouseEvent(values, MouseEvent.MOUSE_PRESSED, 0, 0, 2, 2, 1, false,
                MouseEvent.BUTTON1));

            assertFalse(left.isSelected(), "the column command mode held is deselected");
            assertFalse(leftCell.isSelected());
            assertTrue(right.isSelected());
        });
    }

    /** A column with no selected cell given the focus, as on selecting it, holds it in command mode. */
    @Test
    public void aColumnWithNoCellGoesToCommandMode() throws Exception {
        withPanel(panel -> {
            SpreadsheetColumn column = panel.getColumns().get(1);
            column.setExclusiveSelected(true);
            column.requestFocus();

            assertEquals(focused, List.of(sheet));
            assertTrue(sheet.inCommandMode());
            assertTrue(right.isSelected());
        });
    }
}
