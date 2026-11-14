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
    private ProjectController spreadsheet;

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
            spreadsheet = TestSpreadsheets.withPlayback(dataStore);
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

    /** New Cell from the drawer or keys makes a cell in the selected column, selected alone, and goes into it. */
    @Test
    public void aNewCellIsSelectedAloneInInsertMode() throws Exception {
        withPanel(panel -> {
            panel.getColumns().get(0).selectInCommandMode(leftCell);
            panel.getColumns().get(1).setExclusiveSelected(true);
            focused.clear();

            spreadsheet.getVideoController().createNewCellAction();

            Cell made = spreadsheet.getLastCreatedCell();
            assertEquals(made.getColumn(), right);
            assertTrue(made.isSelected());
            assertTrue(right.isSelected());
            assertFalse(left.isSelected());
            assertFalse(leftCell.isSelected());
            assertEquals(focused.size(), 1);
            assertEquals(((SpreadsheetCell) focused.get(0)).getCell(), made, "insert mode in the new cell");
            assertTrue(panel.getColumns().get(1).isSelected(), "its header shows it selected");
            assertFalse(panel.getColumns().get(0).isSelected());
        });
    }

    /** Deleting the cell being typed in leaves its column selected with no cell, in command mode. */
    @Test
    public void deletingTheCellTypedInGoesToCommandMode() throws Exception {
        withPanel(panel -> {
            panel.getColumns().get(1).select(rightCell);
            owner.set(sheet.edit(rightCell).getDataView());
            assertTrue(sheet.inInsertMode());

            spreadsheet.cells().deleteSelected();

            assertTrue(right.getCells().isEmpty());
            assertTrue(right.isSelected());
            assertTrue(sheet.inCommandMode());
            assertEquals(focused, List.of(sheet));
        });
    }

    /** A cell coming back into a hidden column, as on undo, leaves the selection alone (owner, B22). */
    @Test
    public void aCellInAHiddenColumnLeavesTheSelectionAlone() throws Exception {
        withPanel(panel -> {
            spreadsheet.columns().hide(List.of(right));
            panel.getColumns().get(0).selectInCommandMode(leftCell);

            right.createPointCell(2_000);

            assertTrue(leftCell.isSelected());
            assertTrue(left.isSelected());
            assertFalse(right.isSelected());
        });
    }

    /** A cell taken out of the selection by a ⌘-click stays out when another is ⌘-clicked in. */
    @Test
    public void aCellCommandClickedOutStaysOut() throws Exception {
        withPanel(panel -> {
            Cell another = left.createPointCell(2_000);
            panel.getColumns().get(0).select(leftCell);
            SpreadsheetCell editor = sheet.edit(leftCell);
            editor.mousePressed(commandClick(editor));
            assertFalse(leftCell.isSelected());

            SpreadsheetCell other = sheet.edit(another);
            other.mousePressed(commandClick(other));

            assertTrue(another.isSelected());
            assertFalse(leftCell.isSelected(), "the cell taken out does not come back");
        });
    }

    /** A click on a value selects its cell alone, though the cell is already highlighted among others selected. */
    @Test
    public void aClickOnAValueSelectsItsCellAlone() throws Exception {
        withPanel(panel -> {
            Cell another = left.createPointCell(2_000);
            panel.getColumns().get(0).select(leftCell);
            another.setHighlighted(true);

            SpreadsheetCell editor = sheet.edit(another);
            editor.mousePressed(new MouseEvent(editor.getDataView(), MouseEvent.MOUSE_PRESSED, 0, 0, 2, 2, 1, false,
                MouseEvent.BUTTON1));

            assertTrue(another.isSelected());
            assertFalse(leftCell.isSelected());
        });
    }

    private static MouseEvent commandClick(final SpreadsheetCell editor) {
        return new MouseEvent(editor, MouseEvent.MOUSE_PRESSED, 0,
            java.awt.event.InputEvent.BUTTON1_DOWN_MASK | java.awt.event.InputEvent.META_DOWN_MASK, 2, 2, 1, false,
            MouseEvent.BUTTON1);
    }

    /** A column's header shows its selection however the data model's changed, as on a command clearing it. */
    @Test
    public void theHeadersFollowTheDataModel() throws Exception {
        withPanel(panel -> {
            right.setSelected(true);
            assertTrue(panel.getColumns().get(1).showsSelected());

            spreadsheet.getDataStore().deselectAll();
            assertFalse(panel.getColumns().get(1).showsSelected());
        });
    }

    /** The column selected last is the one New Cell from the menu goes to, however it was selected. */
    @Test
    public void newCellGoesToTheColumnSelectedLast() throws Exception {
        withPanel(panel -> {
            right.setSelected(true);
            assertEquals(spreadsheet.getLastCreatedColumn(), right);
            left.setSelected(true);
            assertEquals(spreadsheet.getLastCreatedColumn(), left);
        });
    }

    /** A ⌘-click taking out a column's only selected cell deselects the column, when another is selected. */
    @Test
    public void aCommandClickOutOfAColumnDeselectsIt() throws Exception {
        withPanel(panel -> {
            panel.getColumns().get(0).select(leftCell);
            SpreadsheetCell editor = sheet.edit(rightCell);
            editor.mousePressed(commandClick(editor));
            assertTrue(right.isSelected());

            editor.mousePressed(commandClick(editor));

            assertFalse(rightCell.isSelected());
            assertFalse(right.isSelected());
            assertTrue(left.isSelected());
        });
    }

    /** A header made for a column already selected shows it selected. */
    @Test
    public void aHeaderShowsASelectionMadeBeforeIt() throws Exception {
        withPanel(panel -> {
            right.setSelected(true);
            SpreadsheetColumn header = new SpreadsheetColumn(spreadsheet, right, sheet,
                org.mockito.Mockito.mock(CellSelectionListener.class),
                org.mockito.Mockito.mock(ColumnSelectionListener.class),
                org.mockito.Mockito.mock(ColumnVisibilityListener.class));
            assertTrue(header.showsSelected());
        });
    }
}
