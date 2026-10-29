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
import java.awt.FontMetrics;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

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

        assertEquals(keys.size(), 15);
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
