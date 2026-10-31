package org.datavyu.views.discrete;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.Variable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.JMenuItem;
import javax.swing.undo.UndoManager;
import java.util.List;

/** A column's header menu deletes the column, or the selected columns when it is one of them, as one undoable edit. */
public class DeleteColumnsTest {

    private DataStore dataStore;
    private Variable first;
    private Variable second;
    private Variable media;
    private UndoManager history;

    @BeforeMethod
    public void setUp() throws Exception {
        dataStore = DataStoreFactory.newDataStore();
        first = dataStore.createVariable("first");
        second = dataStore.createVariable("second");
        media = dataStore.createMediaVariable("media_1");
        first.setSelected(true);
        second.setSelected(true);
        media.setSelected(false);
        history = new UndoManager();
    }

    @Test
    public void anUnselectedColumnIsDeletedAlone() {
        JMenuItem item = SpreadsheetColumn.deleteItem(dataStore, media, history::addEdit);
        assertEquals(item.getText(), "Delete Column");

        item.doClick();

        assertNull(dataStore.getVariable("media_1"));
        assertNotNull(dataStore.getVariable("first"));
        assertNotNull(dataStore.getVariable("second"));
        assertEquals(history.getUndoPresentationName(), "Undo Delete Column media_1");
    }

    @Test
    public void aSelectedColumnDeletesTheSelection() {
        JMenuItem item = SpreadsheetColumn.deleteItem(dataStore, second, history::addEdit);
        assertEquals(item.getText(), "Delete Selected Columns");

        item.doClick();

        assertNull(dataStore.getVariable("first"));
        assertNull(dataStore.getVariable("second"));
        assertNotNull(dataStore.getVariable("media_1"));
        assertEquals(history.getUndoPresentationName(), "Undo Delete 2 Columns");
    }

    @Test
    public void theOnlySelectedColumnIsDeletedAlone() {
        second.setSelected(false);

        JMenuItem item = SpreadsheetColumn.deleteItem(dataStore, first, history::addEdit);
        assertEquals(item.getText(), "Delete Column");
        item.doClick();

        assertNull(dataStore.getVariable("first"));
        assertNotNull(dataStore.getVariable("second"));
    }

    /** A locked column is deleted only once it is unlocked, alone or among the selected columns. */
    @Test
    public void deleteIsDisabledWhileAColumnItWouldDeleteIsLocked() {
        media.setLocked(true);
        assertFalse(SpreadsheetColumn.deleteItem(dataStore, media, history::addEdit).isEnabled());
        assertTrue(SpreadsheetColumn.deleteItem(dataStore, first, history::addEdit).isEnabled());

        second.setLocked(true);
        assertFalse(SpreadsheetColumn.deleteItem(dataStore, first, history::addEdit).isEnabled());
    }

    /** Undo brings the deleted columns back with their cells, and redo deletes them again, all in one step. */
    @Test
    public void undoRestoresTheColumnsWithTheirCellsAndRedoDeletesThemAgain() throws Exception {
        Cell cell = first.createCell();
        cell.setOnset(1_000);

        SpreadsheetColumn.deleteItem(dataStore, first, history::addEdit).doClick();
        history.undo();

        assertEquals(dataStore.getVariable("first"), first);
        assertEquals(dataStore.getVariable("second"), second);
        assertEquals(first.getCells(), List.of(cell));

        history.redo();
        assertNull(dataStore.getVariable("first"));
        assertNull(dataStore.getVariable("second"));
    }
}
