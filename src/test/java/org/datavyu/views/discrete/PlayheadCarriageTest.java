package org.datavyu.views.discrete;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.views.discrete.layouts.TimeAxis;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The playhead beside the spreadsheet: its needle sits where its time falls among the cells, follows the sheet as it
 * scrolls, moves the clock when dragged, and puts its line across the columns.
 */
public class PlayheadCarriageTest {

    /** Cell times at 0 and 10 s, 1000 pixels apart in the sheet. */
    private static final TimeAxis AXIS = new TimeAxis(Map.of(0L, 0, 10_000L, 1_000));

    private JScrollPane scrollPane;
    private Optional<TimeAxis> axis;
    private List<Long> seeks;
    private List<Integer> lines;
    private boolean lineAcross;
    private PlayheadCarriage playhead;

    @BeforeMethod
    public void setUp() {
        JPanel sheet = new JPanel();
        sheet.setPreferredSize(new Dimension(300, 2_000));
        scrollPane = new JScrollPane(sheet);
        scrollPane.setBorder(null);
        axis = Optional.of(AXIS);
        seeks = new ArrayList<>();
        lines = new ArrayList<>();
        lineAcross = true;
        playhead = new PlayheadCarriage(scrollPane.getViewport(), () -> axis, seeks::add, lines::add,
                () -> lineAcross);
        JPanel window = new JPanel(new BorderLayout());
        window.add(playhead, BorderLayout.WEST);
        window.add(scrollPane, BorderLayout.CENTER);
        window.setSize(400, 300);
        layOut(window);
    }

    private static void layOut(final Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layOut(child);
            }
        }
    }

    private void show(final long time) throws Exception {
        playhead.show(time, "1×");
        SwingUtilities.invokeAndWait(() -> { });
    }

    private void press(final int y) {
        playhead.dispatchEvent(new MouseEvent(playhead, MouseEvent.MOUSE_PRESSED, 0, 0, 10, y, 1, false,
                MouseEvent.BUTTON1));
    }

    @Test
    public void theNeedleSitsWhereItsTimeFallsAmongTheCells() throws Exception {
        show(2_500);

        assertEquals(playhead.needleY(), Optional.of(250));
        assertEquals(lines.get(lines.size() - 1), Integer.valueOf(250), "the line across the columns");
        assertEquals(playhead.getTime(), 2_500L);
        assertEquals(playhead.getRate(), "1×");
    }

    @Test
    public void theNeedleScrollsWithTheCells() throws Exception {
        show(5_000);
        scrollPane.getViewport().setViewPosition(new Point(0, 400));

        assertEquals(playhead.needleY(), Optional.of(100));
    }

    @Test
    public void draggingThePlayheadMovesTheClockToTheTimeThere() {
        scrollPane.getViewport().setViewPosition(new Point(0, 400));

        press(100);

        assertEquals(seeks, List.of(5_000L));
    }

    /** With the setting off, the line stays in the playhead's strip and does not cross the columns. */
    @Test
    public void theLineAcrossTheColumnsCanBeTurnedOff() throws Exception {
        lineAcross = false;

        show(2_500);

        assertEquals(lines.get(lines.size() - 1), Integer.valueOf(-1));
        assertEquals(playhead.needleY(), Optional.of(250), "the needle stays");
    }

    /** Laid out by order, the sheet places nothing in time: no needle or line, and dragging does nothing. */
    @Test
    public void aSheetLaidOutByOrderHasNoNeedle() throws Exception {
        axis = Optional.empty();

        show(5_000);
        press(100);

        assertTrue(playhead.needleY().isEmpty());
        assertEquals(lines.get(lines.size() - 1), Integer.valueOf(-1));
        assertEquals(seeks, List.of());
    }

    /** The line across the columns is drawn over the cells, faint enough to read them through. */
    @Test
    public void theLineIsDrawnOverTheCells() {
        SpreadsheetView sheet = new SpreadsheetView();
        sheet.setLayout(new BorderLayout());
        JPanel cell = new JPanel();
        cell.setBackground(Color.WHITE);
        sheet.add(cell);
        sheet.setSize(50, 50);
        layOut(sheet);
        assertTrue(sheet.isOptimizedDrawingEnabled(), "without a line, cells paint by themselves");

        sheet.setPlayheadY(20);
        BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        sheet.paint(image.getGraphics());

        assertFalse(sheet.isOptimizedDrawingEnabled(), "with a line, a cell repaints through the sheet");
        Color onLine = new Color(image.getRGB(25, 20));
        Color offLine = new Color(image.getRGB(25, 30));
        assertEquals(offLine, Color.WHITE);
        assertTrue(onLine.getRed() > onLine.getGreen() + 60, "reddish: " + onLine);
        assertTrue(onLine.getGreen() > 100, "the cell shows through: " + onLine);
    }
}
