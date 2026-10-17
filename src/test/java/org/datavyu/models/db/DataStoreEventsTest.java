package org.datavyu.models.db;

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
        Variable column = dataStore.createVariable("before");
        DataStoreListener listener = mock(DataStoreListener.class);
        List<String> seen = new ArrayList<>();
        doAnswer(call -> seen.add(((Variable) call.getArgument(0)).getName()))
            .when(listener).variableNameChange(any());
        dataStore.addListener(listener);

        column.setName("after");

        assertEquals(seen, List.of("after"));
        assertEquals(dataStore.getVariable("after"), column);
    }

    @Test
    public void keepingAColumnsNameTellsNoOne() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Variable column = dataStore.createVariable("same");
        DataStoreListener listener = mock(DataStoreListener.class);
        dataStore.addListener(listener);

        column.setName("same");

        verify(listener, never()).variableNameChange(any());
    }

    @Test
    public void reorderingColumnsTellsListeners() {
        DataStore dataStore = DataStoreFactory.newDataStore();
        DataStoreListener listener = mock(DataStoreListener.class);
        dataStore.addListener(listener);

        dataStore.columnsReordered();

        verify(listener).variableOrderChanged();
    }
}
