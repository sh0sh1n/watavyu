package com.sh0sh1n.watavyu.views.spreadsheet;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.Project;
import com.sh0sh1n.watavyu.util.ClockTimer;
import com.sh0sh1n.watavyu.util.ClockTimer.ClockListener;
import com.sh0sh1n.watavyu.views.VideoController;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;

import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The sheet follows the clock once for all its cells, rather than each cell following it; a column and the editor over
 * the cell in focus follow it too. Each stops once it is gone.
 */
public class ColumnClockTest {

    @Test
    public void theSheetColumnsAndEditorStopFollowingTheClockOnceGone() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Column notes = dataStore.createColumn("notes");
        Cell deleted = notes.createPointCell(1_000);
        notes.createPointCell(2_000);
        ClockTimer clock = mock(ClockTimer.class);
        VideoController playback = mock(VideoController.class);
        when(playback.getClockTimer()).thenReturn(clock);
        ProjectController spreadsheet = new ProjectController(new Project(), dataStore);
        spreadsheet.setVideoController(playback);

        SwingUtilities.invokeAndWait(() -> {
            SpreadsheetView sheet = new SpreadsheetView(spreadsheet);
            SpreadsheetColumn column = new SpreadsheetColumn(spreadsheet, notes, sheet,
                mock(CellSelectionListener.class), mock(ColumnSelectionListener.class),
                mock(ColumnVisibilityListener.class));
            sheet.addColumn(column);
            column.registerListeners();
            SpreadsheetCell editor = sheet.edit(deleted);

            ArgumentCaptor<ClockListener> registered = ArgumentCaptor.forClass(ClockListener.class);
            verify(clock, atLeastOnce()).registerListener(registered.capture());
            List<ClockListener> listeners = registered.getAllValues();
            assertEquals(listeners.size(), 3, "the sheet, the column and the editor: " + listeners);
            assertTrue(listeners.contains(column));
            assertTrue(listeners.contains(editor));

            notes.removeCell(deleted);
            verify(clock).unRegisterListener(editor);

            column.deregisterListeners();
            verify(clock).unRegisterListener(column);

            sheet.close();
            ClockListener sheetsOwn = listeners.stream().filter(listener -> listener != column && listener != editor)
                .findFirst().orElseThrow();
            verify(clock).unRegisterListener(sheetsOwn);
        });
    }
}
