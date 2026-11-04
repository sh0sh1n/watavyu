package org.datavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.Variable;
import org.datavyu.models.project.Project;
import org.datavyu.views.discrete.EditorComponent;
import org.datavyu.views.discrete.datavalues.vocabelements.FormalArgEditor;
import org.datavyu.views.discrete.datavalues.vocabelements.VocabElementV;
import org.testng.annotations.Test;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
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

    /** Types a name into an editor of the Code Editor, replacing what it shows, as a user does a key at a time. */
    private static void type(final VocabElementV column, final EditorComponent field, final String text) {
        column.getDataView().requestFocus();
        column.getDataView().getEdTracker().setEditor(field);
        for (char key : text.toCharArray()) {
            field.keyTyped(new KeyEvent(column.getDataView(), KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, key));
        }
    }

    private static List<String> shownCodes(final VocabElementV column) {
        return column.getDataView().getEditors().stream()
            .filter(FormalArgEditor.class::isInstance).map(EditorComponent::getText).toList();
    }

    /** A column's name typed in the editor is one edit, and undoing it shows the old name again. */
    @Test
    public void aColumnNameTypedIsOneEditThatTheEditorFollowsBack() throws Exception {
        ProjectController spreadsheet = spreadsheet();
        Variable gaze = spreadsheet.getDataStore().createVariable("gaze");
        AtomicReference<VocabEditorV> editor = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            editor.set(new VocabEditorV(null, false, spreadsheet));
            VocabElementV column = editor.get().getVocabElements().get(0);
            type(column, column.getNameComponent(), "look");
        });
        try {
            assertEquals(gaze.getName(), "look");
            SwingUtilities.invokeAndWait(spreadsheet::undo);
            assertEquals(gaze.getName(), "gaze");
            assertEquals(editor.get().getVocabElements().get(0).getCurrentNameDisplay(), "gaze");
            assertTrue(!spreadsheet.getUndoManager().canUndo(), "the keys typed undo together");
        } finally {
            SwingUtilities.invokeAndWait(() -> editor.get().dispose());
        }
    }

    /** A code's name typed is one edit; a deleted code comes back in the editor and the cells on undo. */
    @Test
    public void codeEditsAreUndoneInTheEditorAndTheCells() throws Exception {
        ProjectController spreadsheet = spreadsheet();
        Variable gaze = spreadsheet.getDataStore().createVariable("gaze");
        Cell cell = gaze.createPointCell(0);
        cell.setMatrixValue(0, "left");
        AtomicReference<VocabEditorV> editor = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            editor.set(new VocabEditorV(null, false, spreadsheet));
            VocabElementV column = editor.get().getVocabElements().get(0);
            type(column, column.getArgumentView(gaze.getRootNode().childArguments.get(0)), "side");
            spreadsheet.codes().delete(gaze, 0);
        });
        try {
            VocabElementV column = editor.get().getVocabElements().get(0);
            assertEquals(shownCodes(column), List.of());
            SwingUtilities.invokeAndWait(spreadsheet::undo);
            assertEquals(shownCodes(column), List.of("side"));
            assertEquals(cell.getMatrixValue(0).toString(), "left");
            SwingUtilities.invokeAndWait(spreadsheet::undo);
            assertEquals(shownCodes(column), List.of("code01"));
            assertTrue(!spreadsheet.getUndoManager().canUndo(), "the keys typed undo together");
        } finally {
            SwingUtilities.invokeAndWait(() -> editor.get().dispose());
        }
    }
}
