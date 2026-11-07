package com.sh0sh1n.watavyu.models;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

/**
 * A cell holds one value for each of its column's codes, as text, {@code ""} when empty, and the values follow the
 * codes as they are added, moved and deleted.
 */
public class CellValuesTest {

    private DataStore dataStore;
    private Column column;
    private Cell cell;

    @BeforeMethod
    public void setUp() throws UserWarningException {
        dataStore = DataStoreFactory.newDataStore();
        column = dataStore.createColumn("gaze");
        cell = column.createCell();
    }

    @Test
    public void aNewCellHasAnEmptyValueForEachCode() {
        assertEquals(cell.getValues(), List.of(""));
        column.addCode();
        assertEquals(column.createCell().getValues(), List.of("", ""));
    }

    @Test
    public void aValueIsSetForOneCode() {
        column.addCode();

        cell.setValue(1, "left, then right");

        assertEquals(cell.getValue(1), "left, then right");
        assertEquals(cell.getValues(), List.of("", "left, then right"));
    }

    @Test
    public void theValuesGivenOutAreACopy() {
        List<String> values = cell.getValues();
        cell.setValue(0, "left");

        assertEquals(values, List.of(""));
        assertThrows(UnsupportedOperationException.class, () -> cell.getValues().set(0, "right"));
    }

    @Test
    public void aCodeTheColumnDoesNotHaveHasNoValue() {
        assertThrows(IndexOutOfBoundsException.class, () -> cell.getValue(1));
        assertThrows(IndexOutOfBoundsException.class, () -> cell.setValue(1, "left"));
        assertThrows(IndexOutOfBoundsException.class, () -> cell.setValue(-1, "left"));
    }

    @Test
    public void aClipHasNoValues() throws UserWarningException {
        Cell clip = dataStore.createMediaColumn("media_1")
            .createClip(0, new MediaClip("/study/cam.mp4", 10_000, 30, 1280, 720, 1));

        assertTrue(clip.getValues().isEmpty());
    }

    @Test
    public void aChangedValueIsHeardAndMarksTheProjectChanged() {
        CellListener listener = mock(CellListener.class);
        cell.addListener(listener);
        dataStore.markAsUnchanged();

        cell.setValue(0, "left");

        verify(listener, times(1)).contentChanged();
        assertTrue(dataStore.isChanged());
    }

    @Test
    public void theSameValueAgainIsNoChange() {
        cell.setValue(0, "left");
        CellListener listener = mock(CellListener.class);
        cell.addListener(listener);
        dataStore.markAsUnchanged();

        cell.setValue(0, "left");

        verify(listener, never()).contentChanged();
        assertFalse(dataStore.isChanged());
    }

    @Test
    public void aLockedColumnRefusesAValue() {
        column.setLocked(true);

        cell.setValue(0, "left");

        assertEquals(cell.getValue(0), "");
    }

    @Test
    public void valuesFollowTheirCodesAsCodesAreAddedMovedAndDeleted() {
        column.addCode();
        column.addCode();
        cell.setValue(0, "a");
        cell.setValue(1, "b");
        cell.setValue(2, "c");

        column.moveCode(0, 2);
        assertEquals(cell.getValues(), List.of("b", "c", "a"));

        column.removeCode(1);
        assertEquals(cell.getValues(), List.of("b", "a"));

        column.insertCode(1, "again");
        assertEquals(cell.getValues(), List.of("b", "", "a"));

        column.addCode();
        assertEquals(cell.getValues(), List.of("b", "", "a", ""));
    }

    /** A cell put back, as by an undo, goes only into its own column. */
    @Test
    public void aColumnTakesOnlyItsOwnCells() throws UserWarningException {
        Column other = dataStore.createColumn("speech");
        column.removeCell(cell);

        other.addCell(cell);
        assertTrue(other.getCells().isEmpty());

        column.addCell(cell);
        assertEquals(column.getCells(), List.of(cell));
    }

    @Test
    public void aNullValueIsRefused() {
        assertThrows(NullPointerException.class, () -> cell.setValue(0, null));
    }

    /** Setting every value at once, as undo does, is heard once. */
    @Test
    public void allTheValuesAreSetAtOnceAndHeardOnce() {
        column.addCode();
        CellListener listener = mock(CellListener.class);
        cell.addListener(listener);
        dataStore.markAsUnchanged();

        cell.setValues(List.of("left", "up"));

        assertEquals(cell.getValues(), List.of("left", "up"));
        verify(listener, times(1)).contentChanged();
        assertTrue(dataStore.isChanged());

        cell.setValues(List.of("left", "up"));
        verify(listener, times(1)).contentChanged();
    }

    @Test
    public void settingAllTheValuesNeedsOneForEachCode() {
        assertThrows(IllegalArgumentException.class, () -> cell.setValues(List.of("left", "up")));
        assertThrows(NullPointerException.class, () -> cell.setValues(java.util.Arrays.asList((String) null)));
        assertEquals(cell.getValues(), List.of(""));
    }

    @Test
    public void aLockedColumnRefusesAllTheValues() {
        column.setLocked(true);

        cell.setValues(List.of("left"));

        assertEquals(cell.getValues(), List.of(""));
    }
}
