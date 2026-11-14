package com.sh0sh1n.watavyu.views.spreadsheet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.TestSpreadsheets;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Optional;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * The sheet draws its cells where the layout puts them and puts one editor over the cell in focus: a drawn cell and
 * its editor are the same size, a press on a drawn cell moves the editor there and selects the cell, and the sheet is
 * laid out again when a cell changes in time or size, or its column is hidden.
 */
public class SpreadsheetViewTest {

    private static final String LONG_VALUE =
        "a value long enough to wrap onto several lines in a column of the usual width, and then some more";

    private Column gaze;
    private Cell first;
    private Cell second;
    private SpreadSheetPanel panel;
    private SpreadsheetView sheet;
    private JFrame frame;

    @BeforeMethod
    public void setUp() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        gaze = dataStore.createColumn("gaze");
        gaze.addCode();
        first = gaze.createPointCell(1_000);
        second = gaze.createPointCell(5_000);
        first.setSelected(false);
        second.setSelected(false);
        SwingUtilities.invokeAndWait(() -> {
            panel = new SpreadSheetPanel(TestSpreadsheets.withPlayback(dataStore), null);
            sheet = panel.getSheet();
            frame = new JFrame();
            frame.add(panel);
            frame.addNotify();
            frame.setSize(1_000, 700);
            frame.validate();
        });
    }

    @AfterMethod
    public void tearDown() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel.close();
            frame.dispose();
        });
    }

    private Rectangle bounds(final Cell cell) {
        return sheet.boundsOf(cell).orElseThrow();
    }

    private static Point centre(final Rectangle bounds) {
        return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
    }

    /** The drawn cell and its editor share their parts' layout, so the editor covers the cell exactly. */
    @Test
    public void anEditorIsTheSizeOfTheCellDrawnBeneathIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            first.setValue(0, LONG_VALUE);
            frame.validate();
            SpreadsheetCell editor = sheet.edit(first);
            frame.validate();

            assertEquals(editor.getBounds(), bounds(first));
            assertEquals(editor.getPreferredSize().height, sheet.heightOf(first));
        });
    }

    @Test
    public void aPressOnADrawnCellMovesTheEditorThereAndSelectsTheCell() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            sheet.edit(first);
            Point at = centre(bounds(second));
            sheet.dispatchEvent(new MouseEvent(sheet, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK,
                at.x, at.y, 1, false, MouseEvent.BUTTON1));

            assertEquals(sheet.getEditor().map(SpreadsheetCell::getCell), Optional.of(second));
            assertTrue(second.isSelected());
            assertFalse(first.isSelected());
        });
    }

    /** A right-click leaves the selection, and the editor, where they are. */
    @Test
    public void aRightClickOnADrawnCellLeavesTheEditorWhereItIs() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            sheet.edit(first);
            Point at = centre(bounds(second));
            sheet.dispatchEvent(new MouseEvent(sheet, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON3_DOWN_MASK,
                at.x, at.y, 1, true, MouseEvent.BUTTON3));

            assertEquals(sheet.getEditor().map(SpreadsheetCell::getCell), Optional.of(first));
            assertFalse(second.isSelected());
        });
    }

    @Test
    public void cellAtFindsTheCellDrawnAtAPoint() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Rectangle below = bounds(second);

            assertEquals(sheet.cellAt(centre(bounds(first))), Optional.of(first));
            assertEquals(sheet.cellAt(centre(below)), Optional.of(second));
            assertEquals(sheet.cellAt(new Point(below.x + 1, below.y + below.height + 50)), Optional.empty());
        });
    }

    /** A cell moved in time is placed again, though nothing about the sheet's components changed. */
    @Test
    public void aCellsNewOnsetLaysTheSheetOutAgain() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            first.setOnset(9_000);
            first.setOffset(9_000);
            frame.validate();

            assertTrue(bounds(first).y > bounds(second).y, "the moved cell now comes after the other");
        });
    }

    @Test
    public void aCellWhoseValuesGrowIsDrawnTaller() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            int before = bounds(second).height;
            second.setValue(0, LONG_VALUE);
            frame.validate();

            assertTrue(bounds(second).height > before, bounds(second).height + " > " + before);
        });
    }

    /** Hiding a column stops its cells being drawn, and takes the editor off the cell it was over. */
    @Test
    public void theCellsOfAHiddenColumnAreNotDrawn() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            sheet.edit(first);
            gaze.setHidden(true);
            frame.validate();

            assertEquals(sheet.boundsOf(first), Optional.empty());
            assertEquals(sheet.cellAt(new Point(5, 5)), Optional.empty());
            assertEquals(sheet.getEditor(), Optional.empty());
        });
    }

    /** Deleting the cell under the editor takes the editor off, and selects no other cell. */
    @Test
    public void deletingTheCellUnderTheEditorTakesItOff() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            sheet.edit(first);
            gaze.removeCell(first);
            frame.validate();

            assertEquals(sheet.getEditor(), Optional.empty());
            assertEquals(sheet.boundsOf(first), Optional.empty());
            assertFalse(second.isSelected());
        });
    }

    /**
     * A new cell is drawn, and the editor stays where it was: the commands that go into a new cell move it
     * ({@code SheetModesInPanelTest}).
     */
    @Test
    public void aNewCellIsDrawnAndLeavesTheEditor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            sheet.edit(first);
            Cell third = gaze.createPointCell(3_000);
            frame.validate();

            assertNotEquals(sheet.boundsOf(third), Optional.empty());
            assertEquals(sheet.getEditor().map(SpreadsheetCell::getCell), Optional.of(first));
        });
    }
}
