package com.sh0sh1n.watavyu.models.db;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/** The data store keeps the columns' order; the views follow it. */
public class ColumnOrderTest {

    private DataStore dataStore;
    private Column first;
    private Column second;
    private Column third;

    @BeforeMethod
    public void setUp() throws Exception {
        dataStore = DataStoreFactory.newDataStore();
        first = dataStore.createColumn("first");
        second = dataStore.createMediaColumn("media_1");
        third = dataStore.createColumn("third");
    }

    @Test
    public void columnsAreInTheOrderTheyWereMade() {
        assertEquals(dataStore.getAllColumns(), List.of(first, second, third));
        assertEquals(dataStore.indexOf(second), 1);
    }

    @Test
    public void selectionFollowsTheOrder() {
        dataStore.moveColumn(third, 0);
        first.setSelected(true);
        third.setSelected(true);
        second.setSelected(false);

        assertEquals(dataStore.getSelectedColumns(), List.of(third, first));
    }

    @Test
    public void aRemovedColumnHasNoPlaceAndTheOthersCloseUp() throws Exception {
        dataStore.removeColumn(first);

        assertEquals(dataStore.indexOf(first), -1);
        assertEquals(dataStore.indexOf(third), 1);
        Column fourth = dataStore.createColumn("fourth");
        assertEquals(dataStore.getAllColumns(), List.of(second, third, fourth));
    }

    @Test
    public void aColumnPutBackTakesItsPlace() {
        dataStore.removeColumn(first);

        dataStore.addColumn(first, 0);

        assertEquals(dataStore.getAllColumns(), List.of(first, second, third));
        assertEquals(dataStore.getColumn("first"), first);
    }

    @Test
    public void aColumnPutBackWithATakenNameOrOutOfRangeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> dataStore.addColumn(first, 0));
        dataStore.removeColumn(first);
        assertThrows(IllegalArgumentException.class, () -> dataStore.addColumn(first, 3));
        assertThrows(IllegalArgumentException.class, () -> dataStore.addColumn(first, -1));
        assertEquals(dataStore.getAllColumns(), List.of(second, third));
    }

    @Test
    public void movingAColumnShiftsThoseBetweenAndIsAChange() {
        dataStore.markAsUnchanged();

        dataStore.moveColumn(first, 2);

        assertEquals(dataStore.getAllColumns(), List.of(second, third, first));
        assertTrue(dataStore.isChanged());
    }

    @Test
    public void aColumnNotInTheSpreadsheetOrAPlaceOutOfRangeCannotBeMoved() {
        assertThrows(IllegalArgumentException.class, () -> dataStore.moveColumn(first, 3));
        dataStore.removeColumn(first);
        assertThrows(IllegalArgumentException.class, () -> dataStore.moveColumn(first, 0));
    }

    @Test
    public void movingAColumnToItsOwnPlaceIsNoChange() {
        dataStore.markAsUnchanged();
        DataStoreListener listener = mock(DataStoreListener.class);
        List<String> heard = new ArrayList<>();
        doAnswer(call -> heard.add("moved")).when(listener).columnOrderChanged();
        dataStore.addListener(listener);

        dataStore.moveColumn(second, 1);

        assertFalse(dataStore.isChanged());
        assertTrue(heard.isEmpty());
    }

    /** Listeners hear of a column added, removed or moved once the order already shows it. */
    @Test
    public void listenersHearOfChangesAfterTheyAreMade() {
        DataStoreListener listener = mock(DataStoreListener.class);
        List<Integer> places = new ArrayList<>();
        doAnswer(call -> places.add(dataStore.indexOf(call.getArgument(0)))).when(listener).columnAdded(any());
        doAnswer(call -> places.add(dataStore.indexOf(call.getArgument(0)))).when(listener).columnRemoved(any());
        doAnswer(call -> places.add(dataStore.indexOf(third))).when(listener).columnOrderChanged();
        dataStore.addListener(listener);

        dataStore.removeColumn(first);
        dataStore.addColumn(first, 0);
        dataStore.moveColumn(third, 0);

        assertEquals(places, List.of(-1, 0, 0));
    }

    /** A column renamed while out of the spreadsheet, as in a stale Code Editor, leaves no name behind. */
    @Test
    public void renamingAColumnOutOfTheSpreadsheetLeavesNoName() throws Exception {
        dataStore.removeColumn(first);

        first.setName("renamed");

        assertNull(dataStore.getColumn("renamed"));
        assertNull(dataStore.getColumn("first"));
        dataStore.createColumn("renamed");
    }
}
