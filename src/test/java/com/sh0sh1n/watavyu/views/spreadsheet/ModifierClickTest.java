package com.sh0sh1n.watavyu.views.spreadsheet;

import static org.testng.Assert.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sh0sh1n.watavyu.TestSpreadsheets;
import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;

/**
 * A click selects a cell or a column alone; with ⌘ it adds to the selection, and with ⇧ it adds the cells between, or
 * the column, to the selection. A right-click leaves the selection alone.
 */
public class ModifierClickTest {

    private CellSelectionListener cells;
    private ColumnSelectionListener columns;
    private Cell model;
    private SpreadsheetCell cell;
    private SpreadsheetColumn header;

    @BeforeMethod
    public void setUp() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Column gaze = dataStore.createColumn("gaze");
        model = gaze.createPointCell(1_000);
        cells = mock(CellSelectionListener.class);
        columns = mock(ColumnSelectionListener.class);
        SwingUtilities.invokeAndWait(() -> {
            try {
                ProjectController spreadsheet = TestSpreadsheets.withPlayback(dataStore);
                SpreadsheetView sheet = new SpreadsheetView(spreadsheet);
                sheet.setFocus(() -> null, component -> { });
                SpreadsheetColumn column = new SpreadsheetColumn(spreadsheet, gaze, sheet, cells, columns,
                    mock(ColumnVisibilityListener.class));
                sheet.addColumn(column);
                cell = sheet.edit(model);
                header = new SpreadsheetColumn(spreadsheet, dataStore.createColumn("speech"),
                    sheet, cells, columns, mock(ColumnVisibilityListener.class));
                // Adding a column sets its selection; the tests follow the clicks after.
                clearInvocations(cells, columns);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static MouseEvent mouse(final Component on, final int id, final int modifiers) {
        return new MouseEvent(on, id, 0, InputEvent.BUTTON1_DOWN_MASK | modifiers, 5, 5, 1, false, MouseEvent.BUTTON1);
    }

    @Test
    public void aClickSelectsACellAlone() throws Exception {
        SwingUtilities.invokeAndWait(() -> cell.mousePressed(mouse(cell, MouseEvent.MOUSE_PRESSED, 0)));

        verify(cells).clearCellSelection();
    }

    @Test
    public void aCommandClickAddsACellToTheSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            model.setSelected(false);
            cell.mousePressed(mouse(cell, MouseEvent.MOUSE_PRESSED, InputEvent.META_DOWN_MASK));
        });

        verify(cells, never()).clearCellSelection();
        verify(cells).addCellToSelection(model);
        assertTrue(model.isSelected());
        assertTrue(model.getColumn().isSelected(), "with its column");
    }

    /** A ⌘-click on a selected cell takes it out of the selection. */
    @Test
    public void aCommandClickOnASelectedCellDeselectsIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            model.setSelected(true);
            cell.mousePressed(mouse(cell, MouseEvent.MOUSE_PRESSED, InputEvent.META_DOWN_MASK));
        });

        verify(cells, never()).clearCellSelection();
        verify(cells, never()).addCellToSelection(model);
        verify(cells).removeCellFromSelection(model);
    }

    @Test
    public void aShiftClickAddsTheCellsBetween() throws Exception {
        SwingUtilities.invokeAndWait(() ->
            cell.mousePressed(mouse(cell, MouseEvent.MOUSE_PRESSED, InputEvent.SHIFT_DOWN_MASK)));

        verify(cells, never()).clearCellSelection();
        verify(cells).addCellToContinousSelection(model);
    }

    /** A right-click, or a ⌃-click, which macOS takes for one, leaves the selection alone, as on a column's header. */
    @Test
    public void aRightClickLeavesTheCellSelectionAlone() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            cell.mousePressed(new MouseEvent(cell, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON3_DOWN_MASK, 5, 5, 1,
                true, MouseEvent.BUTTON3));
            cell.mousePressed(mouse(cell, MouseEvent.MOUSE_PRESSED, InputEvent.CTRL_DOWN_MASK));
        });

        verifyNoInteractions(cells);
    }

    @Test
    public void aClickSelectsAColumnAlone() throws Exception {
        SwingUtilities.invokeAndWait(() -> header.mouseClicked(mouse(header, MouseEvent.MOUSE_CLICKED, 0)));

        verify(columns).clearColumnSelection();
        verify(columns).addColumnToSelection(header);
    }

    @Test
    public void aCommandOrShiftClickAddsAColumnToTheSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            header.mouseClicked(mouse(header, MouseEvent.MOUSE_CLICKED, InputEvent.META_DOWN_MASK));
            header.mouseClicked(mouse(header, MouseEvent.MOUSE_CLICKED, InputEvent.SHIFT_DOWN_MASK));
        });

        verify(columns, never()).clearColumnSelection();
    }

    @Test
    public void aRightClickLeavesTheColumnSelectionAlone() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            header.mouseClicked(new MouseEvent(header, MouseEvent.MOUSE_CLICKED, 0, InputEvent.BUTTON3_DOWN_MASK, 5, 5,
                1, true, MouseEvent.BUTTON3));
            header.mouseClicked(mouse(header, MouseEvent.MOUSE_CLICKED, InputEvent.CTRL_DOWN_MASK));
        });

        verifyNoInteractions(columns);
    }
}
