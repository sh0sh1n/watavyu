package com.sh0sh1n.watavyu.views.spreadsheet.layouts;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.awt.Rectangle;
import java.util.List;

/**
 * Where the layouts put cells, worked out from the cells' times and heights alone. In time, a cell starts at its
 * onset's place and reaches down to its offset's; in order, cells follow one another. Either way a cell is marked where
 * it overlaps the next or ends before it starts.
 */
public class SheetGeometryTest {

    /** Every cell in these tests is this tall. */
    private static final int HEIGHT = 40;

    private static final int WIDTH = 230;

    private static final int GAP = SheetLayoutWeakTemporal.GAP;

    private DataStore dataStore;
    private Column notes;

    @BeforeMethod
    public void setUp() throws Exception {
        dataStore = DataStoreFactory.newDataStore();
        notes = dataStore.createColumn("notes");
    }

    private Cell cell(final Column column, final long onset, final long offset) {
        Cell cell = column.createPointCell(onset);
        cell.setOffset(offset);
        return cell;
    }

    private static SheetGeometry.ColumnCells shown(final Column column) {
        return new SheetGeometry.ColumnCells(column, WIDTH, List.copyOf(column.getCellsTemporally()));
    }

    private static SheetGeometry inTime(final Column... columns) {
        return SheetLayoutWeakTemporal.place(List.of(columns).stream().map(SheetGeometryTest::shown).toList(),
            cell -> HEIGHT);
    }

    private static SheetGeometry inOrder(final Column... columns) {
        return SheetLayoutOrdinal.place(List.of(columns).stream().map(SheetGeometryTest::shown).toList(),
            cell -> HEIGHT, 1);
    }

    private static Rectangle bounds(final SheetGeometry geometry, final Cell cell) {
        return geometry.of(cell).orElseThrow().bounds();
    }

    private static boolean overlap(final SheetGeometry geometry, final Cell cell) {
        return geometry.of(cell).orElseThrow().overlap();
    }

    @Test
    public void inTimeACellReachesFromItsOnsetsPlaceToItsOffsets() throws Exception {
        Cell first = cell(notes, 0, 1_000);
        Cell second = cell(notes, 2_000, 2_000);

        SheetGeometry geometry = inTime(notes);

        assertEquals(bounds(geometry, first), new Rectangle(0, 0, WIDTH - 1, HEIGHT + GAP));
        assertEquals(bounds(geometry, second), new Rectangle(0, HEIGHT + 2 * GAP, WIDTH - 1, HEIGHT));
        assertFalse(overlap(geometry, first));
        assertEquals(geometry.cellsHeight(notes), 2 * HEIGHT + 2 * GAP);
    }

    /** Columns share the times' places, so cells that start together line up across the sheet. */
    @Test
    public void inTimeCellsStartingTogetherLineUpAcrossColumns() throws Exception {
        Column speech = dataStore.createColumn("speech");
        cell(notes, 0, 0);
        cell(notes, 0, 0);
        Cell later = cell(notes, 5_000, 5_000);
        Cell alongside = cell(speech, 5_000, 5_000);

        SheetGeometry geometry = inTime(notes, speech);

        assertEquals(bounds(geometry, later).y, 2 * HEIGHT + GAP);
        assertEquals(bounds(geometry, alongside).y, bounds(geometry, later).y);
        assertEquals(geometry.height(), 3 * HEIGHT + GAP);
    }

    @Test
    public void inTimeCellsSharingAnOnsetStackAndAreMarkedUnlessTheSamePoint() throws Exception {
        Cell point = cell(notes, 0, 0);
        Cell samePoint = cell(notes, 0, 0);
        Cell span = cell(notes, 0, 3_000);

        SheetGeometry geometry = inTime(notes);

        List<Cell> stacked = notes.getCellsTemporally();
        assertEquals(bounds(geometry, stacked.get(0)).y, 0);
        assertEquals(bounds(geometry, stacked.get(1)).y, HEIGHT);
        assertEquals(bounds(geometry, stacked.get(2)).y, 2 * HEIGHT);
        assertTrue(overlap(geometry, point) || overlap(geometry, samePoint), "a point and the same point after it");
        assertFalse(overlap(geometry, span), "the last of the cells sharing an onset");
    }

    @Test
    public void inTimeACellReachingTheNextOnsetIsMarked() throws Exception {
        Cell first = cell(notes, 0, 2_000);
        Cell second = cell(notes, 1_000, 3_000);

        SheetGeometry geometry = inTime(notes);

        assertTrue(overlap(geometry, first));
        assertFalse(overlap(geometry, second));
        assertEquals(bounds(geometry, first).y + bounds(geometry, first).height, bounds(geometry, second).y);
    }

    @Test
    public void inTimeACellEndingBeforeItStartsIsMarked() throws Exception {
        Cell reversed = cell(notes, 5_000, 1_000);

        assertTrue(overlap(inTime(notes), reversed));
    }

    @Test
    public void inTimeCellsAMillisecondApartTouch() throws Exception {
        Cell first = cell(notes, 0, 999);
        Cell second = cell(notes, 1_000, 2_000);

        SheetGeometry geometry = inTime(notes);

        assertEquals(bounds(geometry, first).y + bounds(geometry, first).height, bounds(geometry, second).y);
    }

    @Test
    public void inTimeTheAxisPutsEachOnsetWhereItsCellsStart() throws Exception {
        cell(notes, 0, 0);
        Cell later = cell(notes, 4_000, 4_000);

        SheetGeometry geometry = inTime(notes);

        assertEquals(geometry.timeAxis().y(4_000), bounds(geometry, later).y);
    }

    @Test
    public void aColumnNotShownIsNotPlaced() throws Exception {
        Column hidden = dataStore.createColumn("hidden");
        Cell unseen = cell(hidden, 0, 0);
        cell(notes, 0, 0);

        assertTrue(inTime(notes).of(unseen).isEmpty());
        assertTrue(inOrder(notes).of(unseen).isEmpty());
        assertEquals(inTime(notes).cellsHeight(hidden), 0);
    }

    @Test
    public void inOrderCellsFollowOneAnotherWhateverTheirTimes() throws Exception {
        Cell first = cell(notes, 0, 0);
        Cell second = cell(notes, 60 * 60_000, 60 * 60_000);

        SheetGeometry geometry = inOrder(notes);

        assertEquals(bounds(geometry, first), new Rectangle(0, 0, WIDTH - 1, HEIGHT));
        assertEquals(bounds(geometry, second), new Rectangle(0, HEIGHT, WIDTH - 1, HEIGHT));
        assertEquals(geometry.cellsHeight(notes), 2 * HEIGHT);
        assertTrue(geometry.timeAxis().isEmpty());
    }

    @Test
    public void inOrderACellEndingAfterTheNextStartsIsMarked() throws Exception {
        Cell first = cell(notes, 0, 2_000);
        Cell second = cell(notes, 1_000, 1_500);

        SheetGeometry geometry = inOrder(notes);

        assertTrue(overlap(geometry, first));
        assertFalse(overlap(geometry, second));
    }

    /** A cell ending before it starts stays marked though a cell follows it; an offset of zero is not marked. */
    @Test
    public void inOrderACellEndingBeforeItStartsIsMarkedUnlessItsOffsetIsZero() throws Exception {
        Cell reversed = cell(notes, 5_000, 1_000);
        cell(notes, 6_000, 6_000);
        Cell zero = cell(dataStore.createColumn("speech"), 5_000, 0);

        assertTrue(overlap(inOrder(notes), reversed));
        assertFalse(overlap(inOrder(zero.getColumn()), zero));
    }
}
