package com.sh0sh1n.watavyu.views.spreadsheet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.TestSpreadsheets;
import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import org.testng.annotations.Test;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * The key map's cell keys in a spreadsheet (AGENTS.md › Decisions, 2026-11-13): {@code o} makes a cell at the playhead
 * in the selected column and goes into insert mode in it, {@code O} does the same setting the previous cell's offset,
 * and nothing happens with no column selected; {@code {} and {@code }} go to the selected cell's onset and offset. The
 * commands' rules are {@code CellCommandsTest}'s.
 */
public class SheetEditingKeysTest {

    private Column gaze;
    private Column media;
    private Cell early;
    private Cell late;
    private ProjectController spreadsheet;
    private SpreadsheetView sheet;

    private final AtomicReference<Component> owner = new AtomicReference<>();
    private final List<Component> focused = new ArrayList<>();

    /** Builds gaze (cells at 1 s and 3 s, the second ending at 4 s) and a media column, nothing selected. */
    private void withPanel(final Consumer<SpreadSheetPanel> test) throws Exception {
        owner.set(null);
        focused.clear();
        DataStore dataStore = DataStoreFactory.newDataStore();
        gaze = dataStore.createColumn("gaze");
        media = dataStore.createMediaColumn("media_1");
        early = gaze.createPointCell(1_000);
        late = gaze.createPointCell(3_000);
        late.setOffset(4_000);
        dataStore.deselectAll();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            spreadsheet = TestSpreadsheets.withPlayback(dataStore);
            SpreadSheetPanel panel = new SpreadSheetPanel(spreadsheet, null);
            spreadsheet.setSpreadSheetPanel(panel);
            panel.setSize(900, 400);
            layOut(panel);
            try {
                sheet = panel.getColumns().get(0).getSheet();
                sheet.setFocus(owner::get, component -> {
                    focused.add(component);
                    owner.set(component instanceof SpreadsheetCell cell ? cell.getDataView() : component);
                });
                owner.set(sheet);
                test.accept(panel);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                panel.close();
            }
        });
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static void layOut(final Component component) {
        if (component instanceof java.awt.Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layOut(child);
            }
        }
    }

    private SpreadsheetColumn column(final SpreadSheetPanel panel, final Column column) {
        return panel.getColumns().stream().filter(view -> view.getColumn().equals(column)).findFirst().orElseThrow();
    }

    private void playheadAt(final SpreadSheetPanel panel, final long time) {
        panel.getVideoController().setCurrentTime(time);
    }

    @Test
    public void newCellHereGoesIntoANewCellAtThePlayhead() throws Exception {
        withPanel(panel -> {
            column(panel, gaze).selectInCommandMode(early);
            playheadAt(panel, 2_000);

            panel.newCellHere(false);

            assertEquals(gaze.getCells().size(), 3);
            Cell made = spreadsheet.getLastCreatedCell();
            assertEquals(made.getOnset(), 2_000);
            assertTrue(made.isSelected());
            assertFalse(early.isSelected());
            assertTrue(sheet.inInsertMode());
            assertEquals(sheet.getEditor().orElseThrow().getCell(), made);
            assertEquals(early.getOffset(), 1_000, "o closes nothing");
        });
    }

    @Test
    public void newCellHereGoesInTheSelectedColumnWithNoCellSelected() throws Exception {
        withPanel(panel -> {
            column(panel, gaze).goToColumn();
            playheadAt(panel, 5_000);

            panel.newCellHere(false);

            assertEquals(spreadsheet.getLastCreatedCell().getColumn(), gaze);
            assertEquals(spreadsheet.getLastCreatedCell().getOnset(), 5_000);
        });
    }

    @Test
    public void newCellHereClosingPreviousSetsThePreviousCellsOffset() throws Exception {
        withPanel(panel -> {
            column(panel, gaze).selectInCommandMode(early);
            playheadAt(panel, 6_000);

            panel.newCellHere(true);

            assertEquals(late.getOffset(), 5_999);
            assertEquals(spreadsheet.getLastCreatedCell().getOnset(), 6_000);
            assertTrue(sheet.inInsertMode());
        });
    }

    @Test
    public void newCellHereWithNoColumnOrAMediaColumnDoesNothing() throws Exception {
        withPanel(panel -> {
            spreadsheet.setLastCreatedColumn(gaze);
            panel.newCellHere(false);
            panel.newCellHere(true);
            column(panel, media).goToColumn();
            panel.newCellHere(false);

            assertEquals(gaze.getCells().size(), 2);
            assertTrue(media.getCells().isEmpty());
            assertEquals(late.getOffset(), 4_000);
            assertFalse(sheet.inInsertMode());
        });
    }

    @Test
    public void goToOnsetAndOffsetMoveThePlayheadToTheSelectedCell() throws Exception {
        withPanel(panel -> {
            column(panel, gaze).selectInCommandMode(late);

            panel.goToSelectedCellTime(true);
            assertEquals(panel.getVideoController().getCurrentTime(), 4_000);
            panel.goToSelectedCellTime(false);
            assertEquals(panel.getVideoController().getCurrentTime(), 3_000);
            assertTrue(sheet.inCommandMode(), "the mode is kept");
        });
    }

    @Test
    public void goToOnsetWithNoCellSelectedDoesNothing() throws Exception {
        withPanel(panel -> {
            playheadAt(panel, 2_500);
            panel.goToSelectedCellTime(false);
            assertEquals(panel.getVideoController().getCurrentTime(), 2_500);
        });
    }
}
