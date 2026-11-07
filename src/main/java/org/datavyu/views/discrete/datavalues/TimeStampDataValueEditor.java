/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.datavyu.views.discrete.datavalues;

import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.Cell;
import org.datavyu.util.Timestamp;
import org.datavyu.views.discrete.EditorComponent;
import org.datavyu.views.discrete.EditorTracker;

import javax.swing.text.JTextComponent;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;

/**
 * This class is the character editor of a TimeStampDataValues.
 */
public final class TimeStampDataValueEditor extends EditorComponent {


    /**
     * The parent datacell for the TimeStamp that this view represents.
     */
    private Cell parentCell;

    /**
     * The source of the TimeStampDataValue being edited.
     */
    private TimeStampSource dataSourceType;


    /** The spreadsheet the cell is in */
    private final ProjectController controller;

    /**
     * Constructor.
     *
     * @param controller the spreadsheet the cell is in
     * @param ta         The parent JTextComponent the editor is in.
     * @param cell       The parent data cell this editor resides within.
     * @param sourceType What timestamp are we displaying.
     */
    public TimeStampDataValueEditor(final ProjectController controller,
                                    final JTextComponent ta,
                                    final Cell cell,
                                    final TimeStampSource sourceType) {
        super(ta);
        this.controller = controller;
        setEditable(true);
        parentCell = cell;
        dataSourceType = sourceType;
        addPreservedChars(":");
        setDeleteChar('0');
        resetValue();
    }

    /**
     * Reset the values by retrieving from the database.
     */
    public void resetValue() {
        setText(dataSourceType == TimeStampSource.Onset ? parentCell.getOnsetString() : parentCell.getOffsetString());
    }

    /** Sends the time typed to the command that sets and records it, then shows the cell's time. */
    private void setTimeStamp(final String value) {
        long time = Timestamp.parse(value);
        if (dataSourceType == TimeStampSource.Onset) {
            controller.typing().typeOnset(parentCell, time);
        } else {
            controller.typing().typeOffset(parentCell, time);
        }
        resetValue();
    }

    /**
     * Returns whether this time can be edited: a clip's offset never can, as it follows from the clip's onset and
     * length, and no time in a locked column can.
     */
    public boolean isTypingAllowed() {
        if (parentCell.getVariable().isLocked()) {
            return false;
        }
        return parentCell.getClip() == null || dataSourceType == TimeStampSource.Onset;
    }

    /** Returns whether an edit may go ahead; if not, a locked column hears it was refused. */
    private boolean allowsEdit() {
        return !parentCell.getVariable().refusesEdits() && isTypingAllowed();
    }

    @Override
    public void cut() {
        if (allowsEdit()) {
            super.cut();
        }
    }

    @Override
    public void paste() {
        if (allowsEdit()) {
            super.paste();
        }
    }

    @Override
    public void keyPressed(final KeyEvent e) {
        if ((e.getKeyCode() == KeyEvent.VK_BACK_SPACE || e.getKeyCode() == KeyEvent.VK_DELETE) && !allowsEdit()) {
            e.consume();
            return;
        }
        //TimeStampDataValue tdv = (TimeStampDataValue) getModel();
        switch (e.getKeyCode()) {
            // Force the Home key to behave correctly on OSX 10.4
            case KeyEvent.VK_HOME -> {
                setCaretPosition(0);
                e.consume();
            }

            // Force the End key to behave correctly on OSX 10.4
            case KeyEvent.VK_END -> {
                setCaretPosition(getText().length());
                e.consume();
            }

            case KeyEvent.VK_BACK_SPACE -> {
                removeBehindCaret();
                setTimeStamp(getText());
                e.consume();
            }
            case KeyEvent.VK_DELETE -> {
                int caret = getSelectionEnd();
                removeAheadOfCaret();
                setCaretPosition(caret);
                if (caret < getText().length()
                        && isPreserved(getText().charAt(caret))) {
                    setCaretPosition(getCaretPosition() + 1);
                }
                setCaretPosition(getCaretPosition() + 1);
                //Move an extra caret position if the next char is a ":"
                int c = Math.min(getText().length() - 1, getCaretPosition());
                if (isPreserved(getText().charAt(c))) {
                    setCaretPosition(getCaretPosition() + 1);
                }

                setTimeStamp(getText());
                e.consume();
            }

            case KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT -> {
                // A time is alone in its field, so the caret stops at its ends.
                if (movesCaret(e)) {
                    moveCaret(e);
                }
                e.consume();
            }
            default -> {
                // Other keys need nothing here.
            }
        }
    }

    /**
     * The action to invoke when a key is typed.
     *
     * @param e The KeyEvent that triggered this action.
     */
    @Override
    public void keyTyped(final KeyEvent e) {
        if (!isTypingAllowed()) {
            if (EditorTracker.isTypedEdit(e)) {
                parentCell.getVariable().refusesEdits();
            }
            e.consume();
            return;
        }
        if (e.getKeyChar() < '0' || e.getKeyChar() > '9') {
            // Every other key stroke is ignored by the time editor, other scripts' digits too.
            e.consume();
            return;
        }
        // A digit takes the place of the one after the caret, or of the selection's first; the colons stay.
        removeAheadOfCaret();
        if (getCaretPosition() < getText().length() && isPreserved(getText().charAt(getCaretPosition()))) {
            setCaretPosition(getCaretPosition() + 1);
        }
        if (getCaretPosition() < getText().length()) {
            StringBuilder currentValue = new StringBuilder(getText());
            currentValue.deleteCharAt(getCaretPosition());
            currentValue.insert(getCaretPosition(), e.getKeyChar());
            setCaretPosition(getCaretPosition() + 1);
            //Move an extra caret position if the next char is a ":"
            int c = Math.min(getText().length() - 1, getCaretPosition());
            if (isPreserved(getText().charAt(c))) {
                setCaretPosition(getCaretPosition() + 1);
            }

            setTimeStamp(currentValue.toString());
        }
        // At the end, there is no digit left to take the place of.
        e.consume();
    }

    /**
     * focusSet is the signal that this editor has become "current".
     *
     * @param fe Focus Event
     */
    @Override
    public void focusGained(final FocusEvent fe) {
        // Each change of the time is recorded as it is made, so there is nothing to remember here.
    }

    /**
     * Action to take by this editor when a key is released.
     *
     * @param e KeyEvent
     */
    @Override
    public void keyReleased(final KeyEvent e) {
    }

    public enum TimeStampSource {
        /**
         * Timestamp is the Onset of the datacell associated.
         */
        Onset,
        /**
         * Timestamp is the Offset of the datacell associated.
         */
        Offset
    }
}
