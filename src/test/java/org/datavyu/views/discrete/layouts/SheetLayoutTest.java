package org.datavyu.views.discrete.layouts;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.datavyu.views.discrete.SpreadsheetCell;
import org.datavyu.views.discrete.SpreadsheetColumn;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;

/**
 * A sheet's cells are placed again when something in the sheet changes or its scroll pane resizes, and not when it
 * scrolls (B15): placing every cell on each scroll step stalled scrolling and following the needle.
 */
public class SheetLayoutTest {

    /** Counts how often it places the cells. */
    private static final class CountingLayout extends SheetLayout {
        int placed;

        @Override
        protected void layoutCells(final Container parent) {
            placed++;
        }

        @Override
        public void reorientView(final SpreadsheetCell cell) {
        }

        @Override
        public void reorientView(final SpreadsheetColumn column) {
        }
    }

    private JFrame frame;
    private CountingLayout layout;
    private JScrollPane pane;
    private JPanel cell;

    /** How often the cells were placed while the sheet was first sized and laid out. */
    private int placedBySetUp;

    @BeforeMethod
    public void setUp() throws Exception {
        SwingUtilities.invokeAndWait(this::build);
        // Counted after the layouts that building queued on the EDT.
        SwingUtilities.invokeAndWait(() -> placedBySetUp = layout.placed);
    }

    private void build() {
        JPanel sheet = new JPanel(null);
        sheet.setPreferredSize(new Dimension(300, 5_000));
        cell = new JPanel();
        cell.setBounds(0, 0, 300, 40);
        sheet.add(cell);
        pane = new JScrollPane(sheet);
        layout = new CountingLayout();
        pane.setLayout(layout);
        // Swing lays out and keeps validity only for components with native peers; the frame is never shown.
        frame = new JFrame();
        frame.add(pane);
        frame.addNotify();
        pane.setSize(400, 300);
        pane.validate();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        SwingUtilities.invokeAndWait(frame::dispose);
    }

    @Test
    public void theCellsArePlacedWhenTheSheetIsFirstLaidOut() {
        assertTrue(placedBySetUp >= 1);
    }

    /** Runs a change on the EDT, as Swing revalidates there, and lays the scroll pane out after it. */
    private void after(final Runnable change) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            change.run();
            pane.validate();
        });
    }

    @Test
    public void scrollingDoesNotPlaceTheCellsAgain() throws Exception {
        for (int y = 10; y <= 1_000; y += 10) {
            int top = y;
            after(() -> pane.getViewport().setViewPosition(new Point(0, top)));
        }
        assertEquals(pane.getViewport().getViewPosition().y, 1_000, "the sheet scrolled");
        assertEquals(layout.placed, placedBySetUp);
    }

    @Test
    public void aChangeInTheSheetPlacesTheCellsAgain() throws Exception {
        after(cell::revalidate);
        assertEquals(layout.placed, placedBySetUp + 1);
    }

    @Test
    public void resizingThePanePlacesTheCellsAgain() throws Exception {
        after(() -> pane.setSize(400, 600));
        assertEquals(layout.placed, placedBySetUp + 1);
    }

    @Test
    public void aNewLayoutPlacesTheCells() throws Exception {
        CountingLayout next = new CountingLayout();
        after(() -> pane.setLayout(next));
        assertEquals(next.placed, 1);
    }
}
