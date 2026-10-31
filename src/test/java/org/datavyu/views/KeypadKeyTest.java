package org.datavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.datavyu.views.discrete.SpreadsheetTheme;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
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

/**
 * The keypad's keys show a glyph, a label that fits, and the keypad key that does the same.
 */
public class KeypadKeyTest {

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
        JComponent controls = new VideoController().getControls();
        controls.setSize(controls.getPreferredSize());
        layOut(controls);
        List<KeypadKey> keys = new ArrayList<>();
        collect(controls, keys);

        assertEquals(keys.stream().map(KeypadKey::getKey).toList(),
                List.of("7", "8", "9", "−", "4", "5", "6", "+", "1", "2", "3", "enter", "0"));
        for (KeypadKey key : keys) {
            assertTrue(key.getIcon() instanceof Glyph, key.getName());
            assertTrue(!key.getKey().isEmpty(), key.getName());
            FontMetrics metrics = key.getFontMetrics(key.getFont());
            Insets insets = key.getInsets();
            int room = key.getWidth() - insets.left - insets.right;
            assertTrue(metrics.stringWidth(key.getText()) <= room,
                    key.getName() + ": \"" + key.getText() + "\" needs " + metrics.stringWidth(key.getText())
                            + " of " + room + " pixels");
        }
    }

    /**
     * The keys sit as on a Mac's keypad: 45-pixel squares 5 pixels apart in rows and columns, enter two rows high and 0
     * two wide. The size includes the focus ring, as in Datavyu: MigLayout's visual padding, which would widen each key
     * by its ring, is off.
     */
    @Test
    public void theKeysSitInAKeypadGrid() {
        JComponent controls = new VideoController().getControls();
        controls.setSize(controls.getPreferredSize());
        layOut(controls);
        List<KeypadKey> keys = new ArrayList<>();
        collect(controls, keys);
        Map<String, Rectangle> at = new HashMap<>();
        for (KeypadKey key : keys) {
            at.put(key.getKey(), key.getBounds());
        }

        for (String key : List.of("7", "8", "9", "−", "4", "5", "6", "+", "1", "2", "3")) {
            assertEquals(at.get(key).getSize(), new Dimension(45, 45), key);
        }
        assertEquals(at.get("enter").getSize(), new Dimension(45, 95));
        assertEquals(at.get("0").getSize(), new Dimension(95, 45));
        for (List<String> row : List.of(List.of("7", "8", "9"), List.of("4", "5", "6", "+"), List.of("1", "2", "3",
                "enter"))) {
            for (int i = 1; i < row.size(); i++) {
                assertEquals(at.get(row.get(i)).y, at.get(row.get(0)).y, row.get(i) + " in the row of " + row.get(0));
                String key = row.get(i);
                String left = row.get(i - 1);
                assertEquals(at.get(key).x, at.get(left).x + 50, key + " right of " + left);
            }
        }
        for (List<String> column : List.of(List.of("7", "4", "1", "0"), List.of("8", "5", "2"), List.of("9", "6",
                "3"), List.of("−", "+", "enter"))) {
            for (int i = 1; i < column.size(); i++) {
                String key = column.get(i);
                String above = column.get(i - 1);
                assertEquals(at.get(key).x, at.get(above).x, key + " under " + above);
                assertEquals(at.get(key).y, at.get(above).y + 50, key + " below " + above);
            }
        }
        assertEquals(at.get("enter").y + at.get("enter").height, at.get("0").y + at.get("0").height,
                "enter reaches the row of 0");
    }

    /** FlatLaf's native library is a restricted call Java 25 warns about, and Watavyu needs nothing from it. */
    @Test
    public void theLookAndFeelLoadsNoNativeLibrary() {
        assertEquals(System.getProperty("flatlaf.useNativeLibrary"), "false");
    }

    @Test
    public void aKeySaysWhichKeypadKeyDoesTheSame() {
        KeypadKey key = new KeypadKey(Glyph.Shape.PLAY, "play", "8");
        assertEquals(key.getKey(), "8");
        assertEquals(key.getText(), "play");
        assertEquals(key.getAccessibleContext().getAccessibleDescription(), "Keypad 8");
    }

    private static void layOut(final Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layOut(child);
            }
        }
    }

    private static void collect(final Component component, final List<KeypadKey> keys) {
        if (component instanceof KeypadKey key) {
            keys.add(key);
        } else if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, keys);
            }
        }
    }
}
