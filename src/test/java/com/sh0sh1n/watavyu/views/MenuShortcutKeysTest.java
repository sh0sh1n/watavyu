package com.sh0sh1n.watavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.TestSpreadsheets;
import com.sh0sh1n.watavyu.views.spreadsheet.SpreadsheetTheme;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
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

    /**
     * B5: ⌘← and ⌘→, which text takes to go to a line's ends, select columns, so they are global; plain and other
     * arrows are left to the cell.
     */
    @Test
    public void onlyCommandArrowsSelectColumns() throws Exception {
        List<KeyStroke> keys = List.of(
            KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.META_DOWN_MASK),
            KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.META_DOWN_MASK),
            KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0),
            KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
            KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.META_DOWN_MASK));
        List<String> runs = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame();
            try {
                Shortcuts shortcuts = TestSpreadsheets.mainWindow(frame).getShortcuts();
                for (KeyStroke key : keys) {
                    runs.add(shortcuts.all().stream().filter(shortcut -> key.equals(shortcut.getKey()))
                        .map(shortcut -> shortcut.getScope() + " " + shortcut.getCommand()).findFirst().orElse("none"));
                }
            } finally {
                frame.dispose();
            }
        });

        assertEquals(runs, List.of("GLOBAL SELECT_COLUMN_LEFT", "GLOBAL SELECT_COLUMN_RIGHT", "none", "none", "none",
            "none"));
    }
}
