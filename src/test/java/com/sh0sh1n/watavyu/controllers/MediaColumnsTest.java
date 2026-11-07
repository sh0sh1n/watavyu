package com.sh0sh1n.watavyu.controllers;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.Column;
import org.testng.annotations.Test;


import java.util.List;

/** Media columns made apart from clips: by command, and as a new spreadsheet starts. */
public class MediaColumnsTest {

    @Test
    public void aNewMediaColumnIsEmptyLastAndNamedAfterTheOthers() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        dataStore.createColumn("codes");
        MediaColumns.add(dataStore);

        Column column = MediaColumns.add(dataStore);

        assertEquals(column.getName(), "media_2");
        assertTrue(column.isMedia());
        assertEquals(column.getCells(), List.of());
        assertEquals(dataStore.getAllColumns().get(2), column);
        assertTrue(column.isSelected());
    }

    /** A new spreadsheet starts with its media columns, the first selected for the media added next, unchanged. */
    @Test
    public void aNewSpreadsheetStartsWithItsMediaColumns() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        MediaColumns.startNewSpreadsheet(dataStore, 2);

        List<Column> columns = dataStore.getAllColumns();
        assertEquals(columns.stream().map(Column::getName).toList(), List.of("media_1", "media_2"));
        assertTrue(columns.get(0).isSelected());
        assertFalse(columns.get(1).isSelected());
        assertEquals(dataStore.getSelectedColumns(), List.of(columns.get(0)));
        assertFalse(dataStore.isChanged());
    }

    @Test
    public void aNewSpreadsheetCanStartWithoutMediaColumns() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        MediaColumns.startNewSpreadsheet(dataStore, 0);

        assertEquals(dataStore.getAllColumns(), List.of());
    }

    /** New Media Column makes the new column the only one selected, so media added next goes into it. */
    @Test
    public void newMediaColumnIsTheOnlyColumnSelected() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Column codes = dataStore.createColumn("codes");
        codes.setSelected(true);

        Column column = MediaColumns.addSelected(dataStore);

        assertEquals(dataStore.getSelectedColumns(), List.of(column));
    }
}
