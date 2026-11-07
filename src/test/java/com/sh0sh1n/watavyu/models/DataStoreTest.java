/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sh0sh1n.watavyu.models;

import org.testng.annotations.*;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests for the DataStore interface.
 */
public class DataStoreTest {

    /**
     * The model we are testing.
     */
    private DataStore model;

    /**
     * The modelListener we are testing.
     */
    private DataStoreListener modelListener;

    /**
     * The title notifier we are testing.
     */
    private TitleNotifier titleListener;

    @BeforeClass
    public void spinUp() {
    }

    @AfterClass
    public void spinDown() {
    }

    @BeforeMethod
    public void setUp() {
        model = DataStoreFactory.newDataStore();
        modelListener = mock(DataStoreListener.class);
        titleListener = mock(TitleNotifier.class);
        model.addListener(modelListener);
        model.setTitleNotifier(titleListener);
    }

    @AfterMethod
    public void tearDown() {
        model.removeListener(modelListener);
        modelListener = null;
        titleListener = null;
        model = null;
    }

    @Test
    public void testSetName() {
        model.setName("testName");
        assertEquals(model.getName(), "testName");
    }

    @Test
    public void createColumn() throws UserWarningException {
        assertFalse(model.isChanged());
        model.createColumn("foo");
        assertTrue(model.isChanged());
        Column var = model.getColumn("foo");
        List<Column> varList = new ArrayList<Column>();
        varList.add(var);

        assertNotNull(var);

        // TODO: database should be changed after adding a new column.
        // assertTrue(model.isChanged());

        assertEquals(model.getSelectedColumns(), varList);
        assertEquals(model.getAllColumns(), varList);
        assertEquals(var.getName(), "foo");
        assertEquals(var.getCodes().size(), 1);
        assertTrue(var.isSelected());
        assertTrue(!var.isHidden());
        assertEquals(var.getCells().size(), 0);
        verify(modelListener).columnAdded(var);
        verify(modelListener, times(0)).columnOrderChanged();
        verify(modelListener, times(0)).columnRemoved(var);
        verify(modelListener, times(0)).columnHidden(var);
        verify(modelListener, times(0)).columnNameChange(var);
        verify(modelListener, times(0)).columnVisible(var);
        verify(titleListener).updateTitle();
    }

    @Test(expectedExceptions = UserWarningException.class)
    public void unableToCreateColumn() throws UserWarningException {
        model.createColumn("foo");
        model.createColumn("foo");
    }

    @Test
    public void removeColumn() throws UserWarningException {
        Column var = model.createColumn("foo");
        List<Column> varList = new ArrayList<Column>();
        varList.add(var);

        assertEquals(model.getAllColumns(), varList);
        verify(modelListener).columnAdded(var);

        model.markAsUnchanged();
        assertFalse(model.isChanged());
        model.removeColumn(var);
        assertTrue(model.isChanged());

        assertEquals(model.getAllColumns().size(), 0);
        assertEquals(model.getSelectedColumns().size(), 0);

        verify(modelListener).columnRemoved(var);
        verify(modelListener, times(0)).columnHidden(var);
        verify(modelListener, times(0)).columnVisible(var);
        verify(modelListener, times(0)).columnNameChange(var);
        verify(modelListener, times(0)).columnOrderChanged();
    }

    @Test
    public void unchangedByDefault() throws UserWarningException {
        assertFalse(model.isChanged());
        model.createColumn("test");
        assertTrue(model.isChanged());
        model.markAsUnchanged();
        assertFalse(model.isChanged());
    }
}
