package com.sh0sh1n.watavyu.views.spreadsheet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.Project;
import com.sh0sh1n.watavyu.util.ClockTimer;
import com.sh0sh1n.watavyu.views.VideoController;
import org.testng.annotations.Test;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** ↑ and ↓ move the selection to the cell before or after the selected one in time, in the same column. */
public class ColumnArrowKeysTest {

    private Column notes;
    private Cell first;
    private Cell second;
    private Cell third;

    private SpreadsheetView sheet;

    /** Builds a column of three cells, in time order, and runs the test with its column on the EDT. */
    private void withColumn(final Consumer<SpreadsheetColumn> test) throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        notes = dataStore.createColumn("notes");
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
                sheet = new SpreadsheetView(spreadsheet);
                // Insert mode: the editor's values have the focus, wherever the editor is.
                sheet.setFocus(() -> sheet.getEditor().map(editor -> (Component) editor.getDataView()).orElse(null),
                    component -> { });
                SpreadsheetColumn column = new SpreadsheetColumn(spreadsheet, notes, sheet,
                    mock(CellSelectionListener.class), mock(ColumnSelectionListener.class),
                    mock(ColumnVisibilityListener.class));
                sheet.addColumn(column);
                try {
                    test.accept(column);
                } finally {
                    sheet.close();
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static KeyEvent press(final SpreadsheetColumn column, final int keyCode) {
        return new KeyEvent(column, KeyEvent.KEY_PRESSED, 0, 0, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    @Test
    public void upSelectsTheCellBeforeInTime() throws Exception {
        withColumn(column -> {
            second.setSelected(true);
            sheet.edit(second);
            KeyEvent up = press(column, KeyEvent.VK_UP);
            assertTrue(column.dispatchKeyEvent(up));
            assertTrue(up.isConsumed());
            assertTrue(first.isSelected());
            assertTrue(first.isHighlighted());
            assertFalse(second.isSelected());
            assertFalse(second.isHighlighted());
        });
    }

    @Test
    public void downSelectsTheCellAfterInTime() throws Exception {
        withColumn(column -> {
            second.setSelected(true);
            sheet.edit(second);
            assertTrue(column.dispatchKeyEvent(press(column, KeyEvent.VK_DOWN)));
            assertTrue(third.isSelected());
            assertFalse(second.isSelected());
        });
    }

    @Test
    public void theCaretStaysInTheSameValue() throws Exception {
        withColumn(column -> {
            second.setSelected(true);
            EditorTracker tracker = sheet.edit(second).getDataView().getEdTracker();
            tracker.setEditor(tracker.getEditorAtIndex(1));
            column.dispatchKeyEvent(press(column, KeyEvent.VK_DOWN));
            SpreadsheetCell editor = sheet.getEditor().orElseThrow();
            assertEquals(editor.getCell(), third, "the editor moved to the cell after");
            assertEquals(editor.getDataView().getEdTracker().indexOfCurrentEditor(), 1);
        });
    }

    @Test
    public void theFirstAndLastCellsGoNoFurther() throws Exception {
        withColumn(column -> {
            first.setSelected(true);
            sheet.edit(first);
            KeyEvent up = press(column, KeyEvent.VK_UP);
            assertFalse(column.dispatchKeyEvent(up));
            assertFalse(up.isConsumed());
            assertTrue(first.isSelected());

            first.setSelected(false);
            third.setSelected(true);
            sheet.edit(third);
            assertFalse(column.dispatchKeyEvent(press(column, KeyEvent.VK_DOWN)));
            assertTrue(third.isSelected());
        });
    }

    @Test
    public void otherKeysReleasesAndNoSelectionAreLeftAlone() throws Exception {
        withColumn(column -> {
            assertFalse(column.dispatchKeyEvent(press(column, KeyEvent.VK_UP)));

            second.setSelected(true);
            assertFalse(column.dispatchKeyEvent(press(column, KeyEvent.VK_LEFT)));
            assertFalse(column.dispatchKeyEvent(
                new KeyEvent(column, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED)));
            assertTrue(second.isSelected());
        });
    }
}
