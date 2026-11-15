package com.sh0sh1n.watavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.TestSpreadsheets;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.views.spreadsheet.SpreadsheetTheme;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * The controls drawer's keys show a glyph, a label that fits, and the key of the key map that does the same; they sit
 * in the shape of a numeric keypad.
 */
public class DrawerKeyTest {

    private LookAndFeel before;

    /** Keys are measured in the application's look and feel, whose insets differ from the test default's. */
    @BeforeClass
    public void installTheApplicationsLook() {
        before = UIManager.getLookAndFeel();
        SpreadsheetTheme.installLookAndFeel();
    }

    @AfterClass
    public void restoreTheLook() throws UnsupportedLookAndFeelException {
        UIManager.setLookAndFeel(before);
    }

    @Test
    public void everyKeyOfTheDrawerIsAGlyphKeyWhoseLabelFits() {
        JComponent controls = TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore()).getVideoController().getControls();
        controls.setSize(controls.getPreferredSize());
        layOut(controls);
        List<DrawerKey> keys = new ArrayList<>();
        collect(controls, keys);

        assertEquals(keys.size(), 12);
        for (DrawerKey key : keys) {
            assertTrue(key.getIcon() instanceof Glyph, key.getName());
            FontMetrics metrics = key.getFontMetrics(key.getFont());
            Insets insets = key.getInsets();
            int room = key.getWidth() - insets.left - insets.right;
            assertTrue(metrics.stringWidth(key.getText()) <= room,
                    key.getName() + ": \"" + key.getText() + "\" needs " + metrics.stringWidth(key.getText())
                            + " of " + room + " pixels");
        }
    }

    /**
     * The keys sit as on a Mac's keypad, as in Datavyu, though no keypad key runs them: 45-pixel squares 5 pixels apart
     * in rows and columns, new cell (where enter was) two rows high and new cell setting the offset (where 0 was) two
     * wide. Where 2 was is a blank, as play and pause are one key. Each key is named here by the keypad key it stands
     * where. The size includes the focus ring, as in Datavyu: MigLayout's visual padding, which would widen each key
     * by its ring, is off.
     */
    @Test
    public void theKeysSitInAKeypadGrid() {
        JComponent controls = TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore()).getVideoController().getControls();
        controls.setSize(controls.getPreferredSize());
        layOut(controls);
        List<DrawerKey> keys = new ArrayList<>();
        collect(controls, keys);
        Map<String, String> where = Map.ofEntries(Map.entry("setCellOnsetButton", "7"),
            Map.entry("playPauseButton", "8"), Map.entry("setCellOffsetButton", "9"), Map.entry("jumpByButton", "−"),
            Map.entry("shuttleBackButton", "4"), Map.entry("resetRateButton", "5"),
            Map.entry("shuttleForwardButton", "6"), Map.entry("findButton", "+"), Map.entry("jogBackButton", "1"),
            Map.entry("jogForwardButton", "3"), Map.entry("createNewCellButton", "enter"),
            Map.entry("createNewCellAndSetOffsetButton", "0"));
        Map<String, Rectangle> at = new HashMap<>();
        for (DrawerKey key : keys) {
            at.put(where.get(key.getName()), key.getBounds());
        }

        for (String key : List.of("7", "8", "9", "−", "4", "5", "6", "+", "1", "3")) {
            assertEquals(at.get(key).getSize(), new Dimension(45, 45), key);
        }
        assertEquals(at.get("enter").getSize(), new Dimension(45, 95));
        assertEquals(at.get("0").getSize(), new Dimension(95, 45));
        for (List<String> row : List.of(List.of("7", "8", "9"), List.of("4", "5", "6", "+"), List.of("3", "enter"))) {
            for (int i = 1; i < row.size(); i++) {
                assertEquals(at.get(row.get(i)).y, at.get(row.get(0)).y, row.get(i) + " in the row of " + row.get(0));
                String key = row.get(i);
                String left = row.get(i - 1);
                assertEquals(at.get(key).x, at.get(left).x + 50, key + " right of " + left);
            }
        }
        for (List<String> column : List.of(List.of("7", "4", "1", "0"), List.of("8", "5"), List.of("9", "6",
                "3"), List.of("−", "+", "enter"))) {
            for (int i = 1; i < column.size(); i++) {
                String key = column.get(i);
                String above = column.get(i - 1);
                assertEquals(at.get(key).x, at.get(above).x, key + " under " + above);
                assertEquals(at.get(key).y, at.get(above).y + 50, key + " below " + above);
            }
        }
        assertEquals(at.get("3").y, at.get("1").y, "3 in the row of 1");
        assertEquals(at.get("3").x, at.get("1").x + 100, "3 two keys right of 1");
        assertEquals(at.get("enter").y + at.get("enter").height, at.get("0").y + at.get("0").height,
                "enter reaches the row of 0");
    }

    /** FlatLaf's native library is a restricted call Java 25 warns about, and Watavyu needs nothing from it. */
    @Test
    public void theLookAndFeelLoadsNoNativeLibrary() {
        assertEquals(System.getProperty("flatlaf.useNativeLibrary"), "false");
    }

    @Test
    public void aKeySaysWhichKeyOfTheKeyMapDoesTheSame() {
        DrawerKey key = new DrawerKey(Glyph.Shape.PLAY, "play");
        assertEquals(key.getKey(), "", "none until told");
        key.showKey("Space");
        assertEquals(key.getKey(), "Space");
        assertEquals(key.getText(), "play");
        assertEquals(key.getAccessibleContext().getAccessibleDescription(), "Key: Space");
    }

    /** In the main window, the drawer's keys show the key map's keys, as the user has the map, or none. */
    @Test
    public void theDrawerShowsTheKeyMapsKeys() throws Exception {
        Map<String, String> shown = new HashMap<>();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            javax.swing.JFrame frame = new javax.swing.JFrame();
            try {
                MainWindow window = TestSpreadsheets.mainWindow(frame);
                List<DrawerKey> keys = new ArrayList<>();
                collect(window.selectedController().getVideoController().getControls(), keys);
                keys.forEach(key -> shown.put(key.getName(), key.getKey()));
            } finally {
                frame.dispose();
            }
        });

        Map<String, String> expected = new HashMap<>();
        expected.put("setCellOnsetButton", "[");
        expected.put("playPauseButton", "Space");
        expected.put("setCellOffsetButton", "]");
        expected.put("jumpByButton", "b");
        expected.put("shuttleBackButton", "<");
        expected.put("resetRateButton", "=");
        expected.put("shuttleForwardButton", ">");
        expected.put("findButton", "");
        expected.put("jogBackButton", ",");
        expected.put("jogForwardButton", ".");
        expected.put("createNewCellButton", "");
        expected.put("createNewCellAndSetOffsetButton", "");
        assertEquals(shown, expected);
    }

    private static void layOut(final Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layOut(child);
            }
        }
    }

    private static void collect(final Component component, final List<DrawerKey> keys) {
        if (component instanceof DrawerKey key) {
            keys.add(key);
        } else if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, keys);
            }
        }
    }
}
