package com.sh0sh1n.watavyu.views.spreadsheet;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.Project;
import com.sh0sh1n.watavyu.util.ClockTimer;
import com.sh0sh1n.watavyu.util.ClockTimer.ClockListener;
import com.sh0sh1n.watavyu.views.VideoController;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.awt.Component;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

/**
 * The sheet alone follows the clock, for all its cells, its columns and the editor, and does so on the event thread:
 * the clock's own thread touches no Swing component. It stops following the clock once it is closed.
 */
public class ColumnClockTest {

    private Column notes;
    private Cell inPlay;
    private Cell later;
    private ClockTimer clock;
    private VideoController playback;
    private SpreadsheetView sheet;
    private SpreadsheetColumn column;
    private RepaintManager previousManager;

    /** The Swing components asked to repaint or lay out again off the event thread. */
    private final List<Component> offTheEventThread = new CopyOnWriteArrayList<>();

    @BeforeMethod
    public void setUp() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        notes = dataStore.createColumn("notes");
        inPlay = notes.createPointCell(1_000);
        later = notes.createPointCell(2_000);
        clock = mock(ClockTimer.class);
        playback = mock(VideoController.class);
        when(playback.getClockTimer()).thenReturn(clock);
        ProjectController spreadsheet = new ProjectController(new Project(), dataStore);
        spreadsheet.setVideoController(playback);

        SwingUtilities.invokeAndWait(() -> {
            sheet = new SpreadsheetView(spreadsheet);
            column = new SpreadsheetColumn(spreadsheet, notes, sheet, mock(CellSelectionListener.class),
                mock(ColumnSelectionListener.class), mock(ColumnVisibilityListener.class));
            sheet.addColumn(column);
            column.registerListeners();
            previousManager = RepaintManager.currentManager(sheet);
            RepaintManager.setCurrentManager(new RepaintManager() {
                @Override
                public synchronized void addInvalidComponent(final JComponent component) {
                    if (!SwingUtilities.isEventDispatchThread()) {
                        offTheEventThread.add(component);
                    }
                    super.addInvalidComponent(component);
                }

                @Override
                public void addDirtyRegion(final JComponent component, final int x, final int y, final int w,
                                           final int h) {
                    if (!SwingUtilities.isEventDispatchThread()) {
                        offTheEventThread.add(component);
                    }
                    super.addDirtyRegion(component, x, y, w, h);
                }
            });
        });
    }

    @AfterMethod
    public void tearDown() throws Exception {
        SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(previousManager));
    }

    private ClockListener sheetsListener() {
        ArgumentCaptor<ClockListener> registered = ArgumentCaptor.forClass(ClockListener.class);
        verify(clock, atLeastOnce()).registerListener(registered.capture());
        return registered.getValue();
    }

    @Test
    public void theSheetAloneFollowsTheClockUntilItIsClosed() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            sheet.edit(later);
            ArgumentCaptor<ClockListener> registered = ArgumentCaptor.forClass(ClockListener.class);
            verify(clock, atLeastOnce()).registerListener(registered.capture());
            assertEquals(registered.getAllValues().size(), 1, "the sheet alone: " + registered.getAllValues());

            sheet.close();
            verify(clock).unRegisterListener(registered.getValue());
        });
    }

    /**
     * In highlight-and-focus mode, a tick while the clock runs moves the editor to the selected column's cell in play
     * and styles it as in play, all on the event thread, though the clock ticks on its own.
     */
    @Test
    public void aTickFromTheClockThreadMovesAndStylesTheEditorOnTheEventThread() throws Exception {
        when(playback.getCellHighlightAndFocus()).thenReturn(true);
        when(playback.getCurrentTime()).thenReturn(1_000L);
        when(clock.isPaused()).thenReturn(false);
        SwingUtilities.invokeAndWait(() -> {
            notes.setSelected(true);
            sheet.edit(later);
        });
        offTheEventThread.clear();

        ClockListener listener = sheetsListener();
        Thread clockThread = new Thread(() -> listener.clockPeriodicSync(1_000), "clock");
        clockThread.start();
        clockThread.join();
        SwingUtilities.invokeAndWait(() -> {
            // Lets the tick handed to the event thread run.
        });

        SwingUtilities.invokeAndWait(() -> {
            SpreadsheetCell editor = sheet.getEditor().orElseThrow();
            assertEquals(editor.getCell(), inPlay);
            assertEquals(editor.getShownTime(), CellLook.Time.IN_PLAY);
        });
        assertTrue(offTheEventThread.isEmpty(), "touched off the event thread: " + offTheEventThread);
    }

    /** While the clock is paused, the editor stays where it was, though it still shows where the needle is. */
    @Test
    public void aPausedTickLeavesTheEditorWhereItIs() throws Exception {
        when(playback.getCellHighlightAndFocus()).thenReturn(true);
        when(playback.getCurrentTime()).thenReturn(1_000L);
        when(clock.isPaused()).thenReturn(true);
        SwingUtilities.invokeAndWait(() -> {
            notes.setSelected(true);
            sheet.edit(later);
        });

        ClockListener listener = sheetsListener();
        Thread clockThread = new Thread(() -> listener.clockPeriodicSync(1_000), "clock");
        clockThread.start();
        clockThread.join();
        SwingUtilities.invokeAndWait(() -> {
            // Lets the tick handed to the event thread run.
        });

        SwingUtilities.invokeAndWait(() -> {
            SpreadsheetCell editor = sheet.getEditor().orElseThrow();
            assertEquals(editor.getCell(), later);
            assertEquals(editor.getShownTime(), CellLook.Time.AHEAD);
        });
    }

    /**
     * A jump in highlight-and-focus mode restyles the editor but leaves it where it is, as a new cell made at the
     * needle takes the focus; only a periodic tick moves it.
     */
    @Test
    public void aJumpStylesTheEditorButLeavesItWhereItIs() throws Exception {
        when(playback.getCellHighlightAndFocus()).thenReturn(true);
        when(clock.isPaused()).thenReturn(false);
        SwingUtilities.invokeAndWait(() -> {
            notes.setSelected(true);
            sheet.edit(inPlay);
        });
        when(playback.getCurrentTime()).thenReturn(2_000L);

        ClockListener listener = sheetsListener();
        Thread clockThread = new Thread(() -> listener.clockForceSync(2_000), "clock");
        clockThread.start();
        clockThread.join();
        SwingUtilities.invokeAndWait(() -> {
            // Lets the jump handed to the event thread run.
        });

        SwingUtilities.invokeAndWait(() -> {
            SpreadsheetCell editor = sheet.getEditor().orElseThrow();
            assertEquals(editor.getCell(), inPlay);
            assertEquals(editor.getShownTime(), CellLook.Time.PAST);
        });
    }

    /** A tick handed to the event thread just before the sheet closes finds it closed and does nothing. */
    @Test
    public void aTickThatArrivesAfterTheSheetClosesDoesNothing() throws Exception {
        when(playback.getCellHighlightAndFocus()).thenReturn(true);
        when(playback.getCurrentTime()).thenReturn(1_000L);
        when(clock.isPaused()).thenReturn(false);
        SwingUtilities.invokeAndWait(() -> {
            notes.setSelected(true);
            sheet.edit(later);
        });

        ClockListener listener = sheetsListener();
        SwingUtilities.invokeAndWait(() -> {
            listener.clockPeriodicSync(1_000);
            sheet.close();
        });
        SwingUtilities.invokeAndWait(() -> {
            // Lets the tick handed to the event thread run.
        });

        SwingUtilities.invokeAndWait(() -> assertEquals(sheet.getEditor(), Optional.empty()));
    }
}
