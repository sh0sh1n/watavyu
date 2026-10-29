package org.datavyu.controllers.project;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.Variable;
import org.datavyu.undoableedits.AddMediaColumnEdit;
import org.testng.annotations.Test;

import javax.swing.undo.UndoManager;

import java.util.List;

/** Media columns made apart from clips: by command, and as a new spreadsheet starts. */
public class MediaColumnsTest {

    @Test
    public void aNewMediaColumnIsEmptyLastAndNamedAfterTheOthers() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        dataStore.createVariable("codes");
        MediaColumns.add(dataStore);

        Variable column = MediaColumns.add(dataStore);

        assertEquals(column.getName(), "media_2");
        assertTrue(column.isMedia());
        assertEquals(column.getCells(), List.of());
        assertEquals(dataStore.getAllVariables().get(2), column);
        assertTrue(column.isSelected());
    }

    /** A new spreadsheet starts with its media columns, the first selected for the media added next, unchanged. */
    @Test
    public void aNewSpreadsheetStartsWithItsMediaColumns() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        MediaColumns.startNewSpreadsheet(dataStore, 2);

        List<Variable> columns = dataStore.getAllVariables();
        assertEquals(columns.stream().map(Variable::getName).toList(), List.of("media_1", "media_2"));
        assertTrue(columns.get(0).isSelected());
        assertFalse(columns.get(1).isSelected());
        assertEquals(dataStore.getSelectedVariables(), List.of(columns.get(0)));
        assertFalse(dataStore.isChanged());
    }

    @Test
    public void aNewSpreadsheetCanStartWithoutMediaColumns() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        MediaColumns.startNewSpreadsheet(dataStore, 0);

        assertEquals(dataStore.getAllVariables(), List.of());
    }

    /** New Media Column makes the new column the only one selected, so media added next goes into it. */
    @Test
    public void newMediaColumnIsTheOnlyColumnSelected() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Variable codes = dataStore.createVariable("codes");
        codes.setSelected(true);

        Variable column = MediaColumns.addSelected(dataStore);

        assertEquals(dataStore.getSelectedVariables(), List.of(column));
    }

    /** New Media Column is undone and redone like any edit: the column goes, then comes back as a media column. */
    @Test
    public void newMediaColumnCanBeUndoneAndRedone() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        dataStore.createVariable("codes");
        UndoManager history = new UndoManager();
        Variable column = MediaColumns.addSelected(dataStore);
        history.addEdit(new AddMediaColumnEdit(dataStore, column.getName()));

        assertEquals(history.getUndoPresentationName(), "Undo New Media Column media_1");
        history.undo();
        assertEquals(dataStore.getAllVariables().stream().map(Variable::getName).toList(), List.of("codes"));

        history.redo();
        Variable back = dataStore.getVariable("media_1");
        assertTrue(back.isMedia());
        assertEquals(dataStore.getAllVariables().get(1), back);
    }
}
