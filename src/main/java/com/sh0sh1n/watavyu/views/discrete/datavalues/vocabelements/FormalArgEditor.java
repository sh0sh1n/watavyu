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
package com.sh0sh1n.watavyu.views.discrete.datavalues.vocabelements;

import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.views.discrete.EditorComponent;

import javax.swing.text.JTextComponent;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;

/**
 * This class is the character editor of a NominalDataValue.
 */
public final class FormalArgEditor extends EditorComponent {

    /**
     * String holding the reserved characters.
     */
    private static final String RESERVED_CHARS = ")(<>|,;\t\r\n";
    /**
     * Parent Vocab Column.
     */
    private Column parentColumn;
    /**
     * Index of the formal arg.
     */
    private int argIndex;
    /**
     * The parent editor window that this argument belongs too.
     */
    private VocabElementV parentView;

    private boolean defaultVal;

    /** Whether a key typed now continues a rename, so the keys typed since this editor gained focus undo together. */
    private boolean renaming;

    /**
     * @param ta    The JTextComponent that this virtual editor floats ontop.
     * @param var    The parent that this argument belongs too.
     * @param index The index of the argument within the parent vocabelement
     *              that this Editor will represent.
     * @param pv    The parent vocab element view that this editor belongs too.
     */
    public FormalArgEditor(final JTextComponent ta,
                           final Column var,
                           final int index,
                           final VocabElementV pv) {
        super(ta);
        setEditable(true);
        argIndex = index;
        parentView = pv;
        parentColumn = var;
        defaultVal = true;
        resetValue();
    }

    /**
     * Resets the text value of this editor.
     */
    public void resetValue() {
        setText(parentColumn.getCodes().get(argIndex));
    }

    /**
     * Returns the argument index.
     */
    public int getArgPos() {
        return argIndex;
    }

    /**
     * Renames the code to the text typed, as an edit that joins those of the keys typed before it, unless the name is
     * refused (empty, or another code's), when the warning mark shows it.
     *
     * @param newValue the code's new name
     */
    public void updateArgName(String newValue) {
        parentView.applyTyped(() -> {
            if (parentView.getParentDialog().getProjectController().codes()
                    .rename(parentColumn, argIndex, newValue, renaming)) {
                renaming = true;
            }
        });
        defaultVal = false;
    }

    /**
     * Action to invoke when focus is gained.
     *
     * @param e The FocusEvent that triggered this action.
     */
    @Override
    public void focusGained(final FocusEvent e) {
        renaming = false;
        this.parentView.getParentDialog().updateDialogState();
    }

    /**
     * The action to invoke when a key is typed.
     *
     * @param e The KeyEvent that triggered this action.
     */
    @Override
    public void keyTyped(KeyEvent e) {
        if (!this.isReserved(e.getKeyChar()) && !e.isControlDown()) {

            removeSelectedText();
            StringBuilder currentValue = new StringBuilder(getText());
            currentValue.insert(getCaretPosition(), e.getKeyChar());

            // Advance caret over the top of the new char. The text is set first, so the view already shows the name
            // the code is given and need not be rebuilt.
            int pos = this.getCaretPosition() + 1;
            this.setText(currentValue.toString());
            this.setCaretPosition(pos);

            updateArgName(currentValue.toString());

            parentView.getParentDialog().updateDialogState();
        }

        e.consume();
    }

    /**
     * Returns true if the character is a reserved character.
     * @param aChar Character to test
     */
    public boolean isReserved(final char aChar) {
        return (RESERVED_CHARS.indexOf(aChar) >= 0);
    }

    /**
     * Action to take by this editor when a key is pressed.
     *
     * @param e The KeyEvent that triggered this action.
     */
    @Override
    public void keyPressed(final KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_BACK_SPACE -> {
                removeBehindCaret();

                updateArgName(getText());

                parentView.getParentDialog().updateDialogState();

                e.consume();
            }
            case KeyEvent.VK_DELETE -> {
                removeAheadOfCaret();

                updateArgName(getText());

                parentView.getParentDialog().updateDialogState();
                e.consume();
            }
            default -> {
                // Other keys need nothing here.
            }
        }
    }

    /**
     * Action to take by this editor when a key is released.
     *
     * @param e The KeyEvent that triggered this action.
     */
    @Override
    public void keyReleased(final KeyEvent e) {
    }

    @Override
    public boolean canSubSelect() {
        return !defaultVal;
    }
}
