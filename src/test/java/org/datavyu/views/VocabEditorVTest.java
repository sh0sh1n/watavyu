package org.datavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.db.Variable;
import org.datavyu.models.project.Project;
import org.datavyu.views.discrete.EditorComponent;
import org.datavyu.views.discrete.datavalues.vocabelements.FormalArgEditor;
import org.datavyu.views.discrete.datavalues.vocabelements.VocabElementV;
import org.testng.annotations.Test;

import java.awt.Component;
import java.awt.Container;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
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

    /** A name the column refuses stays in its field, marked, and the column keeps the last good one. */
    @Test
    public void aRefusedNameIsShownAsRefused() throws Exception {
        ProjectController spreadsheet = spreadsheet();
        Variable gaze = spreadsheet.getDataStore().createVariable("gaze");
        gaze.addArgument();
        AtomicReference<VocabEditorV> editor = new AtomicReference<>();
        AtomicReference<List<String>> refused = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            editor.set(new VocabEditorV(null, false, spreadsheet));
            VocabElementV column = editor.get().getVocabElements().get(0);
            type(column, column.getNameComponent(), "1st");
            type(column, column.getArgumentView(gaze.getRootNode().childArguments.get(0)), "code02");
            refused.set(column.refusedNames());
        });
        try {
            assertEquals(refused.get(), List.of("1st", "code02"));
            assertEquals(gaze.getName(), "gaze");
            assertEquals(gaze.getRootNode().childArguments.get(0).name, "code0");
        } finally {
            SwingUtilities.invokeAndWait(() -> editor.get().dispose());
        }
    }

    /**
     * A code selected in the editor is forgotten when a change from elsewhere, such as an undo, rebuilds its column, so
     * Delete and the arrows do not act on a code that has moved or gone.
     */
    @Test
    public void aSelectedCodeIsForgottenWhenAnUndoRebuildsItsColumn() throws Exception {
        ProjectController spreadsheet = spreadsheet();
        Variable gaze = spreadsheet.getDataStore().createVariable("gaze");
        gaze.addArgument();
        AtomicReference<VocabEditorV> editor = new AtomicReference<>();
        AtomicReference<FormalArgEditor> before = new AtomicReference<>();
        AtomicReference<FormalArgEditor> after = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            editor.set(new VocabEditorV(null, false, spreadsheet));
            VocabElementV column = editor.get().getVocabElements().get(0);
            type(column, column.getArgumentView(gaze.getRootNode().childArguments.get(1)), "side");
            before.set(column.getArgWithFocus());
            spreadsheet.undo();
            after.set(column.getArgWithFocus());
        });
        try {
            assertEquals(before.get().getArgPos(), 1);
            assertNull(after.get());
        } finally {
            SwingUtilities.invokeAndWait(() -> editor.get().dispose());
        }
    }

    /** The rows follow the spreadsheet's columns as they are deleted, put back by undo and moved, from anywhere. */
    @Test
    public void theRowsFollowTheSpreadsheetsColumns() throws Exception {
        ProjectController spreadsheet = spreadsheet();
        Variable gaze = spreadsheet.getDataStore().createVariable("gaze");
        Variable speech = spreadsheet.getDataStore().createVariable("speech");
        AtomicReference<VocabEditorV> editor = new AtomicReference<>();
        List<List<Variable>> rows = new ArrayList<>();
        Runnable look = () -> rows.add(rows(editor.get()));
        SwingUtilities.invokeAndWait(() -> {
            editor.set(new VocabEditorV(null, false, spreadsheet));
            VocabElementV column = editor.get().getVocabElements().get(0);
            // A refused name in its field once kept a deleted column's row.
            type(column, column.getNameComponent(), "1st");
            spreadsheet.columns().delete(List.of(gaze));
            look.run();
            spreadsheet.undo();
            look.run();
            spreadsheet.columns().move(gaze, speech);
            look.run();
        });
        try {
            assertEquals(rows, List.of(List.of(speech), List.of(gaze, speech), List.of(speech, gaze)));
        } finally {
            SwingUtilities.invokeAndWait(() -> editor.get().dispose());
        }
    }

    /** Makes a column, from inside a Swing task, which cannot throw a checked exception. */
    private static Variable column(final ProjectController spreadsheet, final String name) {
        try {
            return spreadsheet.getDataStore().createVariable(name);
        } catch (UserWarningException e) {
            throw new AssertionError(e);
        }
    }

    private static List<Variable> rows(final VocabEditorV editor) {
        return editor.getVocabElements().stream().map(VocabElementV::getVariable).toList();
    }

    /** A column added elsewhere gets a row of its own; the other rows keep what is typed in them, refused or not. */
    @Test
    public void aNewColumnLeavesTheOtherRowsAsTheyAre() throws Exception {
        ProjectController spreadsheet = spreadsheet();
        Variable gaze = spreadsheet.getDataStore().createVariable("gaze");
        AtomicReference<VocabEditorV> editor = new AtomicReference<>();
        AtomicReference<VocabElementV> before = new AtomicReference<>();
        AtomicReference<Variable> added = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            editor.set(new VocabEditorV(null, false, spreadsheet));
            before.set(editor.get().getVocabElements().get(0));
            type(before.get(), before.get().getNameComponent(), "1st");
            added.set(column(spreadsheet, "speech"));
        });
        try {
            assertSame(editor.get().getVocabElements().get(0), before.get());
            assertEquals(before.get().refusedNames(), List.of("1st"));
            assertEquals(rows(editor.get()), List.of(gaze, added.get()));
        } finally {
            SwingUtilities.invokeAndWait(() -> editor.get().dispose());
        }
    }

    /** The editor follows the selected tab's columns only, and catches up with changes made while it was closed. */
    @Test
    public void theEditorFollowsTheSpreadsheetItShowsWhileOpen() throws Exception {
        ProjectController first = spreadsheet();
        ProjectController second = spreadsheet();
        Variable gaze = second.getDataStore().createVariable("gaze");
        AtomicReference<VocabEditorV> editor = new AtomicReference<>();
        AtomicReference<Variable> early = new AtomicReference<>();
        AtomicReference<Variable> speech = new AtomicReference<>();
        List<List<Variable>> seen = new ArrayList<>();
        AtomicReference<List<String>> codes = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            editor.set(new VocabEditorV(null, false, first));
            editor.get().setProjectController(second);
            column(first, "other");
            early.set(column(second, "early"));
            seen.add(rows(editor.get()));

            // Closed, the editor misses a code added, a column added, one moved and one deleted.
            editor.get().dispose();
            second.codes().add(gaze);
            speech.set(column(second, "speech"));
            second.columns().move(speech.get(), gaze);
            second.columns().delete(List.of(early.get()));
            editor.get().pack();
            seen.add(rows(editor.get()));
            codes.set(shownCodes(editor.get().getVocabElements().get(1)));
        });
        try {
            assertEquals(seen, List.of(List.of(gaze, early.get()), List.of(speech.get(), gaze)));
            assertEquals(codes.get(), List.of("code01", "code02"));
        } finally {
            SwingUtilities.invokeAndWait(() -> editor.get().dispose());
        }
    }

    private static String warnings(final VocabEditorV editor) {
        return findLabel(editor, "nameWarningLabel").getText();
    }

    private static JLabel findLabel(final Container parent, final String name) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JLabel label && name.equals(label.getName())) {
                return label;
            }
            if (child instanceof Container container) {
                JLabel found = findLabel(container, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** The editor says what was typed was applied, and says nothing for a change made elsewhere. */
    @Test
    public void theWarningsSayWhatWasTypedWasApplied() throws Exception {
        ProjectController spreadsheet = spreadsheet();
        spreadsheet.getDataStore().createVariable("gaze");
        AtomicReference<VocabEditorV> editor = new AtomicReference<>();
        List<String> said = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            editor.set(new VocabEditorV(null, false, spreadsheet));
            VocabElementV column = editor.get().getVocabElements().get(0);
            said.add(warnings(editor.get()));
            type(column, column.getNameComponent(), "look");
            said.add(warnings(editor.get()));
            spreadsheet.undo();
            said.add(warnings(editor.get()));
            column(spreadsheet, "speech");
            said.add(warnings(editor.get()));
        });
        try {
            assertEquals(said, List.of("", "All changes applied.", "", ""));
        } finally {
            SwingUtilities.invokeAndWait(() -> editor.get().dispose());
        }
    }
}
