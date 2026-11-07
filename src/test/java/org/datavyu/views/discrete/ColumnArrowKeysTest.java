package org.datavyu.views.discrete;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.Variable;
import org.datavyu.models.project.Project;
import org.datavyu.util.ClockTimer;
import org.datavyu.views.VideoController;
import org.testng.annotations.Test;

import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** ↑ and ↓ move the selection to the cell before or after the selected one in time, in the same column. */
public class ColumnArrowKeysTest {

    private Variable notes;
    private Cell first;
    private Cell second;
    private Cell third;

    /** Builds a column of three cells, in time order, and runs the test with its cells' panel on the EDT. */
    private void withColumn(final Consumer<ColumnDataPanel> test) throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        notes = dataStore.createVariable("notes");
        // Made out of time order, so the keys follow time rather than the order cells were made.
        third = notes.createPointCell(3_000);
        first = notes.createPointCell(1_000);
        second = notes.createPointCell(2_000);
        // A new cell starts selected.
        for (Cell cell : notes.getCells()) {
            cell.setSelected(false);
        }
        VideoController playback = mock(VideoController.class);
        when(playback.getClockTimer()).thenReturn(mock(ClockTimer.class));
        ProjectController spreadsheet = new ProjectController(new Project(), dataStore);
        spreadsheet.setVideoController(playback);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                SpreadsheetColumn column = new SpreadsheetColumn(spreadsheet, notes, mock(CellSelectionListener.class),
                    mock(ColumnSelectionListener.class), mock(ColumnVisibilityListener.class));
                try {
                    test.accept(column.getDataPanel());
                } finally {
                    column.getDataPanel().deregisterListeners();
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static KeyEvent press(final ColumnDataPanel panel, final int keyCode) {
        return new KeyEvent(panel, KeyEvent.KEY_PRESSED, 0, 0, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    private static SpreadsheetCell view(final ColumnDataPanel panel, final Cell cell) {
        return panel.getCellsTemporally().stream().filter(view -> view.getCell().equals(cell)).findFirst()
            .orElseThrow();
    }

    @Test
    public void upSelectsTheCellBeforeInTime() throws Exception {
        withColumn(panel -> {
            second.setSelected(true);
            KeyEvent up = press(panel, KeyEvent.VK_UP);
            assertTrue(panel.dispatchKeyEvent(up));
            assertTrue(up.isConsumed());
            assertTrue(first.isSelected());
            assertTrue(first.isHighlighted());
            assertFalse(second.isSelected());
            assertFalse(second.isHighlighted());
        });
    }

    @Test
    public void downSelectsTheCellAfterInTime() throws Exception {
        withColumn(panel -> {
            second.setSelected(true);
            assertTrue(panel.dispatchKeyEvent(press(panel, KeyEvent.VK_DOWN)));
            assertTrue(third.isSelected());
            assertFalse(second.isSelected());
        });
    }

    @Test
    public void theCaretStaysInTheSameValue() throws Exception {
        withColumn(panel -> {
            second.setSelected(true);
            EditorTracker tracker = view(panel, second).getDataView().getEdTracker();
            tracker.setEditor(tracker.getEditorAtIndex(1));
            panel.dispatchKeyEvent(press(panel, KeyEvent.VK_DOWN));
            assertEquals(view(panel, third).getDataView().getEdTracker().indexOfCurrentEditor(), 1);
        });
    }

    @Test
    public void theFirstAndLastCellsGoNoFurther() throws Exception {
        withColumn(panel -> {
            first.setSelected(true);
            KeyEvent up = press(panel, KeyEvent.VK_UP);
            assertFalse(panel.dispatchKeyEvent(up));
            assertFalse(up.isConsumed());
            assertTrue(first.isSelected());

            first.setSelected(false);
            third.setSelected(true);
            assertFalse(panel.dispatchKeyEvent(press(panel, KeyEvent.VK_DOWN)));
            assertTrue(third.isSelected());
        });
    }

    @Test
    public void otherKeysReleasesAndNoSelectionAreLeftAlone() throws Exception {
        withColumn(panel -> {
            assertFalse(panel.dispatchKeyEvent(press(panel, KeyEvent.VK_UP)));

            second.setSelected(true);
            assertFalse(panel.dispatchKeyEvent(press(panel, KeyEvent.VK_LEFT)));
            assertFalse(panel.dispatchKeyEvent(
                new KeyEvent(panel, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED)));
            assertTrue(second.isSelected());
        });
    }
}
