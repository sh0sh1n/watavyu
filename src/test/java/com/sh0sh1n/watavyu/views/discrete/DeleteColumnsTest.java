package com.sh0sh1n.watavyu.views.discrete;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.controllers.project.ColumnCommands;
import com.sh0sh1n.watavyu.controllers.project.ProjectController;
import com.sh0sh1n.watavyu.models.db.Cell;
import com.sh0sh1n.watavyu.models.db.DataStore;
import com.sh0sh1n.watavyu.models.db.DataStoreFactory;
import com.sh0sh1n.watavyu.models.db.Variable;
import com.sh0sh1n.watavyu.models.project.Project;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.JMenuItem;
import java.util.List;

/** A column's header menu deletes the column, or the selected columns when it is one of them, as one undoable edit. */
public class DeleteColumnsTest {

    private DataStore dataStore;
    private Variable first;
    private Variable second;
    private Variable media;
    private ProjectController spreadsheet;
    private ColumnCommands columns;

    @BeforeMethod
    public void setUp() throws Exception {
        dataStore = DataStoreFactory.newDataStore();
        first = dataStore.createVariable("first");
        second = dataStore.createVariable("second");
        media = dataStore.createMediaVariable("media_1");
        first.setSelected(true);
        second.setSelected(true);
        media.setSelected(false);
        spreadsheet = new ProjectController(new Project(), dataStore);
        columns = spreadsheet.columns();
    }

    @Test
    public void anUnselectedColumnIsDeletedAlone() {
        JMenuItem item = SpreadsheetColumn.deleteItem(columns, dataStore, media);
        assertEquals(item.getText(), "Delete Column");

        item.doClick();

        assertNull(dataStore.getVariable("media_1"));
        assertNotNull(dataStore.getVariable("first"));
        assertNotNull(dataStore.getVariable("second"));
        assertEquals(spreadsheet.getUndoManager().getUndoPresentationName(), "Undo Delete Column media_1");
    }

    @Test
    public void aSelectedColumnDeletesTheSelection() {
        JMenuItem item = SpreadsheetColumn.deleteItem(columns, dataStore, second);
        assertEquals(item.getText(), "Delete Selected Columns");

        item.doClick();

        assertNull(dataStore.getVariable("first"));
        assertNull(dataStore.getVariable("second"));
        assertNotNull(dataStore.getVariable("media_1"));
        assertEquals(spreadsheet.getUndoManager().getUndoPresentationName(), "Undo Delete 2 Columns");
    }

    @Test
    public void theOnlySelectedColumnIsDeletedAlone() {
        second.setSelected(false);

        JMenuItem item = SpreadsheetColumn.deleteItem(columns, dataStore, first);
        assertEquals(item.getText(), "Delete Column");
        item.doClick();

        assertNull(dataStore.getVariable("first"));
        assertNotNull(dataStore.getVariable("second"));
    }

    /** A locked column is deleted only once it is unlocked, alone or among the selected columns. */
    @Test
    public void deleteIsDisabledWhileAColumnItWouldDeleteIsLocked() {
        media.setLocked(true);
        assertFalse(SpreadsheetColumn.deleteItem(columns, dataStore, media).isEnabled());
        assertTrue(SpreadsheetColumn.deleteItem(columns, dataStore, first).isEnabled());

        second.setLocked(true);
        assertFalse(SpreadsheetColumn.deleteItem(columns, dataStore, first).isEnabled());
    }

    /** Undo brings the deleted columns back with their cells, and redo deletes them again, all in one step. */
    @Test
    public void undoRestoresTheColumnsWithTheirCellsAndRedoDeletesThemAgain() throws Exception {
        Cell cell = first.createCell();
        cell.setOnset(1_000);

        SpreadsheetColumn.deleteItem(columns, dataStore, first).doClick();
        spreadsheet.undo();

        assertEquals(dataStore.getVariable("first"), first);
        assertEquals(dataStore.getVariable("second"), second);
        assertEquals(first.getCells(), List.of(cell));

        spreadsheet.redo();
        assertNull(dataStore.getVariable("first"));
        assertNull(dataStore.getVariable("second"));
    }
}
