package org.datavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.project.Project;
import org.testng.annotations.Test;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * The window has one Code Editor, which edits the selected tab's spreadsheet and takes keys only while it is the active
 * window. (Each editor ever made used to keep its keys and its first spreadsheet, so ⌃M added columns to old ones.)
 */
public class VocabEditorVTest {

    private static ProjectController spreadsheet() {
        return new ProjectController(new Project(), DataStoreFactory.newDataStore());
    }

    @Test
    public void theEditorEditsTheSpreadsheetItIsGiven() throws Exception {
        ProjectController first = spreadsheet();
        ProjectController second = spreadsheet();
        AtomicReference<VocabEditorV> editor = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            editor.set(new VocabEditorV(null, false, first));
            editor.get().setProjectController(second);
            editor.get().addColumn();
        });
        try {
            assertSame(editor.get().getProjectController(), second);
            assertEquals(first.getDataStore().getAllVariables().size(), 0);
            assertEquals(second.getDataStore().getAllVariables().size(), 1);
            assertTrue(second.getUndoManager().canUndo());
        } finally {
            SwingUtilities.invokeAndWait(() -> editor.get().dispose());
        }
    }

    @Test
    public void anEditorThatIsNotActiveTakesNoKeys() throws Exception {
        ProjectController spreadsheet = spreadsheet();
        AtomicReference<VocabEditorV> editor = new AtomicReference<>();
        AtomicBoolean passedOn = new AtomicBoolean();
        KeyboardFocusManager focus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        KeyEventDispatcher probe = event -> {
            passedOn.set(true);
            return false;
        };
        SwingUtilities.invokeAndWait(() -> {
            editor.set(new VocabEditorV(null, false, spreadsheet));
            focus.addKeyEventDispatcher(probe);
            // ⌃M adds a column in the active editor.
            focus.dispatchEvent(new KeyEvent(new JTextField(), KeyEvent.KEY_RELEASED, 0, InputEvent.CTRL_DOWN_MASK,
                KeyEvent.VK_M, KeyEvent.CHAR_UNDEFINED));
        });
        try {
            assertTrue(passedOn.get(), "the key reached the dispatchers");
            assertEquals(spreadsheet.getDataStore().getAllVariables().size(), 0);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                focus.removeKeyEventDispatcher(probe);
                editor.get().dispose();
            });
        }
    }
}
