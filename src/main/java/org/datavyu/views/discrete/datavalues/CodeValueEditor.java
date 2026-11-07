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

import org.datavyu.models.db.Cell;
import org.datavyu.views.discrete.EditorComponent;

import javax.swing.text.JTextComponent;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The editor of a cell's value for one code, one of the editors of a cell's values ({@link CellValuesView}). An empty
 * value shows the code's name as it is now, as {@code <code01>}, and what is typed goes to be set and recorded.
 */
public final class CodeValueEditor extends EditorComponent {

    /** The keys that type nothing into a value: Tab and Escape. */
    private static final String RESERVED_CHARS = "\t\u001B";

    /** The cell whose value this edits */
    private final Cell cell;

    /** The code's place among the column's codes */
    private final int code;

    /** Tells whether quick-key mode is on, in which typing does not change the value */
    private final BooleanSupplier quickKeyMode;

    /** Where what is typed goes, to change the value */
    private final Consumer<String> typing;

    /**
     * @param text         the text component of the cell's values, which this editor is part of
     * @param cell         the cell
     * @param code         the code's place among the column's codes
     * @param quickKeyMode tells whether quick-key mode is on, in which typing does not change the value
     * @param typing       where what is typed goes, to change the value
     */
    CodeValueEditor(final JTextComponent text,
                    final Cell cell,
                    final int code,
                    final BooleanSupplier quickKeyMode,
                    final Consumer<String> typing) {
        super(text);
        this.cell = cell;
        this.code = code;
        this.quickKeyMode = quickKeyMode;
        this.typing = typing;
        setEditable(true);
        setAcceptReturnKey(true);
        resetText(isEmpty() ? placeholder() : cell.getValue(code));
    }

    /** Returns whether the cell's value for the code is empty. */
    private boolean isEmpty() {
        return cell.getValue(code).isEmpty();
    }

    /** Returns what an empty value shows: the code's name as it is now. */
    private String placeholder() {
        return "<" + cell.getVariable().getCodes().get(code) + ">";
    }

    private static boolean isReserved(final char key) {
        return RESERVED_CHARS.indexOf(key) >= 0;
    }

    private void typedText() {
        typing.accept(getText() == null ? "" : getText());
    }

    @Override
    public void focusGained(final FocusEvent fe) {
        // Each change is recorded as it is typed (TypingCommands.typeValue).
    }

    @Override
    public void keyPressed(final KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_BACK_SPACE -> {
                if (!isEmpty()) {
                    removeBehindCaret();
                    typedText();
                }
                e.consume();
            }
            case KeyEvent.VK_DELETE -> {
                if (!isEmpty()) {
                    removeAheadOfCaret();
                    typedText();
                }
                e.consume();
            }
            default -> {
                // Other keys need nothing here.
            }
        }
    }

    @Override
    public void keyTyped(final KeyEvent e) {
        String before = getText();

        // Just a regular vanilla keystroke - insert it into the value.
        if (!quickKeyMode.getAsBoolean() && !e.isConsumed() && !e.isMetaDown() && !e.isControlDown()
                && !isReserved(e.getKeyChar())) {

            // Avoids a nasty bug that occurs if carraige return goes to editor. 'New cell' numpad enter still processed as always
            if (isEmpty() && e.getKeyChar() == '\n') {
                return;
            }

            this.removeSelectedText();
            StringBuilder currentValue = new StringBuilder(getText());

            // If we have a delete or backspace key - do not insert.
            if (!(e.getKeyLocation() == KeyEvent.KEY_LOCATION_UNKNOWN
                    && e.getKeyChar() == '\u007F') &&
                    !(e.getKeyLocation() == KeyEvent.KEY_LOCATION_UNKNOWN
                            && e.getKeyChar() == '\u0008')) {
                currentValue.insert(getCaretPosition(), e.getKeyChar());
            }

            // Advance caret over the top of the new char.
            int pos = this.getCaretPosition() + 1;
            this.setText(currentValue.toString());
            this.setCaretPosition(pos);
            e.consume();

            // All other keystrokes are consumed.
        } else {
            e.consume();
        }

        // Push the character changes into the database. A key that changed nothing, such as a reserved one, sends
        // nothing: the text may be the placeholder of an empty code.
        if (!getText().equals(before)) {
            typedText();
        }
    }

    @Override
    public void keyReleased(final KeyEvent e) {
        if (getText().length() == 0) {
            setText(placeholder());
            selectAll();
        }
    }

    /**
     * Returns whether the editor tracker lets a part of the value be selected: an empty value is selected whole.
     */
    @Override
    public boolean canSubSelect() {
        return !isEmpty();
    }

    /**
     * Selects part of the value, or all of an empty one, its placeholder.
     *
     * @param startClick the first character selected
     * @param endClick   the character after the last selected
     */
    @Override
    public void select(final int startClick, final int endClick) {
        if (isEmpty()) {
            selectAll();
        } else {
            super.select(startClick, endClick);
        }
    }
}
