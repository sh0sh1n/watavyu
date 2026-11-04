/**
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.views.discrete.EditorComponent;

import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * DataValueEditor - abstract class extending EditorComponent. Adds
 * functionality specific to DataValues being edited.
 */
public abstract class DataValueEditor extends EditorComponent {

    /**
     * The logger for this class.
     */
    private static Logger LOGGER = LogManager.getLogger(DataValueEditor.class);
    /** The cell's value for the code, as it is now; {@code ""} is empty */
    private final Supplier<String> value;

    /** What an empty value shows: the code's name, as {@code <code01>} */
    private final String placeholder;

    /** Where what is typed goes, to change the value */
    private final Consumer<String> typing;

    /**
     * Constructor.
     *
     * @param tc          JTextComponent this editor works with.
     * @param value       the cell's value for the code, as it is now
     * @param placeholder what an empty value shows
     * @param typing      where what is typed goes, to change the value
     */
    public DataValueEditor(final JTextComponent tc,
                           final Supplier<String> value,
                           final String placeholder,
                           final Consumer<String> typing) {
        super(tc);
        this.value = value;
        this.placeholder = placeholder;
        this.typing = typing;

        // So far all DataValueEditors are editable
        setEditable(true);
        resetText(isEmpty() ? placeholder : value.get());
    }

    /** Returns whether the cell's value for the code is empty. */
    protected final boolean isEmpty() {
        return value.get().isEmpty();
    }

    private void updateModelText() {
        typed(getText() == null ? "" : getText());
    }

    /**
     * Sends a value typed to be set and recorded.
     *
     * @param value the value, {@code ""} to empty it
     */
    protected final void typed(final String value) {
        typing.accept(value);
    }

    // *************************************************************************
    // FocusListener Overrides
    // *************************************************************************
    @Override
    public void focusGained(final FocusEvent fe) {
        // Each change is recorded as it is typed (TypingCommands.typeValue).
    }

    // *************************************************************************
    // KeyListener Overrides
    // *************************************************************************
    @Override
    public void keyPressed(final KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_BACK_SPACE:
                if (!isEmpty()) {
                    removeBehindCaret();
                    updateModelText();
                }
                e.consume();
                break;
            case KeyEvent.VK_DELETE:
                if (!isEmpty()) {
                    removeAheadOfCaret();
                    updateModelText();
                }
                e.consume();
                break;

            case KeyEvent.VK_LEFT:
                int selectStart = getSelectionStart();
                int selectEnd = getSelectionEnd();

                // Move caret to the left.
                int c = Math.max(0, getCaretPosition() - 1);
                setCaretPosition(c);

                // If after the move, we have a character to the left is
                // preserved character we need to skip one before passing
                // the key event down to skip again (effectively skipping
                // the preserved character).
                int b = Math.max(0, getCaretPosition());
                c = Math.max(0, getCaretPosition() - 1);
                if (isPreserved(getText().charAt(b))
                        || isPreserved(getText().charAt(c))) {
                    setCaretPosition(Math.max(0, getCaretPosition() - 1));
                }
                e.consume();

                // If the user is holding down shift - alter the selection as
                // well as the caret position.
                if (e.getModifiers() == InputEvent.SHIFT_MASK) {
                    // Shrink selection left - removed entire selection.
                    if (getCaretPosition() == selectStart) {
                        select(selectStart, selectStart);
                        // Grow selection left.
                    } else if (getCaretPosition() < selectStart) {
                        select(selectEnd, getCaretPosition());
                        // Shrink selection left.
                    } else {
                        select(selectStart, getCaretPosition());
                    }
                }

                break;

            case KeyEvent.VK_RIGHT:
                selectStart = getSelectionStart();
                selectEnd = getSelectionEnd();

                // Move caret to the right.
                c = Math.min(getText().length(), getCaretPosition() + 1);
                setCaretPosition(c);

                // If after the move, we have a character to the right that
                // is a preserved character, we need to skip one before
                // passing the key event down to skip again (effectively
                // skipping the preserved character)
                b = Math.min(getText().length() - 1, getCaretPosition());
                c = Math.min(getText().length() - 1, getCaretPosition() + 1);
                if (c < getText().length()
                        && (isPreserved(getText().charAt(c)) || isPreserved(getText()
                        .charAt(b)))) {
                    setCaretPosition(Math.min(getText().length() - 1,
                            getCaretPosition() + 1));
                }
                e.consume();

                // If the user is holding down shift - alter the selection as
                // well as the caret position.
                if (e.getModifiers() == InputEvent.SHIFT_MASK) {
                    // Shrink selection right - removed entire selection.
                    if (getCaretPosition() == selectEnd) {
                        select(selectEnd, selectEnd);
                        // Grow selection right.
                    } else if (getCaretPosition() > selectEnd) {
                        select(selectStart, getCaretPosition());
                        // Shrink select right.
                    } else {
                        select(getCaretPosition(), selectEnd);
                    }
                }

                break;

            default:
                break;
        }
    }

    @Override
    public void keyTyped(final KeyEvent e) {
    }

    @Override
    public void keyReleased(final KeyEvent e) {
        if (getText().length() == 0) {
            setText(placeholder);
            selectAll();
        }
        /*
        // Don't think this is necessary, but not entirely confident
        if (!canSubSelect() && hasFocus()) {
            //selectAll();
        }
        */
    }

    /**
     * Determine if the editor tracker permits sub selections within the
     * component.
     *
     * @return True if permitted to perform sub selection, false otherwise.
     */
    @Override
    public boolean canSubSelect() {
        return (!isEmpty());
    }

    /**
     * Override selection to catch if the value is null.
     *
     * @param startClick Start character of the selection.
     * @param endClick   End character of the selection.
     */
    @Override
    public final void select(final int startClick, final int endClick) {
        if (isEmpty()) {
            selectAll();
        } else {
            super.select(startClick, endClick);
        }
    }
}
