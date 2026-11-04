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

import org.datavyu.models.db.NominalCellValue;

import javax.swing.text.JTextComponent;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * This class is the character editor of a NominalDataValue.
 */
public final class NominalDataValueEditor extends DataValueEditor {

    /**
     * String holding the reserved characters - these are characters that are
     * users are unable to enter into a nominal field.
     * <p/>
     * If Character is an escape key - ignore it.
     */
    private static final String RESERVED_CHARS = "\t\u001B";

    /**
     * The model that this editor is manipulating
     */
    NominalCellValue model;

    /** Tells whether quick-key mode is on, in which typing does not change the value */
    private final BooleanSupplier quickKeyMode;

    /**
     * Constructor.
     *
     * @param ta           The parent JTextComponent the editor resides within.
     * @param nv           The value this editor manipulates.
     * @param quickKeyMode tells whether quick-key mode is on, in which typing does not change the value
     * @param typing       where what is typed goes, to change the value
     */
    public NominalDataValueEditor(final JTextComponent ta,
                                  final NominalCellValue nv,
                                  final BooleanSupplier quickKeyMode,
                                  final Consumer<String> typing) {
        super(ta, nv, typing);
        this.quickKeyMode = quickKeyMode;
        setAcceptReturnKey(true);
        model = nv;
    }

    /**
     * @param aChar Character to test
     * @return true if the character is a reserved character.
     */
    public boolean isReserved(final char aChar) {
        return (RESERVED_CHARS.indexOf(aChar) >= 0);
    }

    // *************************************************************************
    // Parent Class Overrides
    // *************************************************************************
    @Override
    public void focusLost(final FocusEvent fe) {
        // Trim trailing spaces from nominal (apparently they are
        // not permitted).
        super.focusLost(fe);
        if (!model.isValid(this.getText())) {
            this.setText(model.toString());
        }

        super.focusLost(fe);
    }

    @Override
    public void keyTyped(final KeyEvent e) {
        String before = getText();
        super.keyTyped(e);

        // Just a regular vanilla keystroke - insert it into nominal field.
        if (!quickKeyMode.getAsBoolean() && !e.isConsumed() && !e.isMetaDown() && !e.isControlDown()
                && !isReserved(e.getKeyChar())) {
            
            // Avoids a nasty bug that occurs if carraige return goes to editor. 'New cell' numpad enter still processed as always
            if (model.isEmpty() && e.getKeyChar() == '\n') {
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
        if (!getText().equals(before) && (model.isValid(this.getText()) || this.getText().isEmpty())) {
            typed(this.getText());
        }
    }
}