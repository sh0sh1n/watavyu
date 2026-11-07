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
package org.datavyu.models.db;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests for the cell interface
 */
public class CellTest {

    /**
     * The parent datastore for the cell we are testing.
     */
    private DataStore ds;

    /**
     * The parent variable for the cell we are testing.
     */
    private Variable var;

    /**
     * The model we are testing.
     */
    private Cell model;

    /**
     * the modelListener we are testing.
     */
    private CellListener modelListener;

    @BeforeMethod
    public void setUp() throws UserWarningException {
        ds = DataStoreFactory.newDataStore();
        var = ds.createVariable("test");
        model = var.createCell();

        modelListener = mock(CellListener.class);
        model.addListener(modelListener);
    }

    @AfterMethod
    public void tearDown() {
        model.removeListener(modelListener);
        modelListener = null;
        model = null;
        var = null;
        ds = null;
    }

    /** B13: no time is negative; a negative onset or offset is refused and leaves the cell as it was. */
    @Test
    public void negativeTimesAreRefused() {
        model.setOnset(1_000);
        model.setOffset(2_000);

        assertThrows(IllegalArgumentException.class, () -> model.setOnset(-1));
        assertThrows(IllegalArgumentException.class, () -> model.setOffset(-1));
        assertThrows(IllegalArgumentException.class, () -> model.setOnset("-00:00:01:000"));

        assertEquals(model.getOnset(), 1_000L);
        assertEquals(model.getOffset(), 2_000L);
    }

    @Test
    public void testIsSelected() {
        assertTrue(model.isSelected());
        model.setSelected(false);
        assertFalse(model.isSelected());

        verify(modelListener).selectionChange(false);
        verify(modelListener, times(0)).highlightingChange(true);
        verify(modelListener, times(0)).offsetChanged(0);
        verify(modelListener, times(0)).onsetChanged(0);
        verify(modelListener, times(0)).contentChanged();
    }

    @Test
    public void testIsHighlighted() {
        assertTrue(model.isHighlighted());
        model.setHighlighted(false);
        assertFalse(model.isHighlighted());

        verify(modelListener).highlightingChange(false);
        verify(modelListener, times(0)).selectionChange(true);
        verify(modelListener, times(0)).offsetChanged(0);
        verify(modelListener, times(0)).onsetChanged(0);
        verify(modelListener, times(0)).contentChanged();
    }

    @Test
    public void testSetOffset() {
//        assertEquals(model.getOffset(), 0);
        assertEquals(model.getOffsetString(), "00:00:00:000");

        model.setOffset(10);
//        assertEquals(model.getOffset(), 10);
        assertEquals(model.getOffsetString(), "00:00:00:010");
        verify(modelListener).offsetChanged(10);
        verify(modelListener, times(0)).onsetChanged(10);
        verify(modelListener, times(0)).highlightingChange(true);
        verify(modelListener, times(0)).selectionChange(true);
        verify(modelListener, times(0)).contentChanged();

        model.setOffset("00:12:01:050");
//        assertEquals(model.getOffset(), 721050);
        assertEquals(model.getOffsetString(), "00:12:01:050");

        model.setOffset(7092113);
//        assertEquals(model.getOffset(), 7092113);
        assertEquals(model.getOffsetString(), "01:58:12:113");
    }

    @Test
    public void testSetOnset() {
//        assertEquals(model.getOnset(), 0);
        assertEquals(model.getOnsetString(), "00:00:00:000");

        model.setOnset(20);
//        assertEquals(model.getOnset(), 20);
        assertEquals(model.getOnsetString(), "00:00:00:020");
        verify(modelListener).onsetChanged(20);
        verify(modelListener, times(0)).offsetChanged(20);
        verify(modelListener, times(0)).highlightingChange(true);
        verify(modelListener, times(0)).selectionChange(true);
        verify(modelListener, times(0)).contentChanged();

        model.setOnset("00:13:04:890");
//        assertEquals(model.getOnset(), 784890);
        assertEquals(model.getOnsetString(), "00:13:04:890");

        model.setOnset(17999999);
//        assertEquals(model.getOnset(), 17999999);
        assertEquals(model.getOnsetString(), "04:59:59:999");
    }

    /** A code set in the model reaches the cell's views, as a quick key's does. */
    @Test
    public void settingACodeTellsTheCellsListeners() {
        model.setValue(0, "smile");

        verify(modelListener).contentChanged();
    }

    @Test
    public void aCodeLeftAsItWasTellsNothing() {
        model.setValue(0, "smile");
        model.setValue(0, "smile");
        var.setLocked(true);
        model.setValue(0, "frown");

        verify(modelListener, times(1)).contentChanged();
    }

    /** What the player finds of a clip's file is told to its views, once, so its text says so. */
    @Test
    public void whatThePlayerFindsOfAClipsFileTellsTheCellsListeners() throws Exception {
        Cell clip = ds.createMediaVariable("media_1").createClip(0, MediaClip.of("/study/cam.mp4"));
        CellListener listener = mock(CellListener.class);
        clip.addListener(listener);

        assertEquals(clip.getClipFile(), Cell.ClipFile.FOUND, "not looked for yet");
        clip.setClipFile(Cell.ClipFile.FAILED);
        clip.setClipFile(Cell.ClipFile.FAILED);

        assertEquals(clip.getClipFile(), Cell.ClipFile.FAILED);
        verify(listener, times(1)).contentChanged();
        assertThrows(IllegalStateException.class, () -> model.setClipFile(Cell.ClipFile.FAILED));
    }
}
