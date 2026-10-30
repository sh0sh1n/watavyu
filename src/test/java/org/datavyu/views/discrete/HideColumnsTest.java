package org.datavyu.views.discrete;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.Variable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.JMenuItem;
import java.util.concurrent.atomic.AtomicInteger;

/** A column's header menu hides the column, or the selected columns when it is one of them. */
public class HideColumnsTest {

    private DataStore dataStore;
    private Variable first;
    private Variable second;
    private Variable third;
    private final AtomicInteger laidOut = new AtomicInteger();

    @BeforeMethod
    public void setUp() throws Exception {
        dataStore = DataStoreFactory.newDataStore();
        first = dataStore.createVariable("first");
        second = dataStore.createVariable("second");
        third = dataStore.createMediaVariable("media_1");
        first.setSelected(true);
        second.setSelected(true);
        third.setSelected(false);
        laidOut.set(0);
    }

    @Test
    public void anUnselectedColumnHidesAlone() {
        JMenuItem item = SpreadsheetColumn.hideItem(dataStore, third, laidOut::incrementAndGet);
        assertEquals(item.getText(), "Hide Column");

        item.doClick();

        assertTrue(third.isHidden());
        assertFalse(first.isHidden());
        assertFalse(second.isHidden());
        assertEquals(laidOut.get(), 1);
    }

    @Test
    public void aSelectedColumnHidesTheSelection() {
        JMenuItem item = SpreadsheetColumn.hideItem(dataStore, second, laidOut::incrementAndGet);
        assertEquals(item.getText(), "Hide Selected Columns");

        item.doClick();

        assertTrue(first.isHidden());
        assertTrue(second.isHidden());
        assertFalse(first.isSelected());
        assertFalse(second.isSelected());
        assertFalse(third.isHidden());
        assertEquals(laidOut.get(), 1);
    }

    @Test
    public void theOnlySelectedColumnHidesAlone() {
        second.setSelected(false);

        JMenuItem item = SpreadsheetColumn.hideItem(dataStore, first, laidOut::incrementAndGet);
        assertEquals(item.getText(), "Hide Column");
        item.doClick();

        assertTrue(first.isHidden());
        assertFalse(second.isHidden());
    }
}
