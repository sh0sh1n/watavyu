package org.datavyu.views.discrete;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.Variable;
import org.datavyu.models.project.Project;
import org.datavyu.util.ClockTimer;
import org.datavyu.util.ClockTimer.ClockListener;
import org.datavyu.views.VideoController;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;

import java.util.List;
import javax.swing.SwingUtilities;

/** A column and its cells follow the clock while they are in the spreadsheet, and stop once they are gone. */
public class ColumnClockTest {

    @Test
    public void cellsAndColumnsStopFollowingTheClockOnceGone() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Variable notes = dataStore.createVariable("notes");
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
