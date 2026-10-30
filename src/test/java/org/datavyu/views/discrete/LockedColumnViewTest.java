package org.datavyu.views.discrete;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.datavyu.Datavyu;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.Variable;
import org.datavyu.views.VideoController;
import org.datavyu.views.discrete.datavalues.MatrixRootView;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.SwingUtilities;
import java.awt.event.KeyEvent;

/**
 * A locked column's header shows a padlock, which shakes when the column refuses a change, and typing in its cells
 * changes nothing.
 */
public class LockedColumnViewTest {

    private VideoController previousController;
    private Variable notes;
    private Cell cell;
    private SpreadsheetColumn header;

    @BeforeMethod
    public void setUp() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        notes = dataStore.createVariable("notes");
        cell = notes.createPointCell(1_000);
        cell.setMatrixValue(0, "smile");
        SwingUtilities.invokeAndWait(() -> {
            previousController = Datavyu.getVideoController();
            Datavyu.setVideoController(new VideoController());
            try {
                header = new SpreadsheetColumn(dataStore, notes, mock(CellSelectionListener.class),
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
            Datavyu.setVideoController(previousController);
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
            MatrixRootView view = new MatrixRootView(cell, cell.getCellValue());
            view.getEdTracker().setEditor(view.getEdTracker().firstEditor());
            notes.setLocked(true);

            KeyEvent typed = new KeyEvent(view, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'x');
            view.getEdTracker().keyTyped(typed);
            KeyEvent backspace = new KeyEvent(view, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_BACK_SPACE, '\b');
            view.getEdTracker().keyPressed(backspace);

            assertTrue(typed.isConsumed());
            assertTrue(backspace.isConsumed());
            assertEquals(cell.getMatrixValue(0).toString(), "smile");
            assertTrue(header.getLockMark().isShaking());
        });
    }
}
