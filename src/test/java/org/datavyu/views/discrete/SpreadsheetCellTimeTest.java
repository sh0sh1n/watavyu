package org.datavyu.views.discrete;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.Datavyu;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.views.VideoController;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

/**
 * A cell whose onset or offset changes asks for the sheet to be laid out again, since the sheet places cells by time
 * and is otherwise laid out only when something in it changes (B15). Its time fields are validate roots of their own,
 * so the new time alone would lay out only the field.
 */
public class SpreadsheetCellTimeTest {

    private VideoController previousController;
    private RepaintManager previousManager;
    private Cell model;
    private SpreadsheetCell cell;

    /** The components asked to be laid out again, in order. */
    private final List<JComponent> layoutRequests = new ArrayList<>();

    @BeforeMethod
    public void setUp() throws Exception {
        model = DataStoreFactory.newDataStore().createVariable("column").createPointCell(1_000);
        SwingUtilities.invokeAndWait(() -> {
            previousController = Datavyu.getVideoController();
            Datavyu.setVideoController(new VideoController());
            cell = new SpreadsheetCell(null, model, null);
            new JPanel(null).add(cell);
            previousManager = RepaintManager.currentManager(cell);
            RepaintManager.setCurrentManager(new RepaintManager() {
                @Override
                public void addInvalidComponent(final JComponent component) {
                    layoutRequests.add(component);
                }
            });
        });
    }

    @AfterMethod
    public void tearDown() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RepaintManager.setCurrentManager(previousManager);
            Datavyu.setVideoController(previousController);
        });
    }

    @Test
    public void aNewOnsetLaysTheSheetOutAgain() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            model.setOnset(5_000);
            cell.onsetChanged(5_000);
        });
        assertTrue(layoutRequests.contains(cell), "requests: " + layoutRequests);
    }

    @Test
    public void aNewOffsetLaysTheSheetOutAgain() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            model.setOffset(5_000);
            cell.offsetChanged(5_000);
        });
        assertTrue(layoutRequests.contains(cell), "requests: " + layoutRequests);
    }

    @Test
    public void aCellNotChangedInTimeAsksForNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> cell.refreshColours());
        assertFalse(layoutRequests.contains(cell));
    }
}
