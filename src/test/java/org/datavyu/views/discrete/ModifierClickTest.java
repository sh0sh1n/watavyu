package org.datavyu.views.discrete;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.datavyu.TestSpreadsheets;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
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
    private SpreadsheetCell cell;
    private SpreadsheetColumn header;

    @BeforeMethod
    public void setUp() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Cell model = dataStore.createVariable("gaze").createPointCell(1_000);
        cells = mock(CellSelectionListener.class);
        columns = mock(ColumnSelectionListener.class);
        SwingUtilities.invokeAndWait(() -> {
            try {
                SpreadsheetColumn column = mock(SpreadsheetColumn.class);
                when(column.getProjectController()).thenReturn(TestSpreadsheets.withPlayback(dataStore));
                cell = new SpreadsheetCell(column, model, cells);
                header = new SpreadsheetColumn(TestSpreadsheets.withPlayback(dataStore),
                    dataStore.createVariable("speech"), cells, columns, mock(ColumnVisibilityListener.class));
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
        SwingUtilities.invokeAndWait(() ->
            cell.mousePressed(mouse(cell, MouseEvent.MOUSE_PRESSED, InputEvent.META_DOWN_MASK)));

        verify(cells, never()).clearCellSelection();
        verify(cells).addCellToSelection(cell);
    }

    @Test
    public void aShiftClickAddsTheCellsBetween() throws Exception {
        SwingUtilities.invokeAndWait(() ->
            cell.mousePressed(mouse(cell, MouseEvent.MOUSE_PRESSED, InputEvent.SHIFT_DOWN_MASK)));

        verify(cells, never()).clearCellSelection();
        verify(cells).addCellToContinousSelection(cell);
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
