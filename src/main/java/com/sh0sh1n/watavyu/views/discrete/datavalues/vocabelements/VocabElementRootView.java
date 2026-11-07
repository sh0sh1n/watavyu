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

import com.sh0sh1n.watavyu.models.db.Column;
import com.sh0sh1n.watavyu.views.discrete.EditorComponent;
import com.sh0sh1n.watavyu.views.discrete.EditorTracker;

import javax.swing.*;
import javax.swing.border.Border;
import java.util.ArrayList;
import java.util.List;

/**
 * JTextArea view of the Matrix (database cell) data.
 */
public final class VocabElementRootView extends JTextArea {

    /**
     * Border to set the text area more aligned to the icons.
     */
    private static Border GAP_BORDER = BorderFactory.createEmptyBorder(2, 0, 0, 0);
    /**
     * The editors that make up the representation of the data.
     */
    private List<EditorComponent> editors;
    /**
     * The editor tracker responsible for the editor components.
     */
    private EditorTracker edTracker;

    /**
     * Constructor
     *
     * @param vocabColumn The vocab element that this root view is representing.
     * @param pv           The parent view that this root view belongs too.
     */
    public VocabElementRootView(final Column vocabColumn,
                                final VocabElementV pv) {
        super();
        setLineWrap(true);
        setWrapStyleWord(true);
        // just push down a little bit
        setBorder(GAP_BORDER);


        editors = new ArrayList<>();
        edTracker = new EditorTracker(this, editors, vocabColumn);

        setVocabElement(vocabColumn, pv);

        this.addFocusListener(edTracker);
        this.addKeyListener(edTracker);
        this.addMouseListener(edTracker);
    }

    /**
     * @return true if a viewport should force the Scrollables width
     * to match its own.
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    /**
     * Sets the argument that this VocabElementRootView will represent.
     *
     * @param var The new vocab element that his root view will represent.
     * @param pv The parent view for this root view.
     */
    public void setVocabElement(final Column var, VocabElementV pv) {
        editors.clear();
        if (editors.isEmpty()) {
            editors.addAll(VocabElementEditorFactory.buildVocabElement(this, var, pv));
        }

        rebuildText();
    }

    /**
     * Recalculates and sets the text to display.
     */
    public void rebuildText() {
        StringBuilder text = new StringBuilder();
        for (EditorComponent item : editors) {
            text.append(item.getText());
        }
        setText(text.toString());
    }

    /**
     * Returns the editor tracker for this VocabElementRootView.
     */
    public EditorTracker getEdTracker() {
        return edTracker;
    }

    /**
     * Returns the editors used for this root view.
     */
    public List<EditorComponent> getEditors() {
        return editors;
    }

}

