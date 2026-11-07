package com.sh0sh1n.watavyu.models.db;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/** B3: the data store tells its listeners, such as the Column List, when a column is renamed or reordered. */
public class DataStoreEventsTest {

    @Test
    public void renamingAColumnTellsListenersItsNewName() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Column column = dataStore.createColumn("before");
        DataStoreListener listener = mock(DataStoreListener.class);
        List<String> seen = new ArrayList<>();
        doAnswer(call -> seen.add(((Column) call.getArgument(0)).getName()))
            .when(listener).columnNameChange(any());
        dataStore.addListener(listener);

        column.setName("after");

        assertEquals(seen, List.of("after"));
        assertEquals(dataStore.getColumn("after"), column);
    }

    @Test
    public void keepingAColumnsNameTellsNoOne() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Column column = dataStore.createColumn("same");
        DataStoreListener listener = mock(DataStoreListener.class);
        dataStore.addListener(listener);

        column.setName("same");

        verify(listener, never()).columnNameChange(any());
    }

    @Test
    public void reorderingColumnsTellsListeners() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Column first = dataStore.createColumn("first");
        dataStore.createColumn("second");
        DataStoreListener listener = mock(DataStoreListener.class);
        dataStore.addListener(listener);

        dataStore.moveColumn(first, 1);

        verify(listener).columnOrderChanged();
    }
}
