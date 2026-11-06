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
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.Variable;
import org.datavyu.views.discrete.EditorComponent;
import org.datavyu.views.discrete.EditorTracker;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

/**
 * JTextArea view of the Matrix (database cell) data.
 */
public final class MatrixRootView extends JTextArea implements FocusListener {

    /** Logger for this class */
    private static Logger logger = LogManager.getLogger(MatrixRootView.class);

    /**
     * The parent cell for this JPanel.
     */
    private Cell parentCell = null;

    /**
     * Whether a value has been typed since the cell took the focus, so what is typed next continues it, as one edit in
     * the undo history.
     */
    private boolean typing;

    /**
     * Whether this view's own typing is being set in the cell. The view shows it already, so it passes over the cell's
     * news of it rather than rebuild the editor being typed in.
     */
    private boolean settingTyping;

    /**
     * All the editors that make up the representation of the data.
     */
    private List<EditorComponent> allEditors;

    /**
     * The editor tracker responsible for the editor components.
     */
    private EditorTracker edTracker;

    /** The spreadsheet the cell is in */
    private final ProjectController controller;

    /**
     * Creates a new instance of MatrixV.
     *
     * @param controller the spreadsheet the cell is in
     * @param cell  The parent cell for this spread sheet cell.
     */
    public MatrixRootView(final ProjectController controller, final Cell cell) {
        super();
        this.controller = controller;

        setLineWrap(true);
        setWrapStyleWord(true);

        parentCell = cell;
        allEditors = new ArrayList<>();
        edTracker = new EditorTracker(this, allEditors, cell.getVariable(), controller.cells()::deleteSelected);

        showValues();

        addFocusListener(this);
        addFocusListener(edTracker);

        // Funny behavior for the return key
        addKeyListener(new KeyListener() {
                   @Override
                   public void keyPressed(KeyEvent e) {
                       if (e.getKeyCode() == KeyEvent.VK_ENTER) { //&& getSelectedText() != null) {
                           e.consume();
                       }
                    }
                   @Override
                   public void keyTyped(KeyEvent e) {}
                   @Override
                   public void keyReleased(KeyEvent e)
                   {
                        if (e.getKeyCode() == KeyEvent.VK_ENTER)
                        {
                            // Verbose debug output
                            if (!generateText().equals(getText())) {
                                logger.info("MatrixRootview rebuilt due to funny return key");
                                logger.info("\tGenerated contents" + generateText().length() + ": " + generateText()
                                        + "\n\tTextbox contents " + getText().length() + " : " + getText());
                                rebuildText();
                            }
                        }
                   }
                   
                });
        //regular editor tracker
        addKeyListener(edTracker);
        addMouseListener(edTracker);
    }

    /**
     * @return true if a viewport should force the Scrollables width to match
     * its own.
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    /** Shows the cell's values, building an editor for each, unless it is this view's own typing being set. */
    public void showValues() {
        if (settingTyping) {
            return;
        }
        // Determine selected editor, and internal caret position.
        int pos = getCaretPosition();
        EditorComponent comp = edTracker.findEditor(pos);
        int edPos = comp.getCaretPosition();

        allEditors.clear();
        allEditors.addAll(DataValueEditorFactory.buildMatrix(this, parentCell, controller::isQuickKeyMode,
            this::typed));

        rebuildText();

        // restore caret position inside current editor.
        comp.setCaretPosition(edPos);
    }

    /** Sends a code's value typed to the command that sets and records it. */
    private void typed(final int code, final String value) {
        settingTyping = true;
        try {
            if (controller.typing().typeValue(parentCell, code, value, typing)) {
                typing = true;
            }
        } finally {
            settingTyping = false;
        }
    }

    /**
     * Recalculates and sets the text to display.
     */
    public void rebuildText() {
        setText(generateText());
    }
    
    public String generateText() {
        String ans = "";
        for (EditorComponent item : allEditors) {
            ans += item.getText();
        }
        ans = ans.replaceAll("\\\\[\\\\]*\\\\", "\\\\");
        return ans;
    }

    /**
     * Used in the UISpec4j tests.
     *
     * @return The editor tracker for this MatrixRootView.
     */
    public EditorTracker getEdTracker() {
        return edTracker;
    }

    // *************************************************************************
    // Focus Listener Overrides
    // *************************************************************************
    @Override
    public void focusGained(final FocusEvent fe) {
        // We need to remember which cell should be duplicated if the user
        // presses the enter key or selects New Cell from the menu.
        if (parentCell != null) {
            // Typing after the cell takes the focus again is an edit of its own.
            typing = false;
            // method names don't reflect usage - we didn't really create
            // this column just now.
            Variable v = controller.getDataStore().getVariable(parentCell);
            controller.setLastCreatedVariable(v);
            controller.setLastSelectedCell(parentCell);
        }
        parentCell.setSelected(true);
    }

    @Override
    public void focusLost(final FocusEvent fe) {
        parentCell.setSelected(false);
    }

    // *************************************************************************
    // Parent Class Overrides
    // *************************************************************************
    @Override
    public void paste() {
        edTracker.paste();
    }

    @Override
    public void cut() {
        edTracker.cut();
    }
}