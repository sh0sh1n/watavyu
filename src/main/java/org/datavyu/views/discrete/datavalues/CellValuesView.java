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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.Cell.ClipFile;
import org.datavyu.models.db.MediaClip;
import org.datavyu.views.discrete.EditorComponent;
import org.datavyu.views.discrete.EditorTracker;
import org.datavyu.views.discrete.SecondaryClick;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final Cell parentCell;

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
        edTracker = new EditorTracker(this, allEditors, cell.getVariable());

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
                            String values = valuesText();
                            if (!values.equals(getText())) {
                                logger.info("Cell values rebuilt after the return key");
                                logger.info("\tGenerated contents" + values.length() + ": " + values
                                        + "\n\tTextbox contents " + getText().length() + " : " + getText());
                                setText(values);
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
        // The editor in the current one's place takes over, with the caret where it was.
        int place = edTracker.editableIndexOfCurrentEditor();
        int caret = edTracker.getCurrentEditor().getCaretPosition();

        allEditors.clear();
        allEditors.addAll(buildEditors());
        setText(valuesText());

        edTracker.followRebuiltEditors(place, caret);
    }

    /** Builds the editors of the cell's values as they are now, or the fixed text of its clip. */
    private List<EditorComponent> buildEditors() {
        List<EditorComponent> editors = new ArrayList<>();
        MediaClip clip = parentCell.getClip();
        if (clip != null) {
            editors.add(new FixedText(this, clipLabel(clip, parentCell.getClipFile())));
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
     * whether the player found the file missing, as {@code cam.mp4 · 29.97 fps (missing)}, or there but could not
     * open it, as {@code cam.mp4 (could not open)}.
     *
     * @param clip  a media cell's clip
     * @param found what the player last found of its file
     * @return the cell's text
     */
    public static String clipLabel(final MediaClip clip, final ClipFile found) {
        String label = clip.fileName();
        if (clip.frameRate() > 0) {
            label += " \u00b7 " + new BigDecimal(clip.frameRate()).setScale(3, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + " fps";
        }
        return switch (found) {
            case FOUND -> label;
            case MISSING -> label + " (missing)";
            case FAILED -> label + " (could not open)";
        };
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

    /** Returns the text the editors show, one after another. */
    private String valuesText() {
        String ans = "";
        for (EditorComponent item : allEditors) {
            ans += item.getText();
        }
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
        // Typing after the cell takes the focus again is an edit of its own.
        typing = false;
        // New Cell from the menu goes to the column of the cell last in focus.
        controller.setLastCreatedVariable(parentCell.getVariable());
        controller.setLastSelectedCell(parentCell);
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

    /** A right-click or ⌃-click neither moves the caret nor takes the focus, so it leaves the cell's selection alone. */
    @Override
    protected void processMouseEvent(final MouseEvent e) {
        if (!SecondaryClick.isDroppedByText(e)) {
            super.processMouseEvent(e);
        }
    }
}
