package org.datavyu.views.discrete;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.controllers.project.ColumnCommands;
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.Variable;
import org.datavyu.models.project.Project;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.JMenuItem;

/** A column's header menu hides the column, or the selected columns when it is one of them. */
public class HideColumnsTest {

    private DataStore dataStore;
    private Variable first;
    private Variable second;
    private Variable third;
    private ColumnCommands columns;

    @BeforeMethod
    public void setUp() throws Exception {
        dataStore = DataStoreFactory.newDataStore();
        first = dataStore.createVariable("first");
        second = dataStore.createVariable("second");
        third = dataStore.createMediaVariable("media_1");
        first.setSelected(true);
        second.setSelected(true);
        third.setSelected(false);
        columns = new ProjectController(new Project(), dataStore).columns();
    }

    @Test
    public void anUnselectedColumnHidesAlone() {
        JMenuItem item = SpreadsheetColumn.hideItem(columns, dataStore, third);
        assertEquals(item.getText(), "Hide Column");

        item.doClick();

        assertTrue(third.isHidden());
        assertFalse(first.isHidden());
        assertFalse(second.isHidden());
    }

    @Test
    public void aSelectedColumnHidesTheSelection() {
        JMenuItem item = SpreadsheetColumn.hideItem(columns, dataStore, second);
        assertEquals(item.getText(), "Hide Selected Columns");

        item.doClick();

        assertTrue(first.isHidden());
        assertTrue(second.isHidden());
        assertFalse(first.isSelected());
        assertFalse(second.isSelected());
        assertFalse(third.isHidden());
    }

    @Test
    public void theOnlySelectedColumnHidesAlone() {
        second.setSelected(false);

        JMenuItem item = SpreadsheetColumn.hideItem(columns, dataStore, first);
        assertEquals(item.getText(), "Hide Column");
        item.doClick();

        assertTrue(first.isHidden());
        assertFalse(second.isHidden());
    }
}
