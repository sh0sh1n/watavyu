package org.datavyu.views;

import static org.testng.Assert.assertTrue;

import org.datavyu.TestSpreadsheets;
import org.datavyu.views.discrete.SpreadsheetTheme;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.text.JTextComponent;

/**
 * In the main window the menu bar runs a menu shortcut only once the focused component has passed over its key. A
 * cell's values and times are text, which takes some keys for itself, as ⌘\ to unselect, so a command on such a key
 * would never run from a cell: it is run by the dispatcher first instead (a global shortcut).
 */
public class MenuShortcutKeysTest {

    private LookAndFeel before;

    /** Text takes the keys its look and feel binds, which differ from the test default's. */
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
    public void noMenuShortcutIsAKeyTextTakes() throws Exception {
        List<String> taken = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame();
            try {
                Shortcuts shortcuts = TestSpreadsheets.mainWindow(frame).getShortcuts();
                List<JTextComponent> texts = List.of(new JTextArea(), new JTextField());
                for (Shortcuts.Shortcut shortcut : shortcuts.all()) {
                    if (shortcut.getScope() != Shortcuts.Scope.MENU) {
                        continue;
                    }
                    for (JTextComponent text : texts) {
                        // A key bound to no action the text has goes on to the menu bar.
                        Object binding = text.getInputMap(JComponent.WHEN_FOCUSED).get(shortcut.getKey());
                        Action action = binding == null ? null : text.getActionMap().get(binding);
                        if (action != null && action.isEnabled()) {
                            taken.add(shortcut.getKeyText() + " (" + binding + ")");
                        }
                    }
                }
            } finally {
                frame.dispose();
            }
        });
        assertTrue(taken.isEmpty(), "menu shortcuts that text takes: " + taken);
    }
}
