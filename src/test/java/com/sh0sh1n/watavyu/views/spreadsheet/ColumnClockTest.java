package com.sh0sh1n.watavyu.views.spreadsheet;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.sh0sh1n.watavyu.controllers.project.ProjectController;
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

/** A column and its cells follow the clock while they are in the spreadsheet, and stop once they are gone. */
public class ColumnClockTest {

    @Test
    public void cellsAndColumnsStopFollowingTheClockOnceGone() throws Exception {
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
            SpreadsheetColumn column;
            try {
                column = new SpreadsheetColumn(spreadsheet, notes, mock(CellSelectionListener.class),
                    mock(ColumnSelectionListener.class), mock(ColumnVisibilityListener.class));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            column.registerListeners();
            ArgumentCaptor<ClockListener> registered = ArgumentCaptor.forClass(ClockListener.class);
            verify(clock, atLeastOnce()).registerListener(registered.capture());
            List<SpreadsheetCell> cells = registered.getAllValues().stream()
                .filter(SpreadsheetCell.class::isInstance).map(SpreadsheetCell.class::cast).toList();
            assertEquals(cells.size(), 2);
            SpreadsheetCell deletedView = cells.stream().filter(cell -> cell.getCell().equals(deleted)).findFirst()
                .orElseThrow();

            notes.removeCell(deleted);
            verify(clock).unRegisterListener(deletedView);

            column.deregisterListeners();
            verify(clock).unRegisterListener(column);
            for (SpreadsheetCell cell : cells) {
                verify(clock).unRegisterListener(cell);
            }
        });
    }
}
