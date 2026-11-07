package com.sh0sh1n.watavyu.views.discrete;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.TestSpreadsheets;
import com.sh0sh1n.watavyu.controllers.project.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.views.discrete.datavalues.CellValuesView;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.awt.event.KeyEvent;
import javax.swing.SwingUtilities;

/**
 * A locked column's header shows a padlock, which shakes when the column refuses a change, and typing in its cells
 * changes nothing.
 */
public class LockedColumnViewTest {

    private Column notes;
    private Cell cell;
    private SpreadsheetColumn header;
    private ProjectController spreadsheet;

    @BeforeMethod
    public void setUp() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        notes = dataStore.createColumn("notes");
        cell = notes.createPointCell(1_000);
        cell.setValue(0, "smile");
        SwingUtilities.invokeAndWait(() -> {
            spreadsheet = TestSpreadsheets.withPlayback(dataStore);
            try {
                header = new SpreadsheetColumn(spreadsheet, notes, mock(CellSelectionListener.class),
                    mock(ColumnSelectionListener.class), mock(ColumnVisibilityListener.class));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            header.registerListeners();
        });
    }

    @AfterMethod
    public void tearDown() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            header.deregisterListeners();
        });
    }

    @Test
    public void theHeaderShowsAPadlockWhileTheColumnIsLocked() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertNull(header.getIcon());
            notes.setLocked(true);
            assertEquals(header.getIcon(), header.getLockMark());
            assertEquals(header.getToolTipText(), "Locked: right-click to unlock");
            notes.setLocked(false);
            assertNull(header.getIcon());
            assertNull(header.getToolTipText());
        });
    }

    @Test
    public void theHeadersPadlockShakesWhenAChangeIsRefused() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            notes.setLocked(true);
            assertFalse(header.getLockMark().isShaking());
            cell.setOnset(5_000);
            assertTrue(header.getLockMark().isShaking());
        });
    }

    @Test
    public void typingInALockedCellChangesNothingAndShakesThePadlock() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CellValuesView view = new CellValuesView(spreadsheet, cell);
            view.getEdTracker().setEditor(view.getEdTracker().firstEditor());
            notes.setLocked(true);

            KeyEvent typed = new KeyEvent(view, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'x');
            view.getEdTracker().keyTyped(typed);
            KeyEvent backspace = new KeyEvent(view, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_BACK_SPACE, '\b');
            view.getEdTracker().keyPressed(backspace);

            assertTrue(typed.isConsumed());
            assertTrue(backspace.isConsumed());
            assertEquals(cell.getValue(0), "smile");
            assertTrue(header.getLockMark().isShaking());
        });
    }
}
