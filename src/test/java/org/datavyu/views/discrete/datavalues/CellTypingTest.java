package org.datavyu.views.discrete.datavalues;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;

import org.datavyu.TestSpreadsheets;
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.CellListener;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.views.discrete.EditorComponent;
import org.datavyu.views.discrete.datavalues.TimeStampDataValueEditor.TimeStampSource;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Typing into a cell's value goes through the spreadsheet's command, so it is an edit in the undo history: one edit
 * while the cell keeps the focus, and another once it takes the focus again. Each digit typed into a time is an edit of
 * its own.
 */
public class CellTypingTest {

    private ProjectController spreadsheet;
    private Cell cell;
    private CellValuesView view;

    @BeforeMethod
    public void setUp() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        cell = dataStore.createVariable("notes").createPointCell(1_000);
        SwingUtilities.invokeAndWait(() -> {
            spreadsheet = TestSpreadsheets.withPlayback(dataStore);
            view = new CellValuesView(spreadsheet, cell);
            // As the cell's SpreadsheetCell does.
            CellListener follower = mock(CellListener.class);
            doAnswer(call -> {
                view.showValues();
                return null;
            }).when(follower).contentChanged();
            cell.addListener(follower);
            view.getEdTracker().setEditor(view.getEdTracker().firstEditor());
        });
    }

    private void type(final String keys) {
        for (char key : keys.toCharArray()) {
            view.getEdTracker().keyTyped(new KeyEvent(view, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, key));
        }
    }

    private void refocus() {
        view.focusLost(new FocusEvent(view, FocusEvent.FOCUS_LOST));
        view.focusGained(new FocusEvent(view, FocusEvent.FOCUS_GAINED));
        view.getEdTracker().setEditor(view.getEdTracker().firstEditor());
    }

    @Test
    public void typingWhileTheCellKeepsTheFocusIsOneEdit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            type("smile");
            assertEquals(cell.getValue(0), "smile");

            spreadsheet.undo();
            assertEquals(cell.getValue(0), "");
            assertFalse(spreadsheet.getUndoManager().canUndo());
        });
    }

    /** The cell tells the view of the typing, which the view shows already, so the editor typed in stays. */
    @Test
    public void typingKeepsTheEditorTypedInAndUndoRebuildsIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EditorComponent typedIn = view.getEdTracker().firstEditor();
            type("smile");
            assertSame(view.getEdTracker().firstEditor(), typedIn);
            assertEquals(view.getText(), "(smile)");

            spreadsheet.undo();
            assertNotSame(view.getEdTracker().firstEditor(), typedIn);
            assertEquals(view.getText(), "(<code01>)");
        });
    }

    /** An emptied value shows its code's name as it is now, though the code was renamed since the editor was made. */
    @Test
    public void anEmptiedValueShowsItsCodesNameNow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            type("smile");
            cell.getVariable().renameCode(0, "face");
            EditorComponent editor = view.getEdTracker().firstEditor();

            editor.setText("");
            editor.keyReleased(new KeyEvent(view, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_BACK_SPACE, '\b'));

            assertEquals(editor.getText(), "<face>");
        });
    }

    @Test
    public void typingAfterTheCellTakesTheFocusAgainIsAnotherEdit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            type("smile");
            refocus();
            type("frown");
            assertEquals(cell.getValue(0), "frown");

            spreadsheet.undo();
            assertEquals(cell.getValue(0), "smile");
        });
    }

    /** Each code has an editor of its own, and what is typed into one changes that code's value alone. */
    @Test
    public void typingIntoACodesEditorChangesThatCode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            spreadsheet.codes().add(cell.getVariable());
            // As the cell's SpreadsheetColumn does when its codes change.
            view.showValues();
            view.getEdTracker().setEditor(view.getEdTracker().getEditorAtArgIndex(1));

            type("near");

            assertEquals(cell.getValues(), List.of("", "near"));
            assertEquals(view.getText(), "(<code01>, near)");
        });
    }

    /**
     * A cell's values redrawn while it keeps the focus, as after an undo or a change from elsewhere, keep the typing in
     * the code it was in, at the caret's place there, though the text before it changed length.
     */
    @Test
    public void typingContinuesInItsCodeAfterTheValuesAreRedrawn() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            spreadsheet.codes().add(cell.getVariable());
            view.showValues();
            view.getEdTracker().setEditor(view.getEdTracker().getEditorAtArgIndex(1));
            type("ne");

            cell.setValue(0, "left");
            type("ar");

            assertEquals(cell.getValues(), List.of("left", "near"));
            assertEquals(view.getText(), "(left, near)");
        });
    }

    /** Typing undone while the cell keeps the focus leaves the code empty, and what is typed next replaces its name. */
    @Test
    public void typingAfterAnUndoReplacesThePlaceholder() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            type("smile");
            spreadsheet.undo();
            type("frown");

            assertEquals(cell.getValue(0), "frown");
            assertEquals(view.getText(), "(frown)");
        });
    }

    /** Undo can take away the code being typed in; typing then goes on in the last code left. */
    @Test
    public void typingGoesOnInTheLastCodeWhenItsCodeIsTakenAway() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            spreadsheet.codes().add(cell.getVariable());
            view.showValues();
            view.getEdTracker().setEditor(view.getEdTracker().getEditorAtArgIndex(1));

            spreadsheet.undo();
            view.showValues();
            type("up");

            assertEquals(cell.getValues(), List.of("up"));
            assertEquals(view.getText(), "(up)");
        });
    }

    /** A value is shown as it is, backslashes and all, though it was set from elsewhere, as by an undo. */
    @Test
    public void aValueShowsAsItIs() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            cell.setValue(0, "\\\\server\\share");

            assertEquals(view.getText(), "(\\\\server\\share)");
        });
    }

    /** A cell never typed in starts its typing in its first code, though its values were redrawn. */
    @Test
    public void aRedrawnCellStartsInItsFirstCode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            spreadsheet.codes().add(cell.getVariable());
            CellValuesView fresh = new CellValuesView(spreadsheet, cell);
            fresh.showValues();
            fresh.setCaretPosition(fresh.getText().length());

            fresh.getEdTracker().focusGained(new FocusEvent(fresh, FocusEvent.FOCUS_GAINED));

            assertSame(fresh.getEdTracker().getCurrentEditor(), fresh.getEdTracker().firstEditor());
        });
    }

    /** Typing in the first of several codes stays there when another code changes. */
    @Test
    public void typingStaysInTheFirstCodeWhenAnotherChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            spreadsheet.codes().add(cell.getVariable());
            view.showValues();
            view.getEdTracker().setEditor(view.getEdTracker().firstEditor());
            type("le");

            cell.setValue(1, "near");
            type("ft");

            assertEquals(cell.getValues(), List.of("left", "near"));
        });
    }

    /** A key that changes no text, such as Escape, leaves an empty code empty, not holding its placeholder. */
    @Test
    public void aKeyThatChangesNothingLeavesAnEmptyCodeEmpty() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            type("\u001b\t");

            assertEquals(cell.getValue(0), "");
            assertFalse(spreadsheet.getUndoManager().canUndo());
        });
    }

    @Test
    public void eachDigitTypedIntoATimeIsAnEdit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TimeStampDataValueEditor onset = new TimeStampDataValueEditor(spreadsheet, new JTextField(), cell,
                TimeStampSource.Onset);
            onset.keyTyped(new KeyEvent(view, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, '1'));
            long once = cell.getOnset();
            onset.keyTyped(new KeyEvent(view, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, '2'));
            assertNotEquals(cell.getOnset(), once);

            spreadsheet.undo();
            assertEquals(cell.getOnset(), once);
            spreadsheet.undo();
            assertEquals(cell.getOnset(), 1_000);
            assertFalse(spreadsheet.getUndoManager().canUndo());
        });
    }
}
