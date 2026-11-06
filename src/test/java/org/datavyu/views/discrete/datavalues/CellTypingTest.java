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
import org.datavyu.views.discrete.EditorTracker;
import org.datavyu.views.discrete.datavalues.TimeStampDataValueEditor.TimeStampSource;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.awt.image.BufferedImage;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultCaret;
import javax.swing.text.JTextComponent;

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

    /** Sends a key press as Swing does: to the component's key listeners, then, if none took it, its key bindings. */
    private static void press(final JComponent on, final int keyCode, final int modifiers) {
        // The caret moves by the text's layout, which a component has once it is sized and painted, as when shown.
        on.setSize(400, 40);
        on.paint(new BufferedImage(400, 40, BufferedImage.TYPE_INT_RGB).createGraphics());
        KeyEvent e = new KeyEvent(on, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
        for (KeyListener listener : on.getKeyListeners()) {
            listener.keyPressed(e);
        }
        Object binding = on.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStrokeForEvent(e));
        if (!e.isConsumed() && binding != null) {
            on.getActionMap().get(binding).actionPerformed(new ActionEvent(on, ActionEvent.ACTION_PERFORMED, null));
        }
    }

    /** B27: ⇧ with an arrow grows or shrinks the selection in a value, up to the value's ends, and typing replaces it. */
    @Test
    public void shiftAndAnArrowSelectInAValue() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            type("smile");

            press(view, KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);
            press(view, KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);
            assertEquals(view.getSelectedText(), "le");
            press(view, KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK);
            assertEquals(view.getSelectedText(), "e");
            for (int i = 0; i < 6; i++) {
                press(view, KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);
            }
            assertEquals(view.getSelectedText(), "smile", "not past the value's start into the text around it");

            type("grin");
            assertEquals(cell.getValue(0), "grin");
        });
    }

    /** An empty value's name is not part of the value, so ⇧ with an arrow selects none of it alone. */
    @Test
    public void shiftAndAnArrowLeaveAnEmptyValuesNameWhole() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            view.getEdTracker().getCurrentEditor().selectAll();

            press(view, KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);
            press(view, KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK);

            assertEquals(view.getSelectedText(), "<code01>");

            // With the caret at the name's start, as after "(".
            view.setCaretPosition(1);
            press(view, KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK);
            assertEquals(view.getSelectedText(), null);
        });
    }

    /** B27: ⇧ with an arrow selects in a time too. */
    @Test
    public void shiftAndAnArrowSelectInATime() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TimeStampTextField onset = new TimeStampTextField(spreadsheet, cell, TimeStampSource.Onset);
            onset.setCaretPosition(onset.getText().length());

            press(onset, KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);
            press(onset, KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);

            assertEquals(onset.getSelectedText(), "00");
        });
    }

    private static final int OPTION = InputEvent.ALT_DOWN_MASK;
    private static final int SHIFT_OPTION = InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK;

    /** An arrow leaves a selection at its start (←) or end (→), as in a text field. */
    @Test
    public void anArrowLeavesASelectionAtItsEdge() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            type("smile");
            press(view, KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);
            press(view, KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);

            press(view, KeyEvent.VK_LEFT, 0);
            assertEquals(view.getSelectedText(), null);
            type("x");
            assertEquals(cell.getValue(0), "smixle");

            press(view, KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);
            press(view, KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);
            press(view, KeyEvent.VK_RIGHT, 0);
            type("y");
            assertEquals(cell.getValue(0), "smixyle");
        });
    }

    /** A value selected whole, as Tab leaves it, is a selection like any other: ← goes to its start. */
    @Test
    public void anArrowFromAValueSelectedWholeStaysInIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            spreadsheet.codes().add(cell.getVariable());
            cell.setValues(List.of("left", "near"));
            EditorTracker tracker = view.getEdTracker();
            tracker.setEditor(tracker.nextEditor());

            press(view, KeyEvent.VK_LEFT, 0);
            type("x");

            assertEquals(cell.getValues(), List.of("left", "xnear"));
        });
    }

    /** With nothing selected, an arrow at a value's edge goes on to the next value, and an empty value's name. */
    @Test
    public void anArrowAtAValuesEdgeGoesToTheNext() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            spreadsheet.codes().add(cell.getVariable());
            cell.setValue(0, "left");
            EditorTracker tracker = view.getEdTracker();
            tracker.setEditor(tracker.firstEditor(), Integer.MAX_VALUE, Integer.MAX_VALUE);

            press(view, KeyEvent.VK_RIGHT, 0);
            assertSame(tracker.getCurrentEditor(), tracker.lastEditor());
            assertEquals(view.getSelectedText(), "<code02>");

            press(view, KeyEvent.VK_LEFT, 0);
            type("x");
            assertEquals(cell.getValues(), List.of("leftx", ""));
        });
    }

    /** Before the first value and after the last there is no value to go to, so the caret stays. */
    @Test
    public void anArrowStaysAtTheFirstValuesStartAndTheLastsEnd() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            spreadsheet.codes().add(cell.getVariable());
            cell.setValues(List.of("left", "near"));
            EditorTracker tracker = view.getEdTracker();
            tracker.setEditor(tracker.firstEditor(), 0, 0);

            press(view, KeyEvent.VK_LEFT, 0);
            type("x");
            tracker.setEditor(tracker.lastEditor(), Integer.MAX_VALUE, Integer.MAX_VALUE);
            press(view, KeyEvent.VK_RIGHT, 0);
            type("y");

            assertEquals(cell.getValues(), List.of("xleft", "neary"));
        });
    }

    /** ⌥ with an arrow moves by a word, and with ⇧ selects one, inside the value. */
    @Test
    public void optionAndAnArrowMoveByAWord() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            type("big red-ball");

            press(view, KeyEvent.VK_LEFT, OPTION);
            press(view, KeyEvent.VK_LEFT, OPTION);
            press(view, KeyEvent.VK_RIGHT, OPTION);
            type("!");
            assertEquals(cell.getValue(0), "big red!-ball");

            press(view, KeyEvent.VK_RIGHT, SHIFT_OPTION);
            assertEquals(view.getSelectedText(), "-ball");
            press(view, KeyEvent.VK_LEFT, SHIFT_OPTION);
            press(view, KeyEvent.VK_LEFT, SHIFT_OPTION);
            press(view, KeyEvent.VK_LEFT, SHIFT_OPTION);
            assertEquals(view.getSelectedText(), "big red!", "not past the value's start into the text around it");

            press(view, KeyEvent.VK_RIGHT, OPTION);
            type("?");
            assertEquals(cell.getValue(0), "big red!-ball?", "a word on from the selection's end, not its caret");
        });
    }

    /** A word move stops at the value's ends rather than going on into the text around it. */
    @Test
    public void aWordMoveStopsAtTheValuesStart() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            spreadsheet.codes().add(cell.getVariable());
            cell.setValues(List.of("left", "near"));
            EditorTracker tracker = view.getEdTracker();
            tracker.setEditor(tracker.nextEditor(), 0, 0);
            view.setCaretPosition(view.getText().indexOf("near") + 2);

            press(view, KeyEvent.VK_LEFT, SHIFT_OPTION);
            press(view, KeyEvent.VK_LEFT, SHIFT_OPTION);
            assertEquals(view.getSelectedText(), "ne");

            press(view, KeyEvent.VK_LEFT, OPTION);
            type("x");
            assertEquals(cell.getValues(), List.of("left", "xnear"));
        });
    }

    /** ⌘ and ⌃ with an arrow are commands or nothing, never a caret move in the text. */
    @Test
    public void commandOrControlAndAnArrowMoveNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            type("smile");
            int caret = view.getCaretPosition();

            press(view, KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK);
            press(view, KeyEvent.VK_LEFT, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);

            assertEquals(view.getCaretPosition(), caret);
            assertEquals(view.getSelectedText(), null);

            TimeStampTextField onset = new TimeStampTextField(spreadsheet, cell, TimeStampSource.Onset);
            onset.setCaretPosition(3);
            press(onset, KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK);
            assertEquals(onset.getCaretPosition(), 3);
        });
    }

    /** In a time, an arrow steps over a colon, ⌥ moves by a group of digits, and the caret stops at the time's ends. */
    @Test
    public void anArrowInATimeStepsOverItsColons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TimeStampTextField onset = new TimeStampTextField(spreadsheet, cell, TimeStampSource.Onset);
            onset.setCaretPosition(3);

            press(onset, KeyEvent.VK_LEFT, 0);
            assertEquals(onset.getCaretPosition(), 1);
            press(onset, KeyEvent.VK_RIGHT, 0);
            assertEquals(onset.getCaretPosition(), 3);
            press(onset, KeyEvent.VK_LEFT, 0);
            press(onset, KeyEvent.VK_LEFT, 0);
            press(onset, KeyEvent.VK_LEFT, 0);
            assertEquals(onset.getCaretPosition(), 0);

            onset.setCaretPosition(onset.getText().length());
            press(onset, KeyEvent.VK_RIGHT, 0);
            assertEquals(onset.getCaretPosition(), onset.getText().length());
            press(onset, KeyEvent.VK_LEFT, SHIFT_OPTION);
            assertEquals(onset.getSelectedText(), "000");
            press(onset, KeyEvent.VK_LEFT, 0);
            press(onset, KeyEvent.VK_LEFT, OPTION);
            assertEquals(onset.getCaretPosition(), 6);
        });
    }

    /** Sends a typed key as Swing does, to the component's key listeners. */
    private static void typeInto(final JComponent on, final char key) {
        KeyEvent e = new KeyEvent(on, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, key);
        for (KeyListener listener : on.getKeyListeners()) {
            listener.keyTyped(e);
        }
    }

    /** A digit typed over a selection that starts on a colon goes after the colon, which stays. */
    @Test
    public void aDigitTypedOverAColonKeepsIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TimeStampTextField onset = new TimeStampTextField(spreadsheet, cell, TimeStampSource.Onset);
            onset.setCaretPosition(3);
            press(onset, KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);
            assertEquals(onset.getSelectedText(), ":");

            typeInto(onset, '5');

            assertEquals(cell.getOnset(), 50 * 60_000 + 1_000);
        });
    }

    /** A time takes only the digits 0 to 9, and at its end there is no digit for one typed to take the place of. */
    @Test
    public void aTimeTakesOnlyDigitsAndNoneAtItsEnd() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TimeStampTextField onset = new TimeStampTextField(spreadsheet, cell, TimeStampSource.Onset);
            onset.setCaretPosition(onset.getText().length());

            typeInto(onset, '5');
            onset.setCaretPosition(0);
            typeInto(onset, 'x');
            typeInto(onset, '\uFF15');

            assertEquals(onset.getText(), "00:00:01:000");
            assertEquals(cell.getOnset(), 1_000);
            assertFalse(spreadsheet.getUndoManager().canUndo());
        });
    }

    /** Sends the press, release and click of a click with the button and modifiers given. */
    private static void click(final JComponent on, final int button, final int modifiers) {
        for (int id : new int[] {MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED}) {
            on.dispatchEvent(new MouseEvent(on, id, 0, modifiers, 5, 5, 1, false, button));
        }
    }

    /**
     * A right-click or ⌃-click in a value or a time does not reach its caret, which would take the focus and so select
     * the cell, or its other mouse listeners.
     */
    @Test
    public void aRightOrControlClickInACellsTextDoesNotReachIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TimeStampTextField onset = new TimeStampTextField(spreadsheet, cell, TimeStampSource.Onset);
            for (JTextComponent text : List.<JTextComponent>of(view, onset)) {
                List<Integer> seen = new ArrayList<>();
                text.setCaret(new DefaultCaret() {
                    @Override
                    public void mousePressed(final MouseEvent e) {
                        seen.add(e.getID());
                    }

                    @Override
                    public void mouseReleased(final MouseEvent e) {
                        seen.add(e.getID());
                    }

                    @Override
                    public void mouseClicked(final MouseEvent e) {
                        seen.add(e.getID());
                    }
                });

                click(text, MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK | InputEvent.CTRL_DOWN_MASK);
                click(text, MouseEvent.BUTTON3, InputEvent.BUTTON3_DOWN_MASK);
                assertEquals(seen, List.of(), text.getClass().getSimpleName());

                click(text, MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK);
                assertEquals(seen, List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED,
                    MouseEvent.MOUSE_CLICKED), "a click reaches the caret");
            }
        });
    }

    /** Tab goes to the next code and ⇧Tab to the one before, each coming round from the end. */
    @Test
    public void tabAndShiftTabGoFromCodeToCode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            spreadsheet.codes().add(cell.getVariable());
            spreadsheet.codes().add(cell.getVariable());
            view.showValues();
            EditorTracker tracker = view.getEdTracker();
            KeyEvent tab = new KeyEvent(view, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_TAB, '\t');
            KeyEvent shiftTab = new KeyEvent(view, KeyEvent.KEY_PRESSED, 0, InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_TAB,
                '\t');
            tracker.setEditor(tracker.getEditorAtArgIndex(1));

            tracker.keyPressed(tab);
            assertSame(tracker.getCurrentEditor(), tracker.getEditorAtArgIndex(2));
            tracker.keyPressed(tab);
            assertSame(tracker.getCurrentEditor(), tracker.getEditorAtArgIndex(0));

            tracker.keyPressed(shiftTab);
            assertSame(tracker.getCurrentEditor(), tracker.getEditorAtArgIndex(2));
            tracker.keyPressed(shiftTab);
            assertSame(tracker.getCurrentEditor(), tracker.getEditorAtArgIndex(1));
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
