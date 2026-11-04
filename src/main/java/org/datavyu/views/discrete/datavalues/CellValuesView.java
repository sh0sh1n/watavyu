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
import org.datavyu.models.db.MediaClip;
import org.datavyu.models.db.Variable;
import org.datavyu.views.discrete.EditorComponent;
import org.datavyu.views.discrete.EditorTracker;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A cell's values, as {@code (left, <code02>)}: an editor for each code's value, with the text between them fixed.
 * A media column's cell shows its clip instead.
 */
public final class CellValuesView extends JTextArea implements FocusListener {

    /** Logger for this class */
    private static Logger logger = LogManager.getLogger(CellValuesView.class);

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
     * @param controller the spreadsheet the cell is in
     * @param cell       the cell
     */
    public CellValuesView(final ProjectController controller, final Cell cell) {
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
                                logger.info("Cell values rebuilt after the return key");
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
        allEditors.addAll(buildEditors());

        rebuildText();

        // restore caret position inside current editor.
        comp.setCaretPosition(edPos);
    }

    /** Builds the editors of the cell's values as they are now, or the fixed text of its clip. */
    private List<EditorComponent> buildEditors() {
        List<EditorComponent> editors = new ArrayList<>();
        MediaClip clip = parentCell.getClip();
        if (clip != null) {
            editors.add(new FixedText(this, clipLabel(clip, parentCell.isClipFailed())));
            return editors;
        }
        int codes = parentCell.getVariable().getCodes().size();
        editors.add(new FixedText(this, "("));
        for (int i = 0; i < codes; i++) {
            int code = i;
            editors.add(new CodeValueEditor(this, parentCell, code, controller::isQuickKeyMode,
                value -> typed(code, value)));
            if (i < codes - 1) {
                editors.add(new FixedText(this, ", "));
            }
        }
        editors.add(new FixedText(this, ")"));
        return editors;
    }

    /**
     * Names what a media cell plays: its file's name, its frame rate when the file has video and has said it, and
     * whether the file is missing, as {@code cam.mp4 · 29.97 fps (missing)}, or there but could not be opened, as
     * {@code cam.mp4 (could not open)}.
     *
     * @param clip   a media cell's clip
     * @param failed whether the file could not be opened
     * @return the cell's text
     */
    public static String clipLabel(final MediaClip clip, final boolean failed) {
        String label = clip.fileName();
        if (clip.frameRate() > 0) {
            label += " \u00b7 " + new BigDecimal(clip.frameRate()).setScale(3, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + " fps";
        }
        if (!Files.exists(Path.of(clip.file()))) {
            return label + " (missing)";
        }
        return failed ? label + " (could not open)" : label;
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
     * @return The editor tracker for this CellValuesView.
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