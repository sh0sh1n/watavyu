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
package com.sh0sh1n.watavyu.views.spreadsheet.editors;

import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.views.spreadsheet.SecondaryClick;
import com.sh0sh1n.watavyu.views.spreadsheet.editors.TimeStampDataValueEditor.TimeStampSource;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;

/**
 * JTextArea view of the Matrix (database cell) data.
 */
public final class TimeStampTextField extends JTextField
        implements FocusListener, KeyListener {

    /**
     * The editors that make up the representation of the data.
     */
    private TimeStampDataValueEditor myEditor;

    /**
     * Creates a new instance of MatrixV.
     *
     * @param controller the spreadsheet the cell is in
     * @param cell   The parent datacell for this spreadsheet cell.
     * @param tsType Which TimeStamp of the cell to display. represent.
     */
    public TimeStampTextField(final ProjectController controller, final Cell cell, final TimeStampSource tsType) {
        super();

        myEditor = new TimeStampDataValueEditor(controller, this, cell, tsType);

        setValue();
        // Set visual appearance.
        setBorder(null);
        setOpaque(false);

        addFocusListener(this);
        addKeyListener(this);
    }

    /**
     * Sets the value to be displayed.
     */
    public void setValue() {
        myEditor.resetValue();

        rebuildText();
    }

    /**
     * Recalculates and sets the text to display.
     */
    public void rebuildText() {
        int pos = getCaretPosition();
        setText(myEditor.getText());
        setCaretPosition(pos);
    }

    /**
     * Calculate the currentEditor's text and call it's resetText method.
     */
    public void resetEditorText() {
        myEditor.resetText(getText());
    }

    // *************************************************************************
    // Parent Class Overrides
    // *************************************************************************
    @Override
    public void cut() {
        myEditor.cut();
    }

    @Override
    public void paste() {
        myEditor.paste();
    }

    /**
     * Override to address bug(?) in JTextField see java bug identifier 4446522 for
     * discussion. Probably not the final answer but resolves the clipping of
     * first character displayed.
     *
     * @return the dimension of this textfield
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension size = new Dimension(super.getPreferredSize());
        size.width += 2;
        return size;
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension mysize = super.getPreferredSize();
        return new Dimension(mysize.width, mysize.height);
    }

    // *************************************************************************
    // FocusListener Overrides
    // *************************************************************************
    @Override
    public void focusGained(final FocusEvent fe) {
        myEditor.focusGained(fe);
    }


    @Override
    public void focusLost(final FocusEvent fe) {
        myEditor.focusLost(fe);
    }

    // *************************************************************************
    // KeyListener Overrides
    // *************************************************************************
    @Override
    public void processKeyEvent(final KeyEvent ke) {
        super.processKeyEvent(ke);

        if (!ke.isConsumed()) {
            getParent().dispatchEvent(ke);
        }
    }

    @Override
    public void keyReleased(final KeyEvent e) {
        resetEditorText();
        myEditor.keyReleased(e);
    }

    @Override
    public void keyTyped(final KeyEvent e) {
        myEditor.keyTyped(e);
    }

    @Override
    public void keyPressed(final KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ENTER -> {
                // help out the editors that don't want the return key
                if (!myEditor.isReturnKeyAccepted() && e.getKeyLocation() == KeyEvent.KEY_LOCATION_STANDARD) {
                    e.consume();
                }
            }
            case KeyEvent.VK_TAB -> {
                myEditor.selectAll();
                e.consume();
            }
            default -> {
                // Other keys need nothing here.
            }
        }

        if (!e.isConsumed()) {
            myEditor.keyPressed(e);
        }
    }

    /** A right-click or ⌃-click neither moves the caret nor takes the focus, so it leaves the cell's selection alone. */
    @Override
    protected void processMouseEvent(final MouseEvent e) {
        if (!SecondaryClick.isDroppedByText(e)) {
            super.processMouseEvent(e);
        }
    }
}
