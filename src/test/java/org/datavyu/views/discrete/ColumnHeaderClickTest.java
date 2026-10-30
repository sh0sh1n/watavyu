package org.datavyu.views.discrete;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.Datavyu;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.views.VideoController;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.SwingUtilities;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/** Clicking a column's header selects it; columns are renamed only in the Code Editor, so a double-click does not. */
public class ColumnHeaderClickTest {

    private VideoController previousController;
    private SpreadsheetColumn header;

    @BeforeMethod
    public void setUp() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        SwingUtilities.invokeAndWait(() -> {
            previousController = Datavyu.getVideoController();
            Datavyu.setVideoController(new VideoController());
            try {
                header = new SpreadsheetColumn(dataStore, dataStore.createVariable("notes"),
                    mock(CellSelectionListener.class), mock(ColumnSelectionListener.class),
                    mock(ColumnVisibilityListener.class));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @AfterMethod
    public void tearDown() throws Exception {
        SwingUtilities.invokeAndWait(() -> Datavyu.setVideoController(previousController));
    }

    private MouseEvent click(final int count) {
        return new MouseEvent(header, MouseEvent.MOUSE_CLICKED, 0, InputEvent.BUTTON1_DOWN_MASK, 5, 5, count, false,
            MouseEvent.BUTTON1);
    }

    @Test
    public void aClickTogglesTheSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            header.setSelected(false);
            header.mouseClicked(click(1));
            assertTrue(header.isSelected());
            header.mouseClicked(click(1));
            assertFalse(header.isSelected());
        });
    }

    /** The double-click once opened a rename dialog; now its second click leaves the first click's selection. */
    @Test
    public void aDoubleClickSelectsOnceAndRenamesNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            header.setSelected(false);
            header.mouseClicked(click(1));
            header.mouseClicked(click(2));
            assertTrue(header.isSelected());
            assertEquals(header.getText(), "notes");
        });
    }
}
